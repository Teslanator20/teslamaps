package com.teslamaps.features;

// Ported from SkyHanni's MinionCraftHelper and SkyOcean's MinionHelper / MinionStorage
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CraftCost;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinionHelper {

    private static final Pattern GENERATOR = Pattern.compile("(.+)_GENERATOR_(\\d+)");
    private static final Pattern CRAFTED = Pattern.compile("\\(\\d+/\\d+\\) Crafted Minions");
    private static final Map<String, String> PART_ALIAS = Map.of(
            "CAVE_SPIDER", "CAVESPIDER", "NETHER_WART", "NETHER_WARTS", "COCOA_BEANS", "COCOA");
    private static final int ROW = 10;
    private static final List<String> SAMPLE = List.of("§9Wheat Minion VI", "§fEnchanted Wheat§8: §e40% §8(§7128§8/§7320§8)",
            "§fWheat Minion V§8: §aDONE");

    private record Upgrade(String text, double price) {}

    private static volatile List<String> hud = List.of();
    private static int hudWidth;
    private static List<String> panel = List.of();
    private static int panelWidth;
    private static final Map<String, Integer> tiers = new HashMap<>();
    private static int ticks;
    private static HudEditElement el;
    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("minionCraftHelper", "Minion Craft Helper").ctx(HudEditElement.HudCtx.ANY).section("Inventory")
                .enabled(() -> cfg().minionCraftHelper)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * ROW)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        feature = Feature.of("MinionHelper").section("Inventory")
                .when(() -> cfg().minionCraftHelper || cfg().minionUpgrades)
                .tick(MinionHelper::tick)
                .hudHiddenInInventory("minioncrafthelper", MinionHelper::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Minion Helper");
                    b.toggle("Minion Craft Helper", () -> c.minionCraftHelper, v -> c.minionCraftHelper = v);
                    b.info("HUD: materials missing for the next tier of minions in your inventory");
                    b.toggle("Cheapest Minion Upgrades", () -> c.minionUpgrades, v -> c.minionUpgrades = v);
                    b.info("Panel next to Crafted Minions: next tier of each minion by material cost");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        if (c.minionUpgrades && mc.screen instanceof AbstractContainerScreen<?> cs
                && CRAFTED.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) {
            readCrafted(cs);
        } else if (!panel.isEmpty()) {
            panel = List.of();
        }
        if (c.minionCraftHelper && mc.player != null) buildHud(mc.player.getInventory());
        else if (!hud.isEmpty()) hud = List.of();
    }

    // SkyOcean: a head with n ticks = n tiers crafted, a gray dye = none yet
    private static void readCrafted(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            int sp = name.lastIndexOf(' ');
            if (sp <= 0 || !name.endsWith(" Minion")) continue;
            String part = name.substring(0, sp).toUpperCase().replace(' ', '_');
            part = PART_ALIAS.getOrDefault(part, part);
            if (st.is(Items.PLAYER_HEAD)) {
                int n = 0;
                for (String l : ItemUtil.loreLines(st)) if (l.contains("✔")) n++;
                if (n > 0) tiers.merge(part, n, Math::max);
            } else if (st.is(Items.GRAY_DYE)) {
                tiers.putIfAbsent(part, 0);
            }
        }
        List<Upgrade> ups = new ArrayList<>();
        for (Map.Entry<String, Integer> e : tiers.entrySet()) {
            int next = e.getValue() + 1;
            String id = e.getKey() + "_GENERATOR_" + next;
            Map<String, Integer> ing = CraftCost.ingredientsOrNull(id);
            if (ing == null || ing.isEmpty()) continue;
            double price = 0;
            boolean ok = true;
            for (Map.Entry<String, Integer> i : ing.entrySet()) {
                if (i.getKey().contains("_GENERATOR_")) continue;
                Double p = price(i.getKey());
                if (p == null) { ok = false; break; }
                price += p * i.getValue();
            }
            if (!ok) continue;
            String label = StringUtil.titleCase(e.getKey().replace('_', ' ').toLowerCase());
            ups.add(new Upgrade("§9" + label + " §a" + next + "§8: §6" + NumberUtil.formatSuffixed(price), price));
        }
        ups.sort((a, b) -> Double.compare(a.price, b.price));
        List<String> out = new ArrayList<>();
        if (!ups.isEmpty()) out.add("§f§lCheapest Minion Upgrades");
        for (Upgrade u : ups) out.add(u.text);
        if (!out.equals(panel)) {
            panelWidth = width(out);
            panel = out;
        }
    }

    private static Double price(String neuId) {
        String id = neuId.replace('-', ':');
        Double p = PriceManager.marketPriceOrNull(id, PriceManager.SOURCE_BAZAAR_BUY);
        if (p == null) p = CraftCost.costOrNull(neuId, PriceManager.SOURCE_BAZAAR_BUY);
        return p;
    }

    private static void buildHud(Inventory inv) {
        Map<String, Integer> have = new HashMap<>();
        Map<String, Integer> minions = new LinkedHashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = ItemUtil.skyblockId(st);
            if (id == null || id.isEmpty()) continue;
            Matcher m = GENERATOR.matcher(id);
            if (m.matches()) minions.merge(m.group(1), Integer.parseInt(m.group(2)), Math::max);
            have.merge(id, st.getCount(), Integer::sum);
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : minions.entrySet()) {
            int tier = e.getValue() + 1;
            String id = e.getKey() + "_GENERATOR_" + tier;
            Map<String, Integer> ing = CraftCost.ingredientsOrNull(id);
            if (ing == null || ing.isEmpty()) continue;
            String prev = e.getKey() + "_GENERATOR_" + e.getValue();
            out.add("§9" + displayName(id));
            for (Map.Entry<String, Integer> i : ing.entrySet()) {
                String ingId = i.getKey().replace('-', ':');
                int need = i.getValue();
                int got = have.getOrDefault(ingId, 0);
                if (ingId.equals(prev)) got = Math.max(got, 1);
                String name = displayName(ingId);
                if (got >= need) {
                    out.add("§f" + name + "§8: §aDONE");
                } else {
                    int pct = (int) (got * 100L / Math.max(1, need));
                    out.add("§f" + name + "§8: §e" + pct + "% §8(§7" + got + "§8/§7" + need + "§8)");
                }
            }
        }
        if (out.equals(hud)) return;
        hudWidth = width(out);
        hud = out;
    }

    private static String displayName(String id) {
        String n = SkyblockItems.name(id);
        if (n != null) return n;
        Matcher m = GENERATOR.matcher(id);
        if (m.matches()) return StringUtil.titleCase(m.group(1).replace('_', ' ').toLowerCase()) + " Minion " + m.group(2);
        return StringUtil.titleCase(id.replace('_', ' ').toLowerCase());
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = hud;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l, hudWidth);
    }

    public static void renderPanel(GuiGraphicsExtractor g) {
        List<String> l = panel;
        if (l.isEmpty() || feature == null || !feature.active() || !cfg().minionUpgrades) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int x = acc.getX() + acc.getImageWidth() + 6, y = acc.getY() + 4;
        int rows = Math.min(l.size(), Math.max(2, (mc.getWindow().getGuiScaledHeight() - y - 4) / ROW));
        HudStyle.panel(g, x - 3, y - 3, panelWidth + 6, rows * ROW + 4);
        for (int i = 0; i < rows; i++) g.text(mc.font, l.get(i), x, y + i * ROW, 0xFFFFFFFF, true);
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * ROW + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * ROW, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
