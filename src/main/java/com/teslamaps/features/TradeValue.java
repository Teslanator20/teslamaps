package com.teslamaps.features;

// After NEU's TradeWindow (value per side, changed items), drawn over the vanilla trade menu
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class TradeValue {

    private static final long CHANGE_MS = 10_000L;

    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inTrade;
    private static final String[] theirSig = new String[16];
    private static final long[] changedAt = new long[16];
    private static boolean seeded;
    private static long lastChange;
    private static List<String> lines = List.of();
    private static final Map<ItemStack, Double> values = new IdentityHashMap<>();
    private static int ticks;

    private TradeValue() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("TradeValue")
                .when(() -> cfg().tradeValue)
                .tick(TradeValue::tick)
                .config("Tooltips & Value", b -> {
                    b.label("Trade Menu");
                    b.toggle("Trade Value Panel", () -> cfg().tradeValue, v -> cfg().tradeValue = v)
                            .desc("Trade menu: value of each side next to the menu, price on every traded item")
                            .tags("trade", "scam", "value");
                    b.toggle("  Mark Their Changes", () -> cfg().tradeValueChanges, v -> cfg().tradeValueChanges = v)
                            .desc("Items the other player adds or swaps get a yellow box for 10s");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, out) -> {
            if (!inTrade || feature == null || !feature.active() || out.isEmpty()) return;
            Double v = values.get(stack);
            if (v != null && v > 0) out.add(Component.literal("§7Trade Value: §6" + EstimatedValue.exact(v) + " coins"));
        });
    }

    private static int ours(int i) { return (i / 4) * 9 + i % 4; }
    private static int theirs(int i) { return (i / 4) * 9 + 5 + i % 4; }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (lastScreen != null) reset();
            return;
        }
        if (cs != lastScreen) {
            reset();
            lastScreen = cs;
            String title = StringUtil.stripTrim(cs.getTitle().getString());
            inTrade = title.startsWith("You    ") && cs.getMenu().slots.size() >= 45;
        }
        if (!inTrade || ticks++ % 5 != 0) return;
        PriceManager.ensureFresh();

        List<Slot> slots = cs.getMenu().slots;
        long now = System.currentTimeMillis();
        values.clear();
        double mine = 0, other = 0;
        for (int i = 0; i < 16; i++) {
            ItemStack a = slots.get(ours(i)).getItem();
            mine += value(a);
            ItemStack b = slots.get(theirs(i)).getItem();
            other += value(b);
            String sig = b.isEmpty() ? "" : b.getHoverName().getString() + "|" + b.getCount() + "|" + com.teslamaps.utils.ItemUtil.itemUuid(b);
            if (seeded && !sig.equals(theirSig[i])) {
                changedAt[i] = sig.isEmpty() ? 0 : now;
                lastChange = now;
            }
            theirSig[i] = sig;
        }
        seeded = true;

        List<String> out = new ArrayList<>(6);
        out.add("§6§lTrade Value");
        out.add("§aYou: §6" + EstimatedValue.fmt(mine));
        out.add("§cThem: §6" + EstimatedValue.fmt(other));
        double diff = other - mine;
        out.add("§7Difference: " + (diff >= 0 ? "§a+" : "§c-") + EstimatedValue.fmt(Math.abs(diff)));
        if (!PriceManager.isLoaded()) out.add("§7loading prices…");
        if (cfg().tradeValueChanges && now - lastChange < CHANGE_MS) out.add("§e⚠ They changed their offer!");
        lines = out;
    }

    private static double value(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        double v;
        if (name.endsWith(" coins")) {
            v = NumberUtil.parseSuffixed(name.substring(0, name.length() - 6).trim());
        } else {
            v = EstimatedValue.compute(stack) * stack.getCount();
        }
        if (v > 0) values.put(stack, v);
        return v;
    }

    private static void reset() {
        lastScreen = null;
        inTrade = false;
        seeded = false;
        lastChange = 0;
        lines = List.of();
        values.clear();
        ticks = 0;
        for (int i = 0; i < 16; i++) {
            theirSig[i] = null;
            changedAt[i] = 0;
        }
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!inTrade || lines.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs != lastScreen) return;
        Perf.begin("TradeValue.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        if (cfg().tradeValueChanges) {
            long now = System.currentTimeMillis();
            List<Slot> slots = cs.getMenu().slots;
            for (int i = 0; i < 16; i++) {
                if (changedAt[i] == 0 || now - changedAt[i] >= CHANGE_MS) continue;
                Slot s = slots.get(theirs(i));
                int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x66FFFF55);
                ctx.fill(sx, sy, sx + 16, sy + 1, 0xFFFFFF55);
                ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFFFFFF55);
                ctx.fill(sx, sy, sx + 1, sy + 16, 0xFFFFFF55);
                ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFFFFFF55);
            }
        }
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        int h = lines.size() * (mc.font.lineHeight + 1);
        int x = Math.max(4, acc.getX() - w - 10);
        int y = acc.getY() + 4;
        com.teslamaps.render.gui.HudStyle.panel(ctx, x - 4, y - 4, w + 8, h + 6);
        for (int i = 0; i < lines.size(); i++) {
            ctx.text(mc.font, lines.get(i), x, y + i * (mc.font.lineHeight + 1), 0xFFFFFFFF);
        }
        Perf.end();
    }
}
