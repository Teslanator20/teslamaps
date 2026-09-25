package com.teslamaps.features;

// Ported from SkyHanni's CraftMaterialCollector.kt and CraftableItemList.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NeuRecipeIndex;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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
    private static final EcoPanel listPanel = new EcoPanel(EcoPanel.Side.RIGHT).maxRows(26);
    private static Map<String, Integer> buying;
    private static int multiplier = 1;
    private static Screen lastScreen;
    private static int ticks;
    private static String search = "";
    private static List<Craftable> craftables;
    private static Feature feature;

    private record Craftable(String id, String name, long times, double cost, Map<String, Integer> need) {}

    private CraftMaterials() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CraftMaterials").section("Inventory")
                .when(() -> cfg().craftMaterials || cfg().craftableList)
                .tick(CraftMaterials::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Craft Materials");
                    b.toggle("Craft Material Collector", () -> c.craftMaterials, v -> c.craftMaterials = v)
                            .desc("Recipe view: material cost panel; click it for a shopping list (x1..x512), click a row for /bz or /ahs.")
                            .tags("recipe", "bazaar", "shopping", "craft cost");
                    b.toggle("Craftable Item List", () -> c.craftableList, v -> c.craftableList = v)
                            .desc("Craft Item menu: every item you can craft from your inventory (and sacks), typed text filters, click = /viewrecipe. Downloads the NEU recipe list once.")
                            .tags("craft item", "recipes", "sacks");
                    b.toggle("  Include Sacks", () -> c.craftableListSacks, v -> { c.craftableListSacks = v; craftables = null; });
                    b.toggle("  Hide Vanilla Items", () -> c.craftableListNoVanilla, v -> { c.craftableListNoVanilla = v; craftables = null; });
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            recipePanel.clear();
            listPanel.clear();
            return;
        }
        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            ticks = 0;
            search = "";
            craftables = null;
        }
        if (ticks++ % 10 != 0) return;
        PriceManager.ensureFresh();
        String title = StringUtil.stripTrim(cs.getTitle().getString());

        if (cfg().craftableList && title.equals("Craft Item")) {
            recipePanel.clear();
            buildList(mc);
            return;
        }
        listPanel.clear();
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

    private static void buildList(Minecraft mc) {
        if (craftables == null) {
            Map<String, NeuRecipeIndex.Entry> all = NeuRecipeIndex.recipesOrNull();
            if (all == null) {
                listPanel.set(List.of(EcoPanel.Row.of("§e§lCraftable Items"),
                        EcoPanel.Row.of(NeuRecipeIndex.loading() ? "§7loading recipes..." : "§cRecipes not loaded yet")));
                return;
            }
            craftables = compute(mc, all);
        }
        List<Craftable> list = craftables;
        List<EcoPanel.Row> rows = new ArrayList<>();
        String q = search.toLowerCase(Locale.ROOT);
        rows.add(EcoPanel.Row.of("§e§lCraftable Items §7(" + list.size() + ")"));
        rows.add(EcoPanel.Row.of(search.isEmpty() ? "§8Type to search" : "§7Search: §f" + search + "§7_"));
        if (list.isEmpty()) {
            rows.add(new EcoPanel.Row("§7No Items to craft", EcoPanel.tip(List.of("§7No items found in your Inventory",
                    "or sacks that can be used as", "material in crafting recipes.")), null, -1));
        }
        for (Craftable c : list) {
            if (!q.isEmpty() && !StringUtil.strip(c.name()).toLowerCase(Locale.ROOT).contains(q)) continue;
            List<String> tip = new ArrayList<>();
            tip.add(c.name());
            tip.add("");
            tip.add("§7Craft cost: §6" + EstimatedValue.fmt(c.cost()));
            for (Map.Entry<String, Integer> e : c.need().entrySet()) {
                String id = e.getKey().replace('-', ':');
                tip.add(" §8" + e.getValue() + "x " + display(id) + " §6" + EstimatedValue.fmt(PriceManager.getPrice(id) * e.getValue()));
            }
            tip.add("");
            tip.add("§7You have enough materials");
            tip.add("§7to craft this item §e" + String.format(Locale.ROOT, "%,d", c.times()) + " §7" + (c.times() == 1 ? "time" : "times") + "!");
            tip.add("");
            tip.add("§eClick to view the recipe!");
            String id = c.id();
            rows.add(new EcoPanel.Row("§8x" + String.format(Locale.ROOT, "%,d", c.times()) + " " + c.name(), EcoPanel.tip(tip), () -> {
                if (mc.getConnection() != null) mc.getConnection().sendCommand("viewrecipe " + id);
            }, -1));
        }
        listPanel.set(rows);
    }

    private static List<Craftable> compute(Minecraft mc, Map<String, NeuRecipeIndex.Entry> all) {
        Map<String, Long> have = new HashMap<>();
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty()) continue;
                String id = ItemUtil.skyblockId(st);
                if (id.isEmpty()) id = vanillaId(st);
                if (id != null && !id.isEmpty()) have.merge(id, (long) st.getCount(), Long::sum);
            }
        }
        if (cfg().craftableListSacks) {
            for (Map.Entry<String, Integer> e : ItemIndex.sackAmounts().entrySet()) {
                String id = SkyblockItems.idForName(e.getKey());
                if (id != null) have.merge(id, (long) e.getValue(), Long::sum);
            }
        }
        List<Craftable> out = new ArrayList<>();
        if (have.isEmpty()) return out;
        for (Map.Entry<String, NeuRecipeIndex.Entry> e : all.entrySet()) {
            NeuRecipeIndex.Entry r = e.getValue();
            if (r.ingredients() == null || r.ingredients().isEmpty()) continue;
            if (cfg().craftableListNoVanilla && r.vanilla()) continue;
            long times = Long.MAX_VALUE;
            for (Map.Entry<String, Integer> ing : r.ingredients().entrySet()) {
                long h = have.getOrDefault(ing.getKey().replace('-', ':'), 0L);
                times = Math.min(times, h / Math.max(1, ing.getValue()));
                if (times == 0) break;
            }
            if (times <= 0 || times == Long.MAX_VALUE) continue;
            double cost = 0;
            for (Map.Entry<String, Integer> ing : r.ingredients().entrySet()) {
                cost += PriceManager.getPrice(ing.getKey().replace('-', ':')) * ing.getValue();
            }
            out.add(new Craftable(e.getKey().replace('-', ':'), r.name(), times, cost, r.ingredients()));
        }
        out.sort((a, b) -> Double.compare(b.cost(), a.cost()));
        return out;
    }

    private static String vanillaId(ItemStack st) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());
        return key == null ? null : key.getPath().toUpperCase(Locale.ROOT);
    }

    private static boolean listOpen() {
        return feature != null && feature.active() && cfg().craftableList && !listPanel.empty();
    }

    public static void render(GuiGraphicsExtractor g, int mx, int my) {
        if (feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen || !(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!listPanel.empty()) listPanel.render(g, cs, 0, mx, my);
        else if (!recipePanel.empty()) recipePanel.render(g, cs, 0, mx, my);
    }

    public static boolean click(double mx, double my, int button) {
        if (feature == null || !feature.active()) return false;
        if (!listPanel.empty()) return listPanel.click(mx, my, button);
        if (!recipePanel.empty() && recipePanel.click(mx, my, button)) {
            ticks = 1;
            return true;
        }
        return false;
    }

    public static boolean scroll(double mx, double my, double dy) {
        if (feature == null || !feature.active()) return false;
        return !listPanel.empty() && listPanel.scroll(mx, my, dy);
    }

    public static boolean charTyped(int cp) {
        if (!listOpen() || craftables == null) return false;
        if (cp < 32 || search.length() >= 30) return false;
        char ch = (char) cp;
        if (!Character.isLetterOrDigit(ch) && ch != ' ' && ch != '\'' && ch != '-') return false;
        search += ch;
        ticks = 0;
        return true;
    }

    public static boolean keyPressed(int key) {
        if (!listOpen() || craftables == null) return false;
        boolean printable = (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) || (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9)
                || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_APOSTROPHE;
        if (printable) return true;
        if (search.isEmpty()) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            search = search.substring(0, search.length() - 1);
            ticks = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            search = "";
            ticks = 0;
            return true;
        }
        return false;
    }
}
