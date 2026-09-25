package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.utils.SbRepo;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimated item value: what an item is worth once every applied upgrade is
 * priced in, plus the line-by-line breakdown the hover panel shows.
 */
public class EstimatedValue {

    private static final String FARMING_FOR_DUMMIES = "FARMING_FOR_DUMMIES";
    private static final String OVERCLOCKER_3000 = "OVERCLOCKER_3000";
    private static final String ETHERWARP_CONDUIT = "ETHERWARP_CONDUIT";
    private static final String ETHERWARP_MERGER = "ETHERWARP_MERGER";
    private static final String FUMING_POTATO_BOOK = "FUMING_POTATO_BOOK";
    private static final String HOT_POTATO_BOOK = "HOT_POTATO_BOOK";
    private static final String SILEX = "SIL_EX";
    private static final String TRANSMISSION_TUNER = "TRANSMISSION_TUNER";
    private static final String MANA_DISINTEGRATOR = "MANA_DISINTEGRATOR";
    private static final String RECOMBOBULATOR_3000 = "RECOMBOBULATOR_3000";
    private static final String JALAPENO_BOOK = "JALAPENO_BOOK";
    private static final String WOOD_SINGULARITY = "WOOD_SINGULARITY";
    private static final String DIVAN_POWDER_COATING = "DIVAN_POWDER_COATING";
    private static final String ART_OF_WAR = "THE_ART_OF_WAR";
    private static final String BOOK_OF_STATS = "BOOK_OF_STATS";
    private static final String ART_OF_PEACE = "THE_ART_OF_PEACE";
    private static final String WET_BOOK = "WET_BOOK";
    private static final String POLARVOID_BOOK = "POLARVOID_BOOK";
    private static final String POCKET_SACK_IN_A_SACK = "POCKET_SACK_IN_A_SACK";
    private static final String BOOKWORM_BOOK = "BOOKWORM_BOOK";
    private static final String STONK_PICKAXE = "STONK_PICKAXE";
    private static final String MITHRIL_INFUSION = "MITHRIL_INFUSION";
    private static final String FREE_WILL = "FREE_WILL";
    private static final String SKYBLOCK_COIN = "SKYBLOCK_COIN";

    private static final String[] MASTER_STARS = {"FIRST", "SECOND", "THIRD", "FOURTH", "FIFTH"};
    private static final String[] RARITIES = {
        "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL", "ULTIMATE"
    };
    private static final String[] KUUDRA_ARMOR_TIERS = {"", "HOT", "BURNING", "FIERY", "INFERNAL"};
    private static final Pattern KUUDRA_ARMOR = Pattern.compile(
            "(HOT|BURNING|FIERY|INFERNAL|)_?(AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)_(?:HELMET|CHESTPLATE|LEGGINGS|BOOTS)");
    private static final Pattern ENCHANT_ID = Pattern.compile("ENCHANTMENT_(.+)_(\\d+)");
    private static final String[] DRILL_PARTS = {"drill_part_upgrade_module", "drill_part_engine", "drill_part_fuel_tank"};
    private static final String[] ROD_PARTS = {"hook", "line", "sinker"};

    private static final Map<String, Character> GEM_SLOT_COLOR = Map.ofEntries(
            Map.entry("JADE", 'a'), Map.entry("AMBER", '6'), Map.entry("TOPAZ", 'e'),
            Map.entry("SAPPHIRE", 'b'), Map.entry("AMETHYST", '5'), Map.entry("JASPER", 'd'),
            Map.entry("RUBY", 'c'), Map.entry("OPAL", 'f'), Map.entry("ONYX", '8'),
            Map.entry("AQUAMARINE", '3'), Map.entry("CITRINE", '4'), Map.entry("PERIDOT", '2'),
            Map.entry("COMBAT", '4'), Map.entry("DEFENSIVE", 'a'), Map.entry("MINING", '5'),
            Map.entry("UNIVERSAL", 'f'));

    // ---------------------------------------------------------------- public API

    /** Total value of one of the item, 0 when nothing is known about it. */
    public static double compute(ItemStack stack) {
        Double total = totalPrice(stack, false);
        return total == null ? 0 : total;
    }

    /** SkyHanni's getTotalPrice: null when the item carries no upgrades over its base price. */
    public static Double totalPrice(ItemStack stack, boolean ignoreBasePrice) {
        double[] result = cached(stack).result;
        if (result == null) return null;
        if (ignoreBasePrice && result[0] == result[1]) return null;
        return result[0];
    }

    /** The hover panel: header, breakdown, total. Empty when there is nothing to show. */
    public static List<String> display(ItemStack stack) {
        Cached c = cached(stack);
        if (c.display == null) c.display = buildDisplay(stack);
        return c.display;
    }

    private static final class Cached {
        final Object data, lore;
        final long made;
        final int key;
        final double[] result;
        List<String> display;

        Cached(Object data, Object lore, long made, int key, double[] result) {
            this.data = data; this.lore = lore; this.made = made; this.key = key; this.result = result;
        }
    }

    private static final Map<ItemStack, Cached> CACHE = new java.util.WeakHashMap<>();

    private static Cached cached(ItemStack stack) {
        Object data = stack.get(DataComponents.CUSTOM_DATA);
        Object lore = stack.get(DataComponents.LORE);
        long now = System.currentTimeMillis();
        int key = cacheKey();
        synchronized (CACHE) {
            Cached c = CACHE.get(stack);
            if (c != null && c.data == data && c.lore == lore && c.key == key && now - c.made < 2000) return c;
        }
        Cached c = new Cached(data, lore, now, key, calculate(stack, null));
        synchronized (CACHE) { CACHE.put(stack, c); }
        return c;
    }

    private static int cacheKey() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int flags = (c.itemValueExactPrice ? 1 : 0) | (c.itemValueIgnoreHelmetSkins ? 2 : 0)
                | (c.itemValueIgnoreArmorDyes ? 4 : 0) | (c.itemValueIgnoreRunes ? 8 : 0)
                | (c.itemValueShowBin ? 16 : 0) | (SbRepo.loaded() ? 32 : 0) | (SkyblockItems.loaded() ? 64 : 0);
        int h = flags;
        h = h * 31 + c.itemValueEnchantmentsCap;
        h = h * 31 + c.itemValueStarMaterialCap;
        h = h * 31 + (c.itemValuePriceSource == null ? 0 : c.itemValuePriceSource.hashCode());
        return h * 31 + PriceManager.version();
    }

    private static List<String> buildDisplay(ItemStack stack) {
        if (shouldIgnore(stack)) return List.of();

        List<String> list = new ArrayList<>();
        list.add("§aEstimated Item Value:");
        double[] result = calculate(stack, list);
        if (result == null) return List.of();
        if (result[1] == result[0]) return List.of();

        String number = TeslaMapsConfig.get().itemValueExactPrice
                ? addSeparators(Math.round(result[0]))
                : shortFormat(result[0]);
        list.add("§aTotal: §6§l" + number + " coins");
        binLine(stack, list);
        return list;
    }

    /**
     * The raw lowest BIN of the base item next to the computed total. The total prices the
     * upgrades on top; the BIN is what the thing actually sells for right now, and the two
     * differ a lot on starred/enchanted gear.
     */
    private static void binLine(ItemStack stack, List<String> list) {
        if (!TeslaMapsConfig.get().itemValueShowBin) return;
        CompoundTag ea = extraAttributes(stack);
        if (ea == null) return;
        String id = ea.getStringOr("id", "");
        if (id.isEmpty()) return;
        Double bin = PriceManager.lowestBinOrNull(id);
        if (bin == null || bin <= 0) return;
        list.add("§7Lowest BIN: §6" + exact(bin));
    }

    /** 655000000 -> "655M", the same compact format SkyHanni uses. */
    public static String fmt(double value) {
        return shortFormat(value);
    }

    /** Compact or exact, whichever the config asks for. */
    public static String price(double value) {
        return TeslaMapsConfig.get().itemValueExactPrice
                ? addSeparators(Math.round(value)) : shortFormat(value);
    }

    /** Always exact: thousands separators, at most one decimal. */
    public static String exact(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(1);
        return nf.format(value);
    }

    // ---------------------------------------------------------------- calculator

    private static double[] calculate(ItemStack stack, List<String> list) {
        return calculate(stack, list, null);
    }

    private static double[] calculate(ItemStack stack, List<String> list, List<Part> parts) {
        CompoundTag ea = extraAttributes(stack);
        if (ea == null) return null;
        String id = ea.getStringOr("id", "");
        if (id.isEmpty()) return null;

        PriceManager.ensureFresh();
        SbRepo.load();

        List<String> lore = lore(stack);
        Recorder rec = new Recorder(list, parts);
        double basePrice = addBaseItem(id, list);
        // enchanted books get their value from ::addEnchantments, counting the base too would double up
        double total = rec.add(id.equals("ENCHANTED_BOOK") ? 0.0 : basePrice);

        total += rec.add(addReforgeStone(lore, ea, list));

        total += rec.add(addRecombobulator(ea, list));
        total += rec.add(addArtOfWar(ea, list));
        total += rec.add(addArtOfPeace(ea, list));
        total += rec.add(addEtherwarp(ea, list));
        total += rec.add(addPowerScrolls(ea, list));
        total += rec.add(addWoodSingularity(ea, list));
        total += rec.add(addJalapenoBook(ea, list));
        total += rec.add(addStatsBook(ea, list));
        total += rec.add(addEnrichment(ea, list));
        total += rec.add(addDivanPowderCoating(ea, list));
        total += rec.add(addMithrilInfusion(ea, list));
        total += rec.add(addFreeWill(ea, list));

        total += rec.add(addCrimsonPrestige(id, list));
        total += rec.add(addStars(lore, ea, id, list));
        total += rec.add(addMasterStars(lore, ea, list));
        total += rec.add(addHotPotatoBooks(ea, list));
        total += rec.add(addWetBook(ea, list));
        total += rec.add(addFarmingForDummies(ea, list));
        total += rec.add(addOverclocker(ea, list));
        total += rec.add(addSilex(ea, id, list));
        total += rec.add(addTransmissionTuners(ea, list));
        total += rec.add(addManaDisintegrators(ea, list));
        total += rec.add(addPolarvoidBook(ea, list));
        total += rec.add(addBookwormBook(ea, list));
        total += rec.add(addPocketSackInASack(ea, list));

        total += rec.add(addHelmetSkin(ea, list));
        total += rec.add(addArmorDye(ea, list));
        total += rec.add(addRune(ea, id, list));

        total += rec.add(addAbilityScrolls(ea, list));
        total += rec.add(addBoosters(ea, list));
        total += rec.add(addDrillUpgrades(ea, list));
        total += rec.add(addRodUpgrades(ea, list));
        total += rec.add(addGemstoneSlotUnlockCost(ea, id, list));
        total += rec.add(addGemstones(ea, list));
        total += rec.add(addEnchantments(ea, id, list));

        return new double[]{total, basePrice};
    }

    /** One value component with its breakdown lines, for the item value screen. */
    public record Part(List<String> lines, double value) {}

    private static final class Recorder {
        private final List<String> list;
        private final List<Part> parts;
        private int mark;

        Recorder(List<String> list, List<Part> parts) {
            this.list = list;
            this.parts = parts;
            this.mark = list == null ? 0 : list.size();
        }

        double add(double value) {
            if (parts == null || list == null) return value;
            if (list.size() > mark || value != 0) parts.add(new Part(new ArrayList<>(list.subList(mark, list.size())), value));
            mark = list.size();
            return value;
        }
    }

    /** Every component of the value with its lines, unsorted; empty when the item has no value. */
    public static List<Part> parts(ItemStack stack) {
        if (shouldIgnore(stack)) return List.of();
        List<Part> parts = new ArrayList<>();
        double[] result = calculate(stack, new ArrayList<>(), parts);
        if (result == null || result[0] <= 0) return List.of();
        return parts;
    }

    private static double addBaseItem(String rawId, List<String> list) {
        String id = removeKuudraTier(rawId);
        if (id.isEmpty()) return 0.0;

        boolean bundle = id.startsWith("ENCHANTED_BOOK_BUNDLE_");
        double price = bundle ? 0.0 : priceOr0(id);
        if (list != null) {
            String name = itemName(id);
            list.add(bundle ? "§7Base item: " + name
                    : "§7Base item: " + name + " " + formatCoinWithBrackets(price, false));
        }
        return price;
    }

    private static double addReforgeStone(List<String> lore, CompoundTag ea, List<String> list) {
        String modifier = attrString(ea, "modifier");
        if (modifier == null) return 0.0;

        SbRepo.Reforge reforge = SbRepo.reforgeByModifier(modifier);
        if (reforge == null || reforge.stoneId() == null || reforge.costs().isEmpty()) return 0.0;

        Integer applyCost = reforgeApplyCost(lore, ea, reforge.costs());
        if (applyCost == null) return 0.0;

        double stonePrice = priceOr0(reforge.stoneId());
        if (list != null) {
            list.add("§7Reforge: §9" + reforge.name());
            list.add(" §7Stone: " + itemName(reforge.stoneId()) + " " + formatCoinWithBrackets(stonePrice, false));
            list.add(" §7Apply cost: " + formatCoinWithBrackets(applyCost, false));
        }
        return stonePrice + applyCost;
    }

    private static Integer reforgeApplyCost(List<String> lore, CompoundTag ea, Map<String, Long> costs) {
        String rarity = itemRarity(lore);
        if (rarity == null) return null;

        int index = rarityIndex(rarity);
        if (index > rarityIndex("MYTHIC")) {
            rarity = "LEGENDARY";
        } else if (attrInt(ea, "rarity_upgrades") != null) {
            if (index <= 0) return null;
            rarity = RARITIES[index - 1];
        }

        Long cost = costs.get(rarity);
        return cost == null ? null : cost.intValue();
    }

    private static double addRecombobulator(CompoundTag ea, List<String> list) {
        if (attrInt(ea, "rarity_upgrades") == null) return 0.0;
        return formatHaving(list, "Recombobulated", RECOMBOBULATOR_3000);
    }

    private static double addJalapenoBook(CompoundTag ea, List<String> list) {
        if (attrInt(ea, "jalapeno_count") == null) return 0.0;
        return formatHaving(list, "Jalapeno Book", JALAPENO_BOOK);
    }

    private static double addWoodSingularity(CompoundTag ea, List<String> list) {
        if (attrInt(ea, "wood_singularity_count") == null) return 0.0;
        return formatHaving(list, "Wood Singularity", WOOD_SINGULARITY);
    }

    private static double addDivanPowderCoating(CompoundTag ea, List<String> list) {
        if (!ea.getBooleanOr("divan_powder_coating", false)) return 0.0;
        return formatHaving(list, "Divan Powder Coating", DIVAN_POWDER_COATING);
    }

    private static double addMithrilInfusion(CompoundTag ea, List<String> list) {
        if (ea.getByteOr("mithril_infusion", (byte) 0) != 1) return 0.0;
        return formatHaving(list, "Mithril Infusion", MITHRIL_INFUSION);
    }

    private static double addFreeWill(CompoundTag ea, List<String> list) {
        if (ea.getByteOr("free_will", (byte) 0) != 1) return 0.0;
        return formatHaving(list, "Free Will", FREE_WILL);
    }

    private static double addArtOfWar(CompoundTag ea, List<String> list) {
        if (attrInt(ea, "art_of_war_count") == null) return 0.0;
        return formatHaving(list, "The Art of War", ART_OF_WAR);
    }

    private static double addStatsBook(CompoundTag ea, List<String> list) {
        if (!ea.contains("stats_book")) return 0.0;
        return formatHaving(list, "Book of Stats", BOOK_OF_STATS);
    }

    private static double addArtOfPeace(CompoundTag ea, List<String> list) {
        if (!ea.getBooleanOr("artOfPeaceApplied", false)) return 0.0;
        return formatHaving(list, "The Art Of Peace", ART_OF_PEACE);
    }

    private static double addPowerScrolls(CompoundTag ea, List<String> list) {
        String id = attrString(ea, "power_ability_scroll");
        if (id == null) return 0.0;
        return formatHaving(list, StringUtil.strip(itemName(id)), id);
    }

    private static double formatHaving(List<String> list, String label, String id) {
        double price = priceOr0(id);
        if (list != null) list.add("§7" + label + ": §a§l✔ " + formatCoinWithBrackets(price, false));
        return price;
    }

    private static double addEtherwarp(CompoundTag ea, List<String> list) {
        if (!ea.getBooleanOr("ethermerge", false)) return 0.0;
        double price = priceOr0(ETHERWARP_CONDUIT) + priceOr0(ETHERWARP_MERGER);
        if (list != null) list.add("§7Etherwarp: §a§l✔ " + formatCoinWithBrackets(price, false));
        return price;
    }

    private static double addMasterStars(List<String> lore, CompoundTag ea, List<String> list) {
        Integer dungeonStars = dungeonStarCount(lore, ea);
        if (dungeonStars == null) return 0.0;

        int masterStars = Math.min(dungeonStars - 5, 5);
        if (masterStars < 1) return 0.0;

        double price = 0;
        for (int i = 0; i < masterStars; i++) price += priceOr0(MASTER_STARS[i] + "_MASTER_STAR");

        if (list != null) list.add(formatProgress("Master Stars", masterStars, 5, price));
        return price;
    }

    private static double addHotPotatoBooks(CompoundTag ea, List<String> list) {
        Integer count = attrInt(ea, "hot_potato_count");
        if (count == null) return 0.0;

        int hpb = Math.min(count, 10);
        int fuming = Math.max(0, count - 10);

        double total = priceOr0(HOT_POTATO_BOOK) * hpb;
        if (list != null) list.add(formatProgress("HPB's", hpb, 10, total));

        if (fuming > 0) {
            double fumingPrice = priceOr0(FUMING_POTATO_BOOK) * fuming;
            if (list != null) list.add(formatProgress("Fuming", fuming, 5, fumingPrice));
            total += fumingPrice;
        }
        return total;
    }

    private static double addWetBook(CompoundTag ea, List<String> list) {
        return counted(ea, "wet_book_count", WET_BOOK, "Wet Book", 5, list);
    }

    private static double addFarmingForDummies(CompoundTag ea, List<String> list) {
        return counted(ea, "farming_for_dummies_count", FARMING_FOR_DUMMIES, "Farming for Dummies", 5, list);
    }

    private static double addOverclocker(CompoundTag ea, List<String> list) {
        return counted(ea, "levelable_overclocks", OVERCLOCKER_3000, "Overclocker 3000", 10, list);
    }

    private static double addPolarvoidBook(CompoundTag ea, List<String> list) {
        return counted(ea, "polarvoid", POLARVOID_BOOK, "Polarvoid", 5, list);
    }

    private static double addPocketSackInASack(CompoundTag ea, List<String> list) {
        return counted(ea, "sack_pss", POCKET_SACK_IN_A_SACK, "Pocket Sack-in-a-Sack", 3, list);
    }

    private static double addBookwormBook(CompoundTag ea, List<String> list) {
        return counted(ea, "bookworm_books", BOOKWORM_BOOK, "Bookworm's Favorite Book", 5, list);
    }

    private static double addTransmissionTuners(CompoundTag ea, List<String> list) {
        return counted(ea, "tuned_transmission", TRANSMISSION_TUNER, "Transmission Tuners", 4, list);
    }

    private static double addManaDisintegrators(CompoundTag ea, List<String> list) {
        return counted(ea, "mana_disintegrator_count", MANA_DISINTEGRATOR, "Mana Disintegrators", 10, list);
    }

    private static double counted(CompoundTag ea, String key, String id, String label, int max, List<String> list) {
        Integer count = attrInt(ea, key);
        if (count == null) return 0.0;
        double price = priceOr0(id) * count;
        if (list != null) list.add(formatProgress(label, count, max, price));
        return price;
    }

    private static double addSilex(CompoundTag ea, String id, List<String> list) {
        Integer efficiency = attrInt(ea.getCompoundOrEmpty("enchantments"), "efficiency");
        if (efficiency == null) return 0.0;
        int tier = efficiency - 5 - baseSilexCount(id);
        if (tier <= 0) return 0.0;

        int maxTier = id.equals(STONK_PICKAXE) ? 4 : 5;
        double price = priceOr0(SILEX) * tier;
        if (list != null) list.add(formatProgress("Silex", tier, maxTier, price));
        return price;
    }

    private static int baseSilexCount(String id) {
        return switch (id) {
            case "STONK_PICKAXE" -> 1;
            case "PROMISING_SPADE" -> 5;
            default -> 0;
        };
    }

    private static String formatProgress(String label, int have, int max, double price) {
        return "§7" + label + ": §e" + have + "§7/§e" + max + " " + formatCoinWithBrackets(price, false);
    }

    private static double addCrimsonPrestige(String id, List<String> list) {
        if (!isKuudraArmor(id)) return 0.0;
        Integer tierIndex = armorKuudraTier(id);
        if (tierIndex == null || tierIndex <= 1) return 0.0;
        if (tierIndex - 1 >= KUUDRA_ARMOR_TIERS.length) return 0.0;
        String armorTier = KUUDRA_ARMOR_TIERS[tierIndex - 1];

        Map<String, Double> allTiersCost = new LinkedHashMap<>();
        for (int index = 1; index < tierIndex; index++) {
            if (index >= KUUDRA_ARMOR_TIERS.length) continue;
            Map<String, Integer> cost = SbRepo.crimsonPrestigeCost(KUUDRA_ARMOR_TIERS[index]);
            if (cost == null) return 0.0;
            for (Map.Entry<String, Integer> entry : cost.entrySet()) {
                allTiersCost.merge(entry.getKey(), entry.getValue().doubleValue(), Double::sum);
            }
        }

        Priced priced = totalAndNames(allTiersCost, list != null);
        if (list != null) {
            list.add("§7Prestige Tier: " + StringUtil.titleCase(armorTier) + " "
                    + formatCoinWithBrackets(priced.total(), false));
            list.addAll(priced.names());
        }
        return priced.total();
    }

    private static double addStars(List<String> lore, CompoundTag ea, String id, List<String> list) {
        Integer dungeonStars = dungeonStarCount(lore, ea);
        Integer plainStars = attrInt(ea, "upgrade_level");
        int totalStars = dungeonStars != null ? dungeonStars : plainStars != null ? plainStars : 0;

        StarPrice star = calculateStarPrice(id, totalStars);
        if (star == null) return 0.0;

        Map<String, Double> items = new LinkedHashMap<>();
        items.put("ESSENCE_" + star.essenceType().toUpperCase(Locale.ROOT), (double) star.essenceAmount());
        if (star.coins() != 0) items.put(SKYBLOCK_COIN, (double) star.coins());
        for (Map.Entry<String, Integer> entry : star.items().entrySet()) {
            items.put(entry.getKey(), entry.getValue().doubleValue());
        }

        Priced priced = totalAndNames(items, list != null);
        if (list != null) {
            list.add(formatProgress("Stars", star.havingStars(), star.maxStars(), priced.total()));
            int cap = Math.max(1, TeslaMapsConfig.get().itemValueStarMaterialCap);
            list.addAll(priced.names().subList(0, Math.min(cap, priced.names().size())));
        }
        return priced.total();
    }

    private record StarPrice(int essenceAmount, String essenceType, long coins, Map<String, Integer> items,
                             int havingStars, int maxStars) {}

    private static StarPrice calculateStarPrice(String id, int inputStars) {
        int totalStars = inputStars;
        StarPrice price;
        int maxStars;

        if (isKuudraArmor(id)) {
            Integer tierIndex = armorKuudraTier(id);
            int tier = (tierIndex == null ? 0 : tierIndex) - 1;
            totalStars += tier * 10;

            String removed = removeKuudraTier(id);
            int remainingStars = totalStars;
            maxStars = 0;
            StarPrice accumulated = null;

            TreeMap<Integer, String> tiers = new TreeMap<>();
            for (String essenceId : SbRepo.essenceItems()) {
                if (!essenceId.contains(removed)) continue;
                Integer t = armorKuudraTier(essenceId);
                tiers.put((t == null ? 0 : t) - 1, essenceId);
            }

            for (String essenceId : tiers.values()) {
                Map<Integer, SbRepo.EssenceTier> prices = SbRepo.essencePrices(essenceId);
                if (prices == null) continue;
                maxStars += prices.size();
                if (remainingStars <= 0) continue;

                StarPrice next = priceFor(prices, remainingStars);
                if (next == null) return null;
                accumulated = accumulated == null ? next : plus(accumulated, next);
                remainingStars -= prices.size();
            }
            if (accumulated == null) return null;
            price = accumulated;
        } else {
            Map<Integer, SbRepo.EssenceTier> prices = SbRepo.essencePrices(id);
            if (totalStars == 0 || prices == null) return null;
            price = priceFor(prices, totalStars);
            if (price == null) return null;
            maxStars = prices.size();
        }

        int havingStars = Math.min(totalStars, maxStars);
        return new StarPrice(price.essenceAmount(), price.essenceType(), price.coins(), price.items(),
                havingStars, maxStars);
    }

    private static StarPrice priceFor(Map<Integer, SbRepo.EssenceTier> prices, int totalStars) {
        String essenceType = null;
        int essenceAmount = 0;
        long coins = 0;
        Map<String, Integer> items = new LinkedHashMap<>();

        for (Map.Entry<Integer, SbRepo.EssenceTier> entry : prices.entrySet()) {
            if (entry.getKey() > totalStars) break;
            SbRepo.EssenceTier tier = entry.getValue();
            essenceType = tier.essenceType();
            essenceAmount += tier.essenceAmount();
            coins += tier.coins();
            for (Map.Entry<String, Integer> item : tier.items().entrySet()) {
                items.merge(item.getKey(), item.getValue(), Integer::sum);
            }
        }
        if (essenceType == null) return null;
        return new StarPrice(essenceAmount, essenceType, coins, items, 0, 0);
    }

    private static StarPrice plus(StarPrice a, StarPrice b) {
        Map<String, Integer> items = new LinkedHashMap<>(a.items());
        for (Map.Entry<String, Integer> item : b.items().entrySet()) items.merge(item.getKey(), item.getValue(), Integer::sum);
        return new StarPrice(a.essenceAmount() + b.essenceAmount(), a.essenceType(), a.coins() + b.coins(), items, 0, 0);
    }

    private static double addDrillUpgrades(CompoundTag ea, List<String> list) {
        Map<String, Double> items = new LinkedHashMap<>();
        for (String part : DRILL_PARTS) {
            String upgrade = attrString(ea, part);
            if (upgrade != null) items.put(upgrade.toUpperCase(Locale.ROOT), 1.0);
        }
        return grouped(items, "Drill upgrades", list);
    }

    private static double addRodUpgrades(CompoundTag ea, List<String> list) {
        Map<String, Double> items = new LinkedHashMap<>();
        for (String part : ROD_PARTS) {
            String upgrade = attrString(ea, part);
            if (upgrade != null) items.put(upgrade.toUpperCase(Locale.ROOT), 1.0);
        }
        return grouped(items, "Rod upgrades", list);
    }

    private static double addAbilityScrolls(CompoundTag ea, List<String> list) {
        ListTag scrolls = ea.getListOrEmpty("ability_scroll");
        if (scrolls.isEmpty()) return 0.0;

        Map<String, Double> items = new LinkedHashMap<>();
        for (int i = 0; i < scrolls.size(); i++) {
            String scroll = scrolls.getStringOr(i, "");
            if (scroll.isBlank()) continue;
            if (scroll.equals("ULTIMATE_WITHER_SCROLL")) {
                items.put("IMPLOSION_SCROLL", 1.0);
                items.put("WITHER_SHIELD_SCROLL", 1.0);
                items.put("SHADOW_WARP_SCROLL", 1.0);
                continue;
            }
            items.put(scroll, 1.0);
        }
        return grouped(items, "Ability Scrolls", list);
    }

    private static double addBoosters(CompoundTag ea, List<String> list) {
        ListTag boosters = ea.getListOrEmpty("boosters");
        if (boosters.isEmpty()) return 0.0;

        Map<String, Double> items = new LinkedHashMap<>();
        for (int i = 0; i < boosters.size(); i++) {
            String booster = boosters.getStringOr(i, "");
            if (booster.isBlank()) continue;
            items.put(booster + "_BOOSTER", 1.0);
        }
        return grouped(items, "Boosters", list);
    }

    private static double addGemstones(CompoundTag ea, List<String> list) {
        Map<String, Double> items = new LinkedHashMap<>();
        for (String[] gem : appliedGems(ea, false)) items.merge(gem[0] + "_" + gem[1] + "_GEM", 1.0, Double::sum);
        if (items.isEmpty()) return 0.0;
        return grouped(items, "Gemstones Applied", list);
    }

    /** Applied gemstones as {QUALITY, TYPE}, e.g. {"PERFECT", "RUBY"}. */
    public static List<String[]> appliedGems(CompoundTag ea, boolean resolveSlotType) {
        CompoundTag gems = ea.getCompoundOrEmpty("gems");
        if (gems.isEmpty()) return List.of();
        List<String[]> out = new ArrayList<>();
        for (String key : gems.keySet()) {
            if (key.endsWith("_gem") || key.equals("unlocked_slots")) continue;

            String quality = gems.getStringOr(key, "");
            if (quality.isEmpty()) {
                quality = gems.getCompoundOrEmpty(key).getStringOr("quality", "");
                if (quality.isEmpty()) continue;
            }

            String type = key.split("_")[0];
            if (!GEM_SLOT_COLOR.containsKey(type)) {
                type = gems.getStringOr(key + "_gem", "").toUpperCase(Locale.ROOT);
                if (!GEM_SLOT_COLOR.containsKey(type)) continue;
            } else if (resolveSlotType && (type.equals("COMBAT") || type.equals("DEFENSIVE") || type.equals("MINING") || type.equals("UNIVERSAL"))) {
                String real = gems.getStringOr(key + "_gem", "").toUpperCase(Locale.ROOT);
                if (!real.isEmpty()) type = real;
            }
            out.add(new String[]{quality.toUpperCase(Locale.ROOT), type});
        }
        return out;
    }

    public static char gemColor(String type) {
        return GEM_SLOT_COLOR.getOrDefault(type, 'f');
    }

    private static double addGemstoneSlotUnlockCost(CompoundTag ea, String id, List<String> list) {
        CompoundTag gems = ea.getCompoundOrEmpty("gems");
        if (!gems.contains("unlocked_slots")) return 0.0;
        if (!SbRepo.knowsGemstoneCosts(id)) return 0.0;
        if (SbRepo.hasLegacyGemstoneSlots(id)) return 0.0;

        String unlocked = gems.getListOrEmpty("unlocked_slots").toString();
        Map<String, List<String>> slots = SbRepo.gemstoneCosts(id);
        if (slots == null) return 0.0;

        Map<String, Double> items = new LinkedHashMap<>();
        List<String> slotNames = new ArrayList<>();
        int matched = 0;
        for (Map.Entry<String, List<String>> slot : slots.entrySet()) {
            if (!unlocked.contains(slot.getKey())) continue;
            matched++;

            for (String ingredient : slot.getValue()) {
                String[] split = ingredient.split(":");
                if (split.length != 2) continue;
                double amount;
                try { amount = Double.parseDouble(split[1]); } catch (NumberFormatException e) { continue; }
                items.merge(split[0], amount, Double::sum);
            }

            if (list == null) continue;
            String[] parts = slot.getKey().split("_");
            char color = GEM_SLOT_COLOR.getOrDefault(parts[0], 'f');
            String name = StringUtil.titleCase(parts[0]) + " Slot"
                    + (parts.length > 1 && !parts[1].equals("0") ? " " + (Integer.parseInt(parts[1]) + 1) : "");
            slotNames.add("§" + color + name);
        }
        if (matched == 0) return 0.0;

        Priced priced = totalAndNames(items, list != null);
        if (list != null) {
            list.add("§7Gemstone Slot Unlock Cost: " + formatCoin(priced.total(), false));
            list.addAll(priced.names());
            list.add(" §7Unlocked slots: " + String.join("§7, ", slotNames));
        }
        return priced.total();
    }

    private static double addEnchantments(CompoundTag ea, String id, List<String> list) {
        CompoundTag enchantments = ea.getCompoundOrEmpty("enchantments");
        if (enchantments.isEmpty()) return 0.0;

        Map<String, Double> items = new LinkedHashMap<>();
        for (String rawName : enchantments.keySet()) {
            // efficiency 1-5 is cheap, 6-10 is handled by silex
            if (rawName.equals("efficiency")) continue;
            int rawLevel = enchantments.getIntOr(rawName, 0);
            if (rawLevel <= 0) continue;

            SbRepo.AlwaysActive alwaysActive = SbRepo.alwaysActive(rawName);
            if (alwaysActive != null && alwaysActive.level() == rawLevel && alwaysActive.items().contains(id)) continue;

            List<SbRepo.Endcap> endcaps = SbRepo.endcaps(rawName);
            int level = rawLevel;
            if (endcaps != null) {
                int minRequired = Integer.MAX_VALUE;
                for (SbRepo.Endcap endcap : endcaps) minRequired = Math.min(minRequired, endcap.requiredLevel());
                if (rawLevel >= minRequired) level = minRequired;
            }

            int multiplier = 1;
            if (SbRepo.onlyTierOnePrice(rawName) && level >= 2 && level <= 5) {
                multiplier = 1 << (level - 1);
                level = 1;
            } else if (SbRepo.onlyTierFivePrice(rawName) && level >= 6 && level <= 10) {
                multiplier = 1 << (level - 5);
                if (multiplier > 1) level = 5;
            }
            if (id.startsWith("ENCHANTED_BOOK_BUNDLE_")) multiplier = SbRepo.bookBundleAmount(rawName);
            if (SbRepo.isStackingEnchant(rawName)) level = 1;

            if (endcaps != null) {
                for (SbRepo.Endcap endcap : endcaps) {
                    if (rawLevel >= endcap.requiredLevel() + 1) items.put(endcap.item(), 1.0);
                }
            }

            String bazaarId = "ENCHANTMENT_" + rawName.toUpperCase(Locale.ROOT) + "_" + level;
            if (PriceManager.hasBazaar(bazaarId)) items.put(bazaarId, (double) multiplier);
        }
        if (items.isEmpty()) return 0.0;

        Priced priced = totalAndNames(items, list != null);
        if (priced.names().isEmpty() && list != null) return 0.0;
        if (list != null) {
            list.add("§7Enchantments: " + formatCoin(priced.total(), false));
            int cap = Math.max(1, TeslaMapsConfig.get().itemValueEnchantmentsCap);
            List<String> names = priced.names();
            for (int i = 0; i < names.size(); i++) {
                if (i == cap) {
                    list.add(" §7§o" + (names.size() - cap) + " more enchantments..");
                    break;
                }
                list.add(names.get(i));
            }
        }
        return priced.total();
    }

    private static double addHelmetSkin(CompoundTag ea, List<String> list) {
        String id = attrString(ea, "skin");
        if (id == null) return 0.0;
        return addCosmetic(id, list, "Skin", TeslaMapsConfig.get().itemValueIgnoreHelmetSkins);
    }

    private static double addArmorDye(CompoundTag ea, List<String> list) {
        String id = attrString(ea, "dye_item");
        if (id == null) return 0.0;
        return addCosmetic(id, list, "Dye", TeslaMapsConfig.get().itemValueIgnoreArmorDyes);
    }

    private static double addRune(CompoundTag ea, String itemId, List<String> list) {
        if (itemId.endsWith("_RUNE") || itemId.contains("_RUNE;")) return 0.0;
        CompoundTag runes = ea.getCompoundOrEmpty("runes");
        if (runes.isEmpty()) return 0.0;

        String name = runes.keySet().iterator().next();
        int tier = runes.getIntOr(name, 0);
        return addCosmetic(name.toUpperCase(Locale.ROOT) + "_RUNE;" + tier, list, "Rune",
                TeslaMapsConfig.get().itemValueIgnoreRunes);
    }

    private static double addCosmetic(String id, List<String> list, String label, boolean ignorePrice) {
        double price = priceOr0(id);
        if (list != null) {
            String name = SkyblockItems.coloredName(id);
            list.add("§7" + label + ": " + (name != null ? name : "§c" + id) + " "
                    + formatCoinWithBrackets(price, ignorePrice));
            if (name == null) list.add("   §8(Not yet in the item table)");
        }
        return ignorePrice ? 0.0 : price;
    }

    private static double addEnrichment(CompoundTag ea, List<String> list) {
        String enrichment = attrString(ea, "talisman_enrichment");
        if (enrichment == null) return 0.0;

        String id = "TALISMAN_ENRICHMENT_" + enrichment.toUpperCase(Locale.ROOT);
        double price = priceOr0(id);
        if (list != null) list.add("§7Enrichment: " + itemName(id) + " " + formatCoinWithBrackets(price, false));
        return price;
    }

    // ---------------------------------------------------------------- name / price helpers

    private record Priced(double total, List<String> names) {}

    private static double grouped(Map<String, Double> items, String label, List<String> list) {
        if (items.isEmpty()) return 0.0;
        Priced priced = totalAndNames(items, list != null);
        if (list != null && !priced.names().isEmpty()) {
            list.add("§7" + label + ": " + formatCoin(priced.total(), false));
            list.addAll(priced.names());
        }
        return priced.total();
    }

    private static Priced totalAndNames(Map<String, Double> items, boolean withNames) {
        double total = 0;
        List<String[]> rows = withNames ? new ArrayList<>() : null;

        for (Map.Entry<String, Double> entry : items.entrySet()) {
            Double price = price(entry.getKey());
            double amount = entry.getValue();
            if (price != null) {
                total += price * amount;
                if (rows != null) rows.add(new String[]{priceName(entry.getKey(), amount, price), String.valueOf(price)});
            } else if (rows != null) {
                rows.add(new String[]{" " + numberedName(entry.getKey(), amount) + " §cUnknown price!",
                        String.valueOf(Double.MAX_VALUE)});
            }
        }
        if (rows == null) return new Priced(total, List.of());

        rows.sort((a, b) -> Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));
        List<String> names = new ArrayList<>(rows.size());
        for (String[] row : rows) names.add(row[0]);
        return new Priced(total, names);
    }

    private static String priceName(String id, double amount, double pricePer) {
        double price = pricePer * amount;
        if (id.equals(SKYBLOCK_COIN)) return formatCoin(price, false) + " coins";
        return numberedName(id, amount) + " " + formatCoinWithBrackets(price, false);
    }

    private static String numberedName(String id, double amount) {
        String prefix = amount == 1.0 ? "" : "§8" + addSeparators(Math.round(amount)) + "x ";
        return prefix + "§r" + itemName(id);
    }

    private static String itemName(String id) {
        if (id.equals(SKYBLOCK_COIN)) return "§6Coins";
        Matcher enchant = ENCHANT_ID.matcher(id);
        if (enchant.matches()) {
            return SbRepo.enchantName(enchant.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(enchant.group(2)));
        }
        String name = SkyblockItems.coloredName(id);
        if (name != null) return name;
        if (id.startsWith("ESSENCE_")) return "§d" + StringUtil.titleCase(id.substring(8)) + " Essence";
        int semicolon = id.indexOf(';');
        if (semicolon > 0) {
            String base = SkyblockItems.coloredName(id.substring(0, semicolon));
            if (base != null) return base + " " + id.substring(semicolon + 1);
        }
        return "§c" + id;
    }

    private static Double price(String id) {
        return PriceManager.getPriceOrNull(id, priceSource());
    }

    static int priceSource() {
        String source = TeslaMapsConfig.get().itemValuePriceSource;
        if ("BZ Instant Buy".equals(source)) return PriceManager.SOURCE_BAZAAR_BUY;
        if ("NPC Sell".equals(source)) return PriceManager.SOURCE_NPC;
        if ("Lowest BIN".equals(source)) return PriceManager.SOURCE_LOWEST_BIN;
        return PriceManager.SOURCE_BAZAAR_SELL;
    }

    private static double priceOr0(String id) {
        Double price = price(id);
        return price == null ? 0.0 : price;
    }

    // ---------------------------------------------------------------- formatting

    private static final TreeMap<Long, String> SUFFIXES = new TreeMap<>(Map.of(
            1_000L, "k",
            1_000_000L, "M",
            1_000_000_000L, "B",
            1_000_000_000_000L, "T",
            1_000_000_000_000_000L, "P",
            1_000_000_000_000_000_000L, "E"));

    /** SkyHanni's compact format, taken from the same StackOverflow answer (CC BY-SA 3.0). */
    public static String shortFormat(double input) {
        if (Math.abs(input) < 1) return String.valueOf(Math.round(input * 10.0) / 10.0);
        long value = (long) input;
        if (value < 0) return "-" + shortFormat(-value);
        if (value < 1000) return String.valueOf(value);

        Map.Entry<Long, String> entry = SUFFIXES.floorEntry(value);
        long divideBy = entry.getKey();
        String suffix = entry.getValue();

        long truncated = value / (divideBy / 10);
        long truncatedAt = suffix.equals("M") ? 1000 : suffix.equals("B") ? 1000000 : 100;
        boolean hasDecimal = truncated < truncatedAt && truncated / 10.0 != (double) (truncated / 10);

        return hasDecimal ? (truncated / 10.0) + suffix : (truncated / 10) + suffix;
    }

    private static String addSeparators(long value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }

    private static String formatCoinWithBrackets(double value, boolean gray) {
        return "§7(" + formatCoin(value, gray) + "§7)";
    }

    private static String formatCoin(double value, boolean gray) {
        String color = gray ? "§7" : value < 0 ? "§c" : "§6";
        return color + shortFormat(value);
    }

    // ---------------------------------------------------------------- item reading

    private static CompoundTag extraAttributes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static String attrString(CompoundTag tag, String key) {
        String value = tag.getStringOr(key, "");
        return value.isBlank() ? null : value;
    }

    private static Integer attrInt(CompoundTag tag, String key) {
        int value = tag.getIntOr(key, 0);
        return value == 0 ? null : value;
    }

    private static Integer dungeonStarCount(List<String> lore, CompoundTag ea) {
        if (!isDungeonItem(lore)) return null;
        Integer stars = attrInt(ea, "upgrade_level");
        return stars != null ? stars : attrInt(ea, "dungeon_item_level");
    }

    private static boolean isDungeonItem(List<String> lore) {
        for (String line : lore) {
            if (line.contains("DUNGEON ")) return true;
        }
        return false;
    }

    private static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return List.of();
        List<String> out = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) out.add(StringUtil.stripTrim(line.getString()));
        return out;
    }

    private static String itemRarity(List<String> lore) {
        for (int i = lore.size() - 1; i >= 0; i--) {
            String line = lore.get(i);
            if (line.isEmpty()) continue;
            for (int r = RARITIES.length - 1; r >= 0; r--) {
                if (line.contains(RARITIES[r].replace('_', ' '))) return RARITIES[r];
            }
        }
        return null;
    }

    private static int rarityIndex(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return i;
        return -1;
    }

    private static boolean isKuudraArmor(String id) {
        return KUUDRA_ARMOR.matcher(id).matches();
    }

    private static Integer armorKuudraTier(String id) {
        Matcher matcher = KUUDRA_ARMOR.matcher(id);
        if (!matcher.matches()) return null;
        String tier = matcher.group(1);
        for (int i = 0; i < KUUDRA_ARMOR_TIERS.length; i++) {
            if (KUUDRA_ARMOR_TIERS[i].equals(tier)) return i + 1;
        }
        return null;
    }

    private static String removeKuudraTier(String id) {
        Matcher matcher = KUUDRA_ARMOR.matcher(id);
        if (!matcher.matches()) return id;
        String tier = matcher.group(1);
        if (tier.isEmpty()) return id;
        return id.startsWith(tier + "_") ? id.substring(tier.length() + 1) : id;
    }

    /** Items SkyHanni never draws a breakdown for. */
    private static boolean shouldIgnore(ItemStack stack) {
        CompoundTag ea = extraAttributes(stack);
        if (ea == null) return true;
        String id = ea.getStringOr("id", "");
        if (id.isEmpty()) return true;

        String name = stack.getHoverName().getString();
        return id.equals("ENCHANTED_BOOK")
                || name.contains("Salesperson")
                || StringUtil.stripTrim(name).equals("☘ Category: Item Ability (Passive)")
                || id.endsWith("_RUNE") || id.contains("_RUNE;")
                || id.startsWith("ULTIMATE_ULTIMATE_")
                || id.startsWith("CATACOMBS_PASS_")
                || id.startsWith("MASTER_CATACOMBS_PASS_")
                || id.startsWith("MAP-")
                || id.contains("UNIQUE_RUNE")
                || id.contains("WISP_POTION");
    }

    private EstimatedValue() {}
}
