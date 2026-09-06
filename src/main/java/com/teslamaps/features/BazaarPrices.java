package com.teslamaps.features;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Instant-sell prices from the public bazaar endpoint. No API key needed for
 * this one, unlike the rest of the Hypixel API.
 * <p>
 * Every product is kept and ids are derived from the display name, so new drops
 * price themselves without the table being extended by hand. Only names whose id
 * does not follow from the name are listed as exceptions.
 */
public class BazaarPrices {

    private static final String URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final long REFRESH_MS = 10 * 60 * 1000L;

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

    private static long lastFetch = 0L;
    private static boolean fetching = false;

    /** "Carrot Zest" -> "CARROT_ZEST". */
    private static String idFromName(String displayName) {
        return displayName.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", "")
                .replace(' ', '_');
    }

    private static String idFor(String displayName) {
        String key = displayName.toLowerCase(Locale.ROOT);
        String exception = EXCEPTIONS.get(key);
        if (exception != null) return exception;

        // Hypixel's own name -> id table beats deriving it: "Imagine Dragonflies
        // Vinyl" is VINYL_IMAGINE_DRAGONFLIES, which no rule would produce.
        String known = com.teslamaps.utils.SkyblockItems.idForName(key);
        return known != null ? known : idFromName(displayName);
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

        String id = idFor(displayName);
        double raw = prices.getOrDefault(id, 0.0);

        String craft = CRAFTS_INTO.get(key);
        if (craft != null) {
            double crafted = prices.getOrDefault(craft, buyPrices.getOrDefault(craft, 0.0));
            double perUnit = crafted / CRAFT_AMOUNT;
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

    public static void refreshIfStale() {
        com.teslamaps.utils.SkyblockItems.load();

        long now = System.currentTimeMillis();
        if (fetching || now - lastFetch < REFRESH_MS) return;
        lastFetch = now;
        fetching = true;

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                        .timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!root.has("products")) return;
                JsonObject products = root.getAsJsonObject("products");

                Map<String, Double> parsed = new HashMap<>();
                Map<String, Double> parsedBuy = new HashMap<>();
                for (String id : products.keySet()) {
                    JsonObject status = products.getAsJsonObject(id).getAsJsonObject("quick_status");
                    if (status == null) continue;

                    double sell = status.has("sellPrice") ? status.get("sellPrice").getAsDouble() : 0;
                    double buy = status.has("buyPrice") ? status.get("buyPrice").getAsDouble() : 0;
                    if (sell > 0) parsed.put(id, sell);
                    if (buy > 0) parsedBuy.put(id, buy);
                }

                synchronized (prices) {
                    prices.clear();
                    prices.putAll(parsed);
                    buyPrices.clear();
                    buyPrices.putAll(parsedBuy);
                }
                TeslaMaps.LOGGER.info("Loaded {} bazaar prices", parsed.size());
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Bazaar price fetch failed: {}", e.toString());
            } finally {
                fetching = false;
            }
        });
    }
}
