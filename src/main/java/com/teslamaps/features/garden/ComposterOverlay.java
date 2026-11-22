package com.teslamaps.features.garden;

// After SkyHanni's ComposterOverlay
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.SackApi;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cheapest items to refill organic matter and fuel, compost profit, upgrade preview. */
public final class ComposterOverlay {

    private static final String REPO = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/Garden.json";
    private static final String[] UNITS = {"Compost", "Hour", "Day"};
    private static final int LINE = 10, ROWS = 10;

    // organic matter per item, SkyHanni repo values; the enchanted tiers are derived below
    private static final Map<String, Double> BASE_MATTER = new LinkedHashMap<>();
    private static final Map<String, Double> BASE_FUEL = new LinkedHashMap<>();
    static {
        BASE_MATTER.put("CROPIE", 2500.0); BASE_MATTER.put("SQUASH", 10000.0); BASE_MATTER.put("FERMENTO", 20000.0);
        BASE_MATTER.put("HELIANTHUS", 30000.0); BASE_MATTER.put("FLOWERING_BOUQUET", 6000.0);
        BASE_MATTER.put("WHEAT", 1.0); BASE_MATTER.put("CARROT_ITEM", 0.29); BASE_MATTER.put("POTATO_ITEM", 0.33);
        BASE_MATTER.put("PUMPKIN", 1.0); BASE_MATTER.put("MELON", 0.2); BASE_MATTER.put("RED_MUSHROOM", 1.0);
        BASE_MATTER.put("INK_SACK:3", 0.4); BASE_MATTER.put("CACTUS_GREEN", 0.5); BASE_MATTER.put("SUGAR_CANE", 0.5);
        BASE_MATTER.put("NETHER_STALK", 0.33); BASE_MATTER.put("SEEDS", 1.0); BASE_MATTER.put("BROWN_MUSHROOM", 1.0);
        BASE_MATTER.put("CACTUS", 0.5); BASE_MATTER.put("POISONOUS_POTATO", 0.33); BASE_MATTER.put("INK_SACK:2", 0.5);
        BASE_MATTER.put("MELON_BLOCK", 1.8); BASE_MATTER.put("DOUBLE_PLANT", 0.5); BASE_MATTER.put("WILD_ROSE", 0.5);
        BASE_FUEL.put("BIOFUEL", 3000.0); BASE_FUEL.put("VOLTA", 10000.0);
        BASE_FUEL.put("OIL_BARREL", 10000.0); BASE_FUEL.put("SUNFLOWER_OIL", 20000.0);
    }

    // item -> {base item, amount of it}
    private static final Object[][] TIERS = {
            {"ENCHANTED_WHEAT", "WHEAT", 160}, {"HAY_BLOCK", "WHEAT", 9}, {"ENCHANTED_HAY_BLOCK", "WHEAT", 1296},
            {"TIGHTLY_TIED_HAY_BALE", "WHEAT", 186624},
            {"ENCHANTED_SEEDS", "SEEDS", 160}, {"BOX_OF_SEEDS", "SEEDS", 25600},
            {"ENCHANTED_CARROT", "CARROT_ITEM", 160}, {"ENCHANTED_POTATO", "POTATO_ITEM", 160},
            {"ENCHANTED_BAKED_POTATO", "POTATO_ITEM", 25600}, {"ENCHANTED_POISONOUS_POTATO", "POISONOUS_POTATO", 160},
            {"ENCHANTED_PUMPKIN", "PUMPKIN", 160}, {"POLISHED_PUMPKIN", "PUMPKIN", 25600},
            {"ENCHANTED_MELON", "MELON", 160}, {"ENCHANTED_MELON_BLOCK", "MELON", 25600},
            {"ENCHANTED_RED_MUSHROOM", "RED_MUSHROOM", 160}, {"ENCHANTED_BROWN_MUSHROOM", "BROWN_MUSHROOM", 160},
            {"ENCHANTED_HUGE_MUSHROOM_1", "BROWN_MUSHROOM", 5184}, {"ENCHANTED_HUGE_MUSHROOM_2", "RED_MUSHROOM", 5184},
            {"ENCHANTED_COCOA", "INK_SACK:3", 160}, {"ENCHANTED_CACTUS_GREEN", "CACTUS_GREEN", 160},
            {"ENCHANTED_CACTUS", "CACTUS_GREEN", 25600}, {"ENCHANTED_SUGAR", "SUGAR_CANE", 160},
            {"ENCHANTED_SUGAR_CANE", "SUGAR_CANE", 25600}, {"ENCHANTED_NETHER_STALK", "NETHER_STALK", 160},
            {"MUTANT_NETHER_STALK", "NETHER_STALK", 25600}, {"ENCHANTED_SUNFLOWER", "DOUBLE_PLANT", 160},
            {"COMPACTED_SUNFLOWER", "DOUBLE_PLANT", 25600}, {"ENCHANTED_WILD_ROSE", "WILD_ROSE", 160},
            {"COMPACTED_WILD_ROSE", "WILD_ROSE", 25600},
    };

    private record Row(String text, String id, boolean fuel, int need, List<Component> tip) {}

    private static Map<String, Double> matterFactors = build(BASE_MATTER);
    private static Map<String, Double> fuelFactors = new LinkedHashMap<>(BASE_FUEL);
    private static boolean fetched;
    private static Feature feature;
    private static int ticks;
    private static String menu = "";
    private static String hovered;
    private static List<Row> rows = List.of();
    private static int panelW;
    private static int panelX, panelY;
    private static long lastRetrieve;

    private ComposterOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ComposterOverlay").section("Garden")
                .when(() -> cfg().composterOverlay)
                .tick(ComposterOverlay::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Composter Overlay", () -> c.composterOverlay, v -> c.composterOverlay = v)
                            .desc("Next to the Composter menu: the 10 cheapest items to fill organic matter and fuel, "
                                    + "time per compost, composts, cost and profit per hour or day. In Composter Upgrades "
                                    + "it previews the hovered upgrade. Click an item to use it for the profit numbers, "
                                    + "Ctrl + click takes it from your sacks or opens the Bazaar.")
                            .tags("compost", "organic matter", "fuel", "profit");
                    b.priceSource("  Price Source", () -> c.composterPriceSource, v -> c.composterPriceSource = v);
                });
    }

    private static Map<String, Double> build(Map<String, Double> base) {
        Map<String, Double> out = new LinkedHashMap<>(base);
        for (Object[] t : TIERS) {
            Double v = base.get((String) t[1]);
            if (v != null) out.put((String) t[0], v * (Integer) t[2]);
        }
        return out;
    }

    private static void fetch() {
        fetched = true;
        Thread t = new Thread(() -> {
            try {
                JsonObject o = RepoCache.fetch("sh-garden", REPO, 20).getAsJsonObject();
                Map<String, Double> matter = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("organic_matter").entrySet())
                    matter.put(e.getKey().replace('-', ':'), e.getValue().getAsDouble());
                Map<String, Double> fuel = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("fuel").entrySet())
                    fuel.put(e.getKey().replace('-', ':'), e.getValue().getAsDouble());
                if (!matter.isEmpty()) matterFactors = build(matter);
                if (!fuel.isEmpty()) fuelFactors = fuel;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Composter] Garden repo failed, using built-in values: {}", e.toString());
            }
        }, "TeslaMaps-Composter");
        t.setDaemon(true);
        t.start();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !SkyblockUtils.isInGarden()) {
            if (!rows.isEmpty()) rows = List.of();
            menu = "";
            return;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.equals("Composter") && !title.equals("Composter Upgrades")) {
            if (!rows.isEmpty()) rows = List.of();
            menu = "";
            return;
        }
        if (!fetched) fetch();
        String hov = null;
        if (title.equals("Composter Upgrades")) {
            Slot s = ((HandledScreenAccessor) cs).getFocusedSlot();
            if (s != null && s.hasItem()) {
                String name = StringUtil.stripTrim(s.getItem().getHoverName().getString());
                for (String u : Composter.upgradeNames()) {
                    if (name.startsWith(u)) {
                        hov = name.endsWith(" XXV") ? null : u;
                        break;
                    }
                }
            }
        }
        boolean changed = !title.equals(menu) || !java.util.Objects.equals(hov, hovered);
        menu = title;
        hovered = hov;
        if (!changed && ++ticks % 10 != 0) return;
        PriceManager.ensureFresh();
        rows = build(title.equals("Composter"), hov);
        int w = 0;
        for (Row r : rows) w = Math.max(w, mc.font.width(r.text));
        panelW = w;
    }

    private static double price(String id) {
        Double p = PriceManager.getPriceOrNull(id, cfg().composterPriceSource);
        if (p == null) return 0;
        if (id.equals("BIOFUEL") && p > 20_000) return 20_000;
        return p;
    }

    private static String name(String id) {
        String n = SkyblockItems.coloredName(id);
        return n != null ? n : "§f" + StringUtil.titleCase(id.replace('_', ' ').toLowerCase(Locale.ROOT));
    }

    private static List<Row> build(boolean composter, String plus) {
        List<Row> out = new ArrayList<>();
        TeslaMapsConfig c = cfg();
        if (!Composter.hasUpgrades()) {
            out.add(new Row("§cOpen Composter Upgrades once!", null, false, 0, null));
            return out;
        }
        String matterItem = pick(c.composterMatterItem, matterFactors);
        String fuelItem = pick(c.composterFuelItem, fuelFactors);
        if (!composter) {
            out.add(new Row(plus == null ? "§7Preview: §fhover an upgrade" : "§7Preview §a" + plus + "§7: §a"
                    + Composter.level(plus, null) + " §c➜ §a" + (Composter.level(plus, null) + 1), null, false, 0, null));
            out.add(new Row("", null, false, 0, null));
        }
        profit(out, matterItem, fuelItem, plus);
        if (composter) {
            double matter = Math.max(0, Composter.matter());
            double fuel = Math.max(0, Composter.fuel());
            out.add(new Row("§7Items needed to fill §eOrganic Matter", null, false, 0, null));
            fill(out, matterFactors, Composter.maxMatter(null) - matter, false, matterItem);
            out.add(new Row("", null, false, 0, null));
            out.add(new Row("§7Items needed to fill §2Fuel", null, false, 0, null));
            fill(out, fuelFactors, Composter.maxFuel(null) - fuel, true, fuelItem);
        } else {
            out.add(new Row("§eOrganic Matter §7empty in " + emptyText(Composter.maxMatter(null), Composter.matterPer(null),
                    Composter.maxMatter(plus), Composter.matterPer(plus), plus), null, false, 0, null));
            out.add(new Row("§2Fuel §7empty in " + emptyText(Composter.maxFuel(null), Composter.fuelPer(null),
                    Composter.maxFuel(plus), Composter.fuelPer(plus), plus), null, false, 0, null));
        }
        return out;
    }

    private static String emptyText(double max, double per, double maxP, double perP, String plus) {
        long now = (long) (Composter.secondsPerCompost(null) * Math.floor(max / per) * 1000);
        long pre = (long) (Composter.secondsPerCompost(plus) * Math.floor(maxP / perP) * 1000);
        return "§b" + Composter.format(now) + (pre != now ? " §c➜ §b" + Composter.format(pre) : "");
    }

    private static String pick(String chosen, Map<String, Double> factors) {
        if (chosen != null && factors.containsKey(chosen)) return chosen;
        String best = null;
        double bestV = 0;
        for (Map.Entry<String, Double> e : factors.entrySet()) {
            double p = price(e.getKey());
            if (p <= 0) continue;
            double v = e.getValue() / p;
            if (v > bestV) { bestV = v; best = e.getKey(); }
        }
        return best;
    }

    private static void profit(List<Row> out, String matterItem, String fuelItem, String plus) {
        if (matterItem == null || fuelItem == null) {
            out.add(new Row("§7loading prices…", null, false, 0, null));
            return;
        }
        String unit = cfg().composterTimeUnit;
        double unitSec = unit.equals("Day") ? 86400 : unit.equals("Hour") ? 3600 : 0;
        double sec = Composter.secondsPerCompost(null), secP = Composter.secondsPerCompost(plus);
        double timeMul = unitSec == 0 ? 1 : unitSec / sec, timeMulP = unitSec == 0 ? 1 : unitSec / secP;
        double drop = Composter.multiDrop(null) + 1, dropP = Composter.multiDrop(plus) + 1;
        double mul = drop * timeMul, mulP = dropP * timeMulP;
        double mPrice = price(matterItem), fPrice = price(fuelItem);
        double mFac = matterFactors.getOrDefault(matterItem, 1.0), fFac = fuelFactors.getOrDefault(fuelItem, 1.0);
        double costPer = mPrice * Composter.matterPer(null) / mFac + fPrice * Composter.fuelPer(null) / fFac;
        double costPerP = mPrice * Composter.matterPer(plus) / mFac + fPrice * Composter.fuelPer(plus) / fFac;
        double compost = price("COMPOST");
        double cost = costPer * timeMul, costP = costPerP * timeMulP;
        double profit = (compost * drop - costPer) * timeMul, profitP = (compost * dropP - costPerP) * timeMulP;
        String per = unit.toLowerCase(Locale.ROOT);

        out.add(new Row("§7Display: §e[" + unit + "] §8(click)", "#unit", false, 0, null));
        out.add(new Row("§7Using " + name(matterItem) + " §7and " + name(fuelItem), null, false, 0, null));
        out.add(new Row(" §7Time per Compost: §b" + Composter.format((long) (sec * 1000))
                + (secP != sec ? " §c➜ §b" + Composter.format((long) (secP * 1000)) : ""), null, false, 0, null));
        out.add(new Row(" §7" + (unitSec == 0 ? "Compost factor" : "Composts per " + per) + ": §e" + round(mul)
                + (mulP != mul ? " §c➜ §e" + round(mulP) : ""), null, false, 0, null));
        out.add(new Row(" §7Cost per " + per + ": §6" + EstimatedValue.fmt(cost)
                + (costP != cost ? " §c➜ §6" + EstimatedValue.fmt(costP) : ""), null, false, 0, null));
        out.add(new Row(" §7Profit per " + per + ": " + (profit < 0 ? "§c" : "§6") + EstimatedValue.fmt(profit)
                + (profitP != profit ? " §c➜ §6" + EstimatedValue.fmt(profitP) : ""), null, false, 0, null));
        out.add(new Row("", null, false, 0, null));
    }

    private static String round(double v) {
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    private static void fill(List<Row> out, Map<String, Double> factors, double missing, boolean fuel, String selected) {
        Map<String, Double> value = new HashMap<>();
        for (Map.Entry<String, Double> e : factors.entrySet()) {
            double p = price(e.getKey());
            if (p > 0) value.put(e.getKey(), e.getValue() / p);
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(value.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        String type = fuel ? "§2Fuel" : "§eOrganic Matter";
        for (int i = 0; i < sorted.size() && i < ROWS; i++) {
            String id = sorted.get(i).getKey();
            double factor = factors.get(id);
            int need = (int) Math.ceil(Math.max(0, missing) / factor);
            double p = price(id), total = need * p;
            boolean sel = id.equals(selected);
            String n = name(id);
            String shown = sel ? n.substring(0, Math.min(2, n.length())) + "§n" + StringUtil.strip(n) : n;
            List<Component> tip = List.of(
                    Component.literal(n), Component.empty(),
                    Component.literal("§7Price per: §6" + EstimatedValue.fmt(p)),
                    Component.literal(type + " §7per: §e" + String.format(Locale.ROOT, "%,.0f", factor)),
                    Component.literal("§7Need §8" + need + " §7items for max " + type),
                    Component.literal("§7Total price: §6" + EstimatedValue.fmt(total)),
                    Component.empty(),
                    Component.literal(sel ? "§eCtrl + click: take from sacks / open Bazaar" : "§eClick to use it for the profit numbers"));
            out.add(new Row(shown + " §8x" + String.format(Locale.ROOT, "%,d", need) + " §7(§6" + EstimatedValue.fmt(total) + "§7)",
                    id, fuel, need, tip));
        }
    }

    public static void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (rows.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        Perf.begin("ComposterOverlay.render");
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int h = rows.size() * LINE;
        panelX = Math.max(6, acc.getX() - panelW - 12);
        panelY = Math.max(6, acc.getY());
        com.teslamaps.render.gui.HudStyle.panel(g, panelX - 4, panelY - 4, panelW + 8, h + 6);
        Row hover = null;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = panelY + i * LINE;
            boolean in = r.id != null && mouseX >= panelX && mouseX < panelX + panelW && mouseY >= y && mouseY < y + LINE;
            if (in) {
                g.fill(panelX - 2, y - 1, panelX + panelW + 2, y + LINE - 1, 0x30FFFFFF);
                hover = r;
            }
            g.text(mc.font, r.text, panelX, y, 0xFFFFFFFF, true);
        }
        if (hover != null && hover.tip != null) g.setComponentTooltipForNextFrame(mc.font, hover.tip, mouseX, mouseY);
        Perf.end();
    }

    /** From HandledScreenClickMixin. */
    public static boolean handleClick(double mx, double my, int button) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || rows.isEmpty() || feature == null || !feature.active()) return false;
        if (mx < panelX || mx >= panelX + panelW || my < panelY) return false;
        int i = (int) ((my - panelY) / LINE);
        if (i < 0 || i >= rows.size()) return false;
        Row r = rows.get(i);
        if (r.id == null) return false;
        TeslaMapsConfig c = cfg();
        if (r.id.equals("#unit")) {
            int at = java.util.Arrays.asList(UNITS).indexOf(c.composterTimeUnit);
            c.composterTimeUnit = UNITS[(at + 1) % UNITS.length];
        } else {
            if (r.fuel) c.composterFuelItem = r.id;
            else c.composterMatterItem = r.id;
            if (Minecraft.getInstance().hasControlDown()) retrieve(r);
        }
        TeslaMapsConfig.save();
        ticks = 9;
        hovered = "#dirty";
        return true;
    }

    private static void retrieve(Row r) {
        long now = System.currentTimeMillis();
        if (now - lastRetrieve < 500 || r.need <= 0) return;
        lastRetrieve = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        String plain = StringUtil.strip(name(r.id));
        long inSacks = SackApi.amount(plain);
        if (inSacks > 0 && !r.id.equals("SUNFLOWER_OIL")) {
            mc.getConnection().sendCommand("gfs " + r.id.replace('-', ':') + " " + Math.min(r.need, inSacks));
        } else {
            mc.getConnection().sendCommand("bz " + plain);
        }
    }
}
