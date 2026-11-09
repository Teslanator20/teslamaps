package com.teslamaps.features;

// Ported from SkyHanni's NpcTradeHelper + NpcTradeApi
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** NPC shop / trade menus: owned x/y or ✔, sack amount and coin total on each cost line; affordable trades green. */
public final class NpcTradeCosts {

    private record Trade(Map<String, String> lines, String headerSuffix, boolean affordable) {}

    private static Feature feature;
    private static Screen lastScreen;
    private static int ticks;
    private static final Map<ItemStack, Trade> trades = new IdentityHashMap<>();
    private static int[] affordable = new int[0];

    private NpcTradeCosts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("NpcTradeCosts")
                .when(() -> cfg().npcTradeHelper)
                .tick(NpcTradeCosts::tick)
                .config("Tooltips & Value", b -> {
                    b.label("NPC Trades");
                    b.toggle("NPC Trade Helper", () -> cfg().npcTradeHelper, v -> cfg().npcTradeHelper = v)
                            .desc("NPC shop / trade menus: each cost line shows how many you own (x/y or ✔), your sack amount and the coin value")
                            .tags("npc", "trade", "shop", "cost", "sacks");
                    b.toggle("  Highlight Affordable Trades", () -> cfg().npcTradeHighlight, v -> cfg().npcTradeHighlight = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (trades.isEmpty() || feature == null || !feature.active() || lines.isEmpty()) return;
            Trade t = trades.get(stack);
            if (t != null) onTooltip(t, lines);
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) {
                lastScreen = null;
                trades.clear();
                affordable = new int[0];
            }
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            ticks = 0;
            trades.clear();
            affordable = new int[0];
        }
        if (ticks++ % 10 != 0) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (title.startsWith("Heart of the") || title.contains("RNG Meter")) return;
        PriceManager.ensureFresh();
        build(cs);
    }

    private static void build(AbstractContainerScreen<?> cs) {
        trades.clear();
        List<Integer> ok = new ArrayList<>();
        long purse = -2;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            if (!LoreCosts.hasTradeLine(lore)) continue;
            List<LoreCosts.Cost> costs = LoreCosts.read(lore);
            if (costs.isEmpty()) continue;
            if (purse == -2) purse = LoreCosts.purse();
            Map<String, String> lines = new HashMap<>();
            double coinTotal = 0;
            boolean all = true;
            for (LoreCosts.Cost c : costs) {
                double price = LoreCosts.price(c);
                coinTotal += price;
                long owned = c.coins() ? purse : LoreCosts.owned(c);
                boolean covered = owned >= 0 && owned >= c.amount();
                if (!covered) all = false;
                String suffix;
                if (owned < 0) suffix = " §8?§7/§8" + fmt(c.amount());
                else if (covered) suffix = " §a✔";
                else suffix = " §8" + fmt(owned) + "§7/§8" + fmt(c.amount());
                long sacks = c.coins() ? -1 : SackApi.amount(c.name());
                String sackText = sacks > 0 ? " §7(sacks: §a" + fmt(sacks) + "§7)" : "";
                String name = c.coins() ? "§6" + fmt(c.amount()) + " Coins"
                        : "§f" + c.name() + (c.amount() > 1 ? " §8x" + fmt(c.amount()) : "")
                        + (price > 0 ? " §7(§6" + EstimatedValue.fmt(price) + "§7)" : "");
                String indent = c.raw().substring(0, c.raw().length() - c.raw().stripLeading().length());
                lines.put(c.raw().trim(), indent + name + suffix + sackText);
            }
            String suffix = coinTotal > 0 ? " §7(§6" + EstimatedValue.fmt(coinTotal) + "§7)" : "";
            for (String l : lore) {
                String h = l.trim();
                if (!LoreCosts.isHeader(h)) continue;
                String out = h;
                int colon = h.indexOf(": ");
                if (colon >= 0) {
                    String inline = lines.get(h.substring(colon + 2).trim());
                    if (inline != null) out = h.substring(0, colon + 2) + inline.trim();
                }
                lines.put(h, "§7" + out + suffix);
            }
            trades.put(stack, new Trade(lines, suffix, all));
            if (all) ok.add(slot.index);
        }
        int[] arr = new int[ok.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ok.get(i);
        affordable = arr;
    }

    private static String fmt(long v) { return String.format("%,d", v); }

    private static void onTooltip(Trade t, List<Component> lines) {
        for (int i = 1; i < lines.size(); i++) {
            String repl = t.lines().get(lines.get(i).getString().trim());
            if (repl != null) lines.set(i, Component.literal(repl));
        }
    }

    public static void render(GuiGraphicsExtractor ctx) {
        int[] a = affordable;
        if (a.length == 0 || !cfg().npcTradeHighlight || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        Perf.begin("NpcTradeCosts.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (int idx : a) {
            if (idx >= slots.size()) continue;
            Slot s = slots.get(idx);
            int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x5555FF55);
        }
        Perf.end();
    }
}
