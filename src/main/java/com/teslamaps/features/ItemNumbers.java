package com.teslamaps.features;

// Ported from SkyHanni's ItemDisplayOverlayFeatures
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** A number where the stack size goes, per item type picked from a list. */
public class ItemNumbers {

    public static final List<String> TYPES = List.of(
            "Master Star Tier", "Master Skull Tier", "Dungeon Head Floor", "Dungeon Item Quality",
            "Star Count", "New Year Cake Year", "Pet Level", "Minion Tier", "Kuudra Key Tier",
            "Collection Level", "Bestiary Tier", "SkyBlock Level", "Rancher's Boots Speed",
            "Larva Hook Harvests", "Dungeon Potion Level", "Vacuum Pests", "Evolving Item Hours", "Edition",
            "Prehistoric Egg Blocks", "Toolkit Indicator");

    private static final Pattern MASTER_SKULL = Pattern.compile("MASTER_SKULL_TIER_(\\d)");
    private static final Pattern PET = Pattern.compile("^\\[Lvl (\\d+)].*");
    private static final Pattern VACUUM = Pattern.compile("Vacuum Bag: ([\\d,]+) Pests?");
    private static final Pattern HARVEST = Pattern.compile("You may harvest (\\d+).*");
    private static final Pattern POTION = Pattern.compile("Dungeon (\\S+) Potion(?: x1)?");
    private static final Pattern SB_LEVEL = Pattern.compile("Your SkyBlock Level: \\[(\\d+)]");
    private static final Pattern BESTIARY = Pattern.compile("Progress to Tier (\\S+): [\\d.]+%");

    /** Read by ItemNumbersMixin, per item draw. */
    public static volatile boolean on;

    private record Entry(ItemLore lore, CustomData data, Screen screen, int version, String tip) {}

    private static final Map<ItemStack, Entry> CACHE = new WeakHashMap<>();
    private static int version;
    private static int lastTypesHash;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ItemNumbers").tickAlways(ItemNumbers::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Item Numbers As Stack Size", () -> c.itemNumbers, v -> c.itemNumbers = v)
                            .desc("Pet level, minion tier, key tier, potion level ... where the stack size goes.");
                    b.searchList("  Number Types", () -> TYPES, c.itemNumberTypes::contains, t -> {
                        if (!c.itemNumberTypes.remove(t)) c.itemNumberTypes.add(t);
                    });
                });
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        on = c.itemNumbers && !c.itemNumberTypes.isEmpty() && c.section("Inventory");
        int h = c.itemNumberTypes.hashCode();
        if (h != lastTypesHash) {
            lastTypesHash = h;
            version++;
        }
    }

    public static String tip(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        Screen screen = Minecraft.getInstance().gui.screen();
        Entry e;
        synchronized (CACHE) { e = CACHE.get(stack); }
        if (e != null && e.lore == lore && e.data == data && e.screen == screen && e.version == version) return e.tip;
        String tip;
        try {
            tip = compute(stack, data, screen);
        } catch (RuntimeException ex) {
            tip = null;
        }
        synchronized (CACHE) { CACHE.put(stack, new Entry(lore, data, screen, version, tip)); }
        return tip;
    }

    private static boolean sel(String type) {
        return cfg().itemNumberTypes.contains(type);
    }

    private static String compute(ItemStack stack, CustomData data, Screen screen) {
        String id = ItemUtil.skyblockId(stack);
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        String title = screen instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
        CompoundTag ea = null;

        if (sel("Master Star Tier")) {
            switch (id) {
                case "FIRST_MASTER_STAR": return "1";
                case "SECOND_MASTER_STAR": return "2";
                case "THIRD_MASTER_STAR": return "3";
                case "FOURTH_MASTER_STAR": return "4";
                case "FIFTH_MASTER_STAR": return "5";
                default: break;
            }
        }
        if (sel("Master Skull Tier")) {
            Matcher m = MASTER_SKULL.matcher(id);
            if (m.matches()) return m.group(1);
        }
        if (sel("Dungeon Head Floor") && (id.contains("GOLD_") || id.contains("DIAMOND_"))) {
            if (id.contains("BONZO")) return "1";
            if (id.contains("SCARF")) return "2";
            if (id.contains("PROFESSOR")) return "3";
            if (id.contains("THORN")) return "4";
            if (id.contains("LIVID")) return "5";
            if (id.contains("SADAN")) return "6";
            if (id.contains("NECRON")) return "7";
        }
        if (sel("Dungeon Item Quality")) {
            ea = data.copyTag();
            int q = ea.getIntOr("baseStatBoostPercentage", -1);
            if (q >= 0 && ea.contains("baseStatBoostPercentage")) return String.valueOf(q);
        }
        if (sel("Star Count")) {
            if (ea == null) ea = data.copyTag();
            int s = Math.max(ea.getIntOr("upgrade_level", 0), ea.getIntOr("dungeon_item_level", 0));
            if (s > 0) return "§6" + s;
        }
        if (sel("New Year Cake Year") && id.equals("NEW_YEAR_CAKE")) {
            if (ea == null) ea = data.copyTag();
            int y = ea.getIntOr("new_years_cake", -1);
            if (y >= 0) return "§b" + y;
        }
        if (sel("Pet Level") && name.startsWith("[Lvl ")) {
            Matcher m = PET.matcher(name);
            if (m.matches()) {
                int lvl = Integer.parseInt(m.group(1));
                boolean maxed = lvl >= 200 || (lvl == 100 && !name.contains("Dragon"));
                if (!maxed) return String.valueOf(lvl);
            }
        }
        if (sel("Minion Tier") && name.contains(" Minion ") && !name.contains("Recipe")
                && ItemUtil.loreContains(stack, "Place this minion")) {
            int t = roman(name.substring(name.lastIndexOf(' ') + 1));
            if (t > 0) return String.valueOf(t);
        }
        if (sel("Kuudra Key Tier") && name.contains("Kuudra Key")) {
            return switch (id) {
                case "KUUDRA_TIER_KEY" -> "§a1";
                case "KUUDRA_HOT_TIER_KEY" -> "§22";
                case "KUUDRA_BURNING_TIER_KEY" -> "§e3";
                case "KUUDRA_FIERY_TIER_KEY" -> "§64";
                case "KUUDRA_INFERNAL_TIER_KEY" -> "§c5";
                default -> "§4?";
            };
        }
        if (sel("Collection Level") && title.endsWith(" Collections") && ItemUtil.loreContains(stack, "Click to view!")) {
            int lvl = roman(name.substring(name.lastIndexOf(' ') + 1));
            if (lvl > 0) return String.valueOf(lvl);
        }
        if (sel("Bestiary Tier") && (title.contains("Bestiary ➜") || title.contains("Fishing ➜"))
                && ItemUtil.loreContains(stack, "Deaths: ")) {
            for (String l : ItemUtil.loreLines(stack)) {
                Matcher m = BESTIARY.matcher(l.trim());
                if (m.matches()) {
                    int t = roman(m.group(1));
                    if (t > 0) return String.valueOf(t - 1);
                }
            }
            int t = roman(name.substring(name.lastIndexOf(' ') + 1));
            if (t > 0) return String.valueOf(t);
        }
        if (sel("SkyBlock Level") && title.equals("SkyBlock Menu") && name.equals("SkyBlock Leveling")) {
            for (String l : ItemUtil.loreLines(stack)) {
                Matcher m = SB_LEVEL.matcher(l.trim());
                if (m.find()) return m.group(1);
            }
        }
        if (sel("Rancher's Boots Speed") && id.equals("RANCHERS_BOOTS")) {
            if (ea == null) ea = data.copyTag();
            int s = ea.getIntOr("ranchers_speed", -1);
            if (s >= 0) return (s > 400 ? "§c" : "§a") + (s > 999 ? "1k" : String.valueOf(s));
        }
        if (sel("Larva Hook Harvests") && id.equals("LARVA_HOOK")) {
            for (String l : ItemUtil.loreLines(stack)) {
                Matcher m = HARVEST.matcher(l.trim());
                if (m.matches()) {
                    int a = Integer.parseInt(m.group(1));
                    return (a > 4 ? "§a" : a > 2 ? "§e" : "§c") + a;
                }
            }
        }
        if (sel("Dungeon Potion Level") && name.startsWith("Dungeon ") && name.contains(" Potion")) {
            Matcher m = POTION.matcher(name);
            if (m.matches()) {
                int lvl = roman(m.group(1));
                if (lvl > 0) return (lvl <= 2 ? "§f" : lvl <= 4 ? "§a" : lvl <= 6 ? "§9" : "§5") + lvl;
            }
        }
        if (sel("Vacuum Pests") && id.contains("VACUUM") && ownItem(stack)) {
            for (String l : ItemUtil.loreLines(stack)) {
                Matcher m = VACUUM.matcher(l.trim());
                if (!m.find()) continue;
                long p = Long.parseLong(m.group(1).replace(",", ""));
                if (p < 40) return String.valueOf(p);
                if (p < 1_000) return "§6" + p;
                if (p < 100_000) return "§c" + (p / 1000) + "k";
                return "§c" + (p / 100_000 / 10.0) + "m";
            }
        }
        if (sel("Evolving Item Hours")) {
            if (ea == null) ea = data.copyTag();
            String key = switch (id) {
                case "NEW_BOTTLE_OF_JYRRE" -> "bottle_of_jyrre_seconds";
                case "DISCRITE" -> "rift_discrite_seconds";
                default -> "seconds_held";
            };
            if (ea.contains(key)) return "§a" + (ea.getIntOr(key, 0) / 3600);
        }
        if (sel("Prehistoric Egg Blocks") && id.equals("PREHISTORIC_EGG")) {
            if (ea == null) ea = data.copyTag();
            if (ea.contains("blocks_walked")) {
                int w = ea.getIntOr("blocks_walked", 0);
                if (w < 1000) return String.valueOf(w);
                if (w < 10000) return (w / 100) / 10.0 + "k";
                return w / 1000 + "k";
            }
        }
        if (sel("Edition")) {
            if (ea == null) ea = data.copyTag();
            int ed = ea.getIntOr("edition", -1);
            if (ed >= 0 && ed < 1000 && ea.contains("edition")) return "§6" + ed;
        }
        return null;
    }

    private record Toolkit(ItemLore lore, boolean part) {}

    private static final Map<ItemStack, Toolkit> TOOLKIT = new WeakHashMap<>();

    /** Red ❒ in the top-left corner of a hunting toolkit item. */
    public static void drawToolkit(net.minecraft.client.gui.GuiGraphicsExtractor ctx, net.minecraft.world.inventory.Slot slot) {
        if (!on || !sel("Toolkit Indicator")) return;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return;
        Toolkit t = TOOLKIT.get(stack);
        if (t == null || t.lore != lore) {
            boolean part = false;
            for (var l : lore.lines()) {
                if (l.getString().contains("Part of a Toolkit!")) { part = true; break; }
            }
            t = new Toolkit(lore, part);
            TOOLKIT.put(stack, t);
        }
        if (t.part) SlotText.draw(ctx, Minecraft.getInstance().font, "❒", slot.x, slot.y, 0, 0xFFFF5555);
    }

    private static boolean ownItem(ItemStack stack) {
        for (String l : ItemUtil.loreLines(stack)) {
            if (l.contains("Click to trade!") || l.contains("Starting bid:") || l.contains("Buy it now:")
                    || l.contains("Click to inspect")) return false;
        }
        return true;
    }

    private static int roman(String s) {
        if (s.isEmpty()) return -1;
        if (Character.isDigit(s.charAt(0))) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
        }
        int result = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100;
                default -> -1;
            };
            if (v < 0) return -1;
            result += v < prev ? -v : v;
            prev = v;
        }
        return result;
    }
}
