package com.teslamaps.features;

// Ported from SkyHanni's CraftMaterialCollector.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CraftMaterials {

    private static final int[] MATERIAL_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int[] MULTIPLIERS = {1, 5, 16, 32, 64, 512};

    private static final EcoPanel recipePanel = new EcoPanel(EcoPanel.Side.RIGHT);
    private static Map<String, Integer> buying;
    private static int multiplier = 1;
    private static Screen lastScreen;
    private static int ticks;
    private static Feature feature;

    private CraftMaterials() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CraftMaterials").section("Inventory")
                .when(() -> cfg().craftMaterials)
                .tick(CraftMaterials::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Craft Materials");
                    b.toggle("Craft Material Collector", () -> c.craftMaterials, v -> c.craftMaterials = v)
                            .desc("Recipe view: material cost panel; click it for a shopping list (x1..x512), click a row for /bz or /ahs.")
                            .tags("recipe", "bazaar", "shopping", "craft cost");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            recipePanel.clear();
            return;
        }
        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            ticks = 0;
        }
        if (ticks++ % 10 != 0) return;
        PriceManager.ensureFresh();
        if (!cfg().craftMaterials) {
            recipePanel.clear();
            return;
        }
        if (buying != null) {
            buildBuying();
            return;
        }
        var slots = cs.getMenu().slots;
        if (slots.size() > 32 && name(slots.get(23).getItem()).equals("Crafting Table")
                && name(slots.get(32).getItem()).equals("Supercraft")) {
            buildRecipe(slots);
        } else {
            recipePanel.clear();
        }
    }

    private static String name(ItemStack st) {
        return st.isEmpty() ? "" : StringUtil.stripTrim(st.getHoverName().getString());
    }

    private static String idOf(ItemStack st) {
        String id = ItemUtil.skyblockId(st);
        if (!id.isEmpty()) return id;
        String n = name(st);
        String p = PriceManager.idForName(n);
        return p != null ? p : SkyblockItems.idForName(n);
    }

    private static void buildRecipe(List<net.minecraft.world.inventory.Slot> slots) {
        Map<String, Integer> mats = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        for (int s : MATERIAL_SLOTS) {
            ItemStack st = slots.get(s).getItem();
            if (st.isEmpty()) continue;
            String id = idOf(st);
            if (id == null || id.isEmpty()) continue;
            mats.merge(id, st.getCount(), Integer::sum);
            names.putIfAbsent(id, st.getHoverName().getString());
        }
        if (mats.isEmpty()) {
            recipePanel.clear();
            return;
        }
        String out = slots.get(25).getItem().getHoverName().getString();
        List<EcoPanel.Row> rows = new ArrayList<>();
        rows.add(EcoPanel.Row.of("§7Craft " + out + " §7(§6" + EstimatedValue.fmt(total(mats, 1)) + "§7)"));
        Map<String, Integer> buyable = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : mats.entrySet()) {
            double p = PriceManager.getPrice(e.getKey());
            String text = "§8" + String.format(Locale.ROOT, "%,d", e.getValue()) + "x " + names.get(e.getKey());
            if (p > 0) {
                buyable.put(e.getKey(), e.getValue());
                text += " §6" + EstimatedValue.fmt(p * e.getValue());
            }
            rows.add(EcoPanel.Row.of(text));
        }
        if (!buyable.isEmpty()) {
            rows.add(new EcoPanel.Row("§eAdd to craft material collector!",
                    EcoPanel.tip(List.of("§eClick here to get help purchasing the items!")), () -> {
                        buying = buyable;
                        multiplier = 1;
                        buildBuying();
                    }, -1));
        }
        recipePanel.set(rows);
    }

    private static double total(Map<String, Integer> mats, int mult) {
        double sum = 0;
        for (Map.Entry<String, Integer> e : mats.entrySet()) sum += PriceManager.getPrice(e.getKey()) * e.getValue() * (double) mult;
        return sum;
    }

    private static String display(String id) {
        String n = SkyblockItems.coloredName(id);
        return n != null ? n : "§f" + id;
    }

    private static void buildBuying() {
        Map<String, Integer> b = buying;
        if (b == null) return;
        List<EcoPanel.Row> rows = new ArrayList<>();
        rows.add(EcoPanel.Row.of("§7Buy items:"));
        for (Map.Entry<String, Integer> e : b.entrySet()) {
            String id = e.getKey();
            long amount = (long) e.getValue() * multiplier;
            String name = display(id);
            boolean bz = PriceManager.hasBazaar(id);
            rows.add(new EcoPanel.Row("§8" + String.format(Locale.ROOT, "%,d", amount) + "x " + name + " §6"
                    + EstimatedValue.fmt(PriceManager.getPrice(id) * amount),
                    EcoPanel.tip(List.of(name, "", bz ? "§eClick: /bz (amount copied)" : "§eClick: /ahs")),
                    () -> EcoPanel.shop(id, name, amount), -1));
        }
        rows.add(new EcoPanel.Row("§eStop!", EcoPanel.tip(List.of("§eClick here to stop this view!")), () -> {
            buying = null;
            recipePanel.clear();
        }, -1));
        for (int m : MULTIPLIERS) {
            boolean on = m == multiplier;
            String text = (on ? "§a" : "§e") + "Multiply x" + m + " " + (on ? "§6" : "§7") + EstimatedValue.fmt(total(b, m));
            rows.add(on ? EcoPanel.Row.of(text) : new EcoPanel.Row(text, null, () -> {
                multiplier = m;
                buildBuying();
            }, -1));
        }
        recipePanel.set(rows);
    }

    public static void render(GuiGraphicsExtractor g, int mx, int my) {
        if (feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!recipePanel.empty()) recipePanel.render(g, cs, 0, mx, my);
    }

    public static boolean click(double mx, double my, int button) {
        if (feature == null || !feature.active()) return false;
        if (!recipePanel.empty() && recipePanel.click(mx, my, button)) {
            ticks = 1;
            return true;
        }
        return false;
    }
}
