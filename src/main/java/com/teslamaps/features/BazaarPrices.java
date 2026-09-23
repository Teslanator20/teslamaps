package com.teslamaps.features;

import com.teslamaps.TeslaMaps;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Instant-sell prices from the public bazaar endpoint. No API key needed for
 * this one, unlike the rest of the Hypixel API.
 * <p>
 * Every product is kept and ids are derived from the display name, so new drops
 * price themselves without the table being extended by hand. Only names whose id
 * does not follow from the name are listed as exceptions.
 */
public class BazaarPrices {

    /** Display names whose bazaar id cannot be guessed from the name. */
    private static final Map<String, String> EXCEPTIONS = new HashMap<>();

    static {
        put("Melon Slice", "MELON");
        put("Carrot", "CARROT_ITEM");
        put("Potato", "POTATO_ITEM");
        put("Cocoa Beans", "INK_SACK:3");
        put("Nether Wart", "NETHER_STALK");
        put("Enchanted Nether Wart", "ENCHANTED_NETHER_STALK");
        put("Enchanted Cocoa Bean", "ENCHANTED_COCOA");
        put("Enchanted Cocoa Beans", "ENCHANTED_COCOA");
        put("Enchanted Hay Bale", "ENCHANTED_HAY_BALE");
        put("Mutant Nether Wart", "MUTANT_NETHER_STALK");
        // ENCHANTED_HUGE_MUSHROOM_* are the blocks at ~253k each, not these.
        put("Enchanted Red Mushroom", "ENCHANTED_RED_MUSHROOM");
        put("Enchanted Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM");

        // Sack lines shorten some names.
        put("Ench Carrot", "ENCHANTED_CARROT");
        put("Ench Potato", "ENCHANTED_POTATO");
        put("Ench Cocoa Beans", "ENCHANTED_COCOA");
        put("Ench Sunflower", "ENCHANTED_SUNFLOWER");
        put("Ench Melon", "ENCHANTED_MELON");
        put("Ench Nether Wart", "ENCHANTED_NETHER_STALK");
        put("Ench Sugar", "ENCHANTED_SUGAR");
        put("Ench Seeds", "ENCHANTED_SEEDS");
        put("Ench Bread", "ENCHANTED_BREAD");
        put("Ench Pumpkin", "ENCHANTED_PUMPKIN");
        put("Ench Cactus Green", "ENCHANTED_CACTUS_GREEN");
        put("Ench Red Mushroom", "ENCHANTED_RED_MUSHROOM");
        put("Ench Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM");
    }

    private static void put(String name, String id) {
        EXCEPTIONS.put(name.toLowerCase(Locale.ROOT), id);
    }

    /**
     * Raw crop -> what it crafts into. Selling the raw drop is worth a fraction
     * of the crafted form, which is why a naive per-drop price reads far too low.
     * Skyblock uses 160 raw per enchanted for these.
     */
    private static final Map<String, String> CRAFTS_INTO = new HashMap<>();
    private static final int CRAFT_AMOUNT = 160;

    static {
        CRAFTS_INTO.put("melon slice", "ENCHANTED_MELON");
        CRAFTS_INTO.put("pumpkin", "ENCHANTED_PUMPKIN");
        CRAFTS_INTO.put("carrot", "ENCHANTED_CARROT");
        CRAFTS_INTO.put("potato", "ENCHANTED_POTATO");
        CRAFTS_INTO.put("cactus", "ENCHANTED_CACTUS_GREEN");
        CRAFTS_INTO.put("nether wart", "ENCHANTED_NETHER_STALK");
        CRAFTS_INTO.put("cocoa beans", "ENCHANTED_COCOA");
        CRAFTS_INTO.put("seeds", "ENCHANTED_SEEDS");
        CRAFTS_INTO.put("sugar cane", "ENCHANTED_SUGAR");
        CRAFTS_INTO.put("red mushroom", "ENCHANTED_RED_MUSHROOM");
        CRAFTS_INTO.put("brown mushroom", "ENCHANTED_BROWN_MUSHROOM");
    }

    /** Instant-sell price, what dumping the item actually pays. */
    private static final Map<String, Double> prices = new HashMap<>();
    /** Buy-order price, only used when an item has no instant-sell orders. */
    private static final Map<String, Double> buyPrices = new HashMap<>();

    private static long lastSynced = 0L;

    /** "Carrot Zest" -> "CARROT_ZEST". */
    private static String idFromName(String displayName) {
        return displayName.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", "")
                .replace(' ', '_');
    }

    /** "Enchanted Book (Sunset I)" — the only pest drop that gets no chat line. */
    private static final java.util.regex.Pattern BOOK =
            java.util.regex.Pattern.compile("^Enchanted Book \\((.+) ([IVX]+)\\)$");

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; case "X" -> 10;
            default -> 0;
        };
    }

    /**
     * Bazaar id of an enchanted book, or null when the name is not one.
     * <p>
     * Ultimate books drop the word "Ultimate" from the display name, so
     * "Sunset I" is ENCHANTMENT_ULTIMATE_SUNSET_1 and nothing in the name says so.
     */
    private static String bookId(String displayName) {
        java.util.regex.Matcher m = BOOK.matcher(displayName.trim());
        if (!m.matches()) return null;
        int level = roman(m.group(2));
        if (level == 0) return null;
        String base = m.group(1).toUpperCase(Locale.ROOT)
                .replace("'", "").replaceAll("[^A-Z0-9 ]", "").trim().replace(' ', '_');
        String plain = "ENCHANTMENT_" + base + "_" + level;
        if (prices.containsKey(plain) || buyPrices.containsKey(plain)) return plain;
        return "ENCHANTMENT_ULTIMATE_" + base + "_" + level;
    }

    private static String idFor(String displayName) {
        String key = displayName.toLowerCase(Locale.ROOT);
        String exception = EXCEPTIONS.get(key);
        if (exception != null) return exception;

        String book = bookId(displayName);
        if (book != null) return book;

        // Hypixel's own name -> id table beats deriving it: "Imagine Dragonflies
        // Vinyl" is VINYL_IMAGINE_DRAGONFLIES, which no rule would produce.
        String known = com.teslamaps.utils.SkyblockItems.idForName(key);
        return known != null ? known : idFromName(displayName);
    }

    /** How the player intends to turn the drop into coins. */
    public enum Mode { INSTANT_SELL, SELL_OFFER, NPC }

    public static Mode mode() {
        String set = com.teslamaps.config.TeslaMapsConfig.get().farmPriceMode;
        if ("Sell Offer".equals(set)) return Mode.SELL_OFFER;
        if ("NPC".equals(set)) return Mode.NPC;
        return Mode.INSTANT_SELL;
    }

    /**
     * What one unit pays in the chosen mode. A sell offer eventually fills at the
     * buy side, instant selling at the sell side. NPC prices fall back to the
     * bazaar for items no NPC buys.
     */
    private static double sellValue(String id, Mode mode) {
        return switch (mode) {
            case NPC -> {
                double npc = com.teslamaps.utils.SkyblockItems.npcPrice(id);
                yield npc > 0 ? npc : prices.getOrDefault(id, 0.0);
            }
            case SELL_OFFER -> buyPrices.getOrDefault(id, prices.getOrDefault(id, 0.0));
            case INSTANT_SELL -> prices.getOrDefault(id, 0.0);
        };
    }

    /**
     * Coins per unit of a name as it appears in a sack line: the raw drop, or its
     * share of the crafted form when that is worth more.
     */
    public static double priceOf(String displayName) {
        refreshIfStale();
        String key = displayName.toLowerCase(Locale.ROOT);

        // Pest kills pay coins directly, so the amount is already the value.
        if (key.contains("coins")) return 1;

        Mode mode = mode();
        String id = idFor(displayName);
        double raw = sellValue(id, mode);

        String craft = CRAFTS_INTO.get(key);
        if (craft != null) {
            double perUnit = sellValue(craft, mode) / CRAFT_AMOUNT;
            if (perUnit > raw) return perUnit;
        }

        // Vinyls and similar drops are not tradeable on the bazaar at all; they
        // get sold to an NPC, so that is their value.
        double npc = com.teslamaps.utils.SkyblockItems.npcPrice(id);
        if (npc > raw) return npc;

        // Raw crops mostly have no instant-sell orders. Falling back to the buy
        // side would price them at what buyers pay, which is not what the drop is
        // worth; only do that when there is no crafted route either.
        if (raw <= 0 && craft == null) {
            return buyPrices.getOrDefault(id, 0.0);
        }
        return raw;
    }

    /** False for names the bazaar does not carry, e.g. auction-only drops. */
    public static boolean known(String displayName) {
        String key = displayName.toLowerCase(Locale.ROOT);
        if (key.contains("coins")) return true;
        String id = idFor(displayName);
        return prices.containsKey(id) || buyPrices.containsKey(id)
                || com.teslamaps.utils.SkyblockItems.hasNpcPrice(id) || CRAFTS_INTO.containsKey(key);
    }

    public static boolean loaded() {
        return !prices.isEmpty();
    }

    /** True when the bazaar carries that item id at all. */
    public static boolean onBazaar(String id) {
        return id != null && (prices.containsKey(id) || buyPrices.containsKey(id));
    }

    /** Every product id the bazaar returned, names or not. */
    private static final java.util.Set<String> productIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static volatile boolean namesSynced = false;

    private static final String[] ROMAN =
            {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** "WIKI_TIKI" -> "Wiki Tiki". */
    private static String title(String id) {
        StringBuilder out = new StringBuilder(id.length());
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /**
     * Display name for a bazaar id the item resource does not carry. Shards,
     * enchanted books, essences and chocolate rabbits are roughly half the bazaar
     * and would otherwise never show up in the search.
     */
    private static String nameFor(String id) {
        if (id.startsWith("SHARD_")) {
            com.teslamaps.features.hunting.ShardData.Shard shard =
                    com.teslamaps.features.hunting.ShardData.bySkyblockId(id);
            if (shard != null && !shard.internalName().isEmpty()) {
                com.teslamaps.utils.SkyblockItems.registerNeuAlias(id, shard.internalName());
            }
            String base = shard != null && !shard.displayName().isEmpty()
                    ? shard.displayName() : title(id.substring(6));
            return base + " Shard";
        }
        if (id.startsWith("ESSENCE_")) return title(id.substring(8)) + " Essence";
        if (id.startsWith("FACTION_RABBIT_")) return title(id.substring(15));
        if (id.startsWith("ENCHANTMENT_")) {
            String rest = id.substring(12);
            int cut = rest.lastIndexOf('_');
            int level = 0;
            if (cut > 0) {
                try {
                    level = Integer.parseInt(rest.substring(cut + 1));
                    rest = rest.substring(0, cut);
                } catch (NumberFormatException ignored) {
                }
            }
            if (rest.startsWith("ULTIMATE_")) rest = rest.substring(9);
            String name = title(rest);
            return level > 0 && level < ROMAN.length ? name + " " + ROMAN[level] : name;
        }
        return title(id);
    }

    /** Feeds the bazaar-only ids into the item name table so the search can find them. */
    private static void syncNames() {
        if (namesSynced || productIds.isEmpty()) return;
        com.teslamaps.features.hunting.ShardData.load();
        if (!com.teslamaps.utils.SkyblockItems.loaded()
                || !com.teslamaps.features.hunting.ShardData.loaded()) return;

        int added = 0;
        for (String id : productIds) {
            if (com.teslamaps.utils.SkyblockItems.name(id) != null) continue;
            com.teslamaps.utils.SkyblockItems.registerName(id, nameFor(id));
            added++;
        }
        namesSynced = true;
        TeslaMaps.LOGGER.info("Named {} bazaar-only items", added);
    }

    /** Reads the bazaar out of PriceManager, which does the one fetch for everything. */
    public static void refreshIfStale() {
        com.teslamaps.utils.SkyblockItems.load();
        syncNames();

        com.teslamaps.features.croesus.PriceManager.ensureFresh();
        long updated = com.teslamaps.features.croesus.PriceManager.bazaarUpdatedAt();
        if (updated == 0 || updated == lastSynced) return;
        lastSynced = updated;

        Map<String, Double> parsed = new HashMap<>();
        Map<String, Double> parsedBuy = new HashMap<>();
        for (String id : com.teslamaps.features.croesus.PriceManager.bazaarProductIds()) {
            productIds.add(id);
            Double sell = com.teslamaps.features.croesus.PriceManager.bazaarSellOrNull(id);
            Double buy = com.teslamaps.features.croesus.PriceManager.bazaarBuyOrNull(id);
            if (sell != null && sell > 0) parsed.put(id, sell);
            if (buy != null && buy > 0) parsedBuy.put(id, buy);
        }
        synchronized (prices) {
            prices.clear();
            prices.putAll(parsed);
            buyPrices.clear();
            buyPrices.putAll(parsedBuy);
        }
    }
}
