package com.teslamaps.features.croesus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceManager {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "teslamaps-prices");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, Double> bazaar = new ConcurrentHashMap<>();    // internal id -> instant-buy price
    private static final Map<String, Double> bazaarSell = new ConcurrentHashMap<>(); // internal id -> instant-sell price
    private static final Map<String, Double> buyOrder = new ConcurrentHashMap<>();   // best buy order, top of the book
    private static final Map<String, Double> sellOffer = new ConcurrentHashMap<>();  // best sell offer, top of the book
    private static final java.util.Set<String> productIds = ConcurrentHashMap.newKeySet();
    private static final Map<String, Double> lowestBin = new ConcurrentHashMap<>(); // internal id -> lowest BIN
    private static final Map<String, String> nameToId = new ConcurrentHashMap<>();  // lowercase clean name -> internal id

    private static volatile boolean loaded = false;
    private static volatile long lastFetch = 0L;
    private static volatile long bazaarUpdated = 0L;

    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence$");
    private static final Pattern SHARD = Pattern.compile("^(.+) Shard$");

    private static volatile int version = 0;

    public static boolean isLoaded() { return loaded; }

    public static int version() { return version; }

    public static void ensureFresh() {
        com.teslamaps.utils.SkyblockItems.load();
        long now = System.currentTimeMillis();
        if (loaded && now - lastFetch < 600_000) return;
        if (now - lastFetch < 30_000) return;
        lastFetch = now;
        EXEC.submit(PriceManager::refresh);
    }

    /** Skips the 10-minute wait and fetches everything again. */
    public static void forceRefresh() {
        lastFetch = System.currentTimeMillis();
        EXEC.submit(PriceManager::refresh);
    }

    /** Everything, averages included, then {@code done} on the main thread. */
    public static void forceRefreshAll(Runnable done) {
        lastFetch = System.currentTimeMillis();
        EXEC.submit(() -> {
            refresh();
            net.minecraft.client.Minecraft.getInstance().execute(done);
        });
    }

    private static void refresh() {
        try { fetchItems(); } catch (Exception e) { TeslaMaps.LOGGER.warn("[Croesus] items fetch failed: {}", e.toString()); }
        try { fetchBazaar(); } catch (Exception e) { TeslaMaps.LOGGER.warn("[Croesus] bazaar fetch failed: {}", e.toString()); }
        try { fetchLowestBin(); } catch (Exception e) { TeslaMaps.LOGGER.warn("[Croesus] lowest-BIN fetch failed: {}", e.toString()); }
        loaded = !nameToId.isEmpty() && (!bazaar.isEmpty() || !lowestBin.isEmpty());
        version++;
        TeslaMaps.LOGGER.info("[Croesus] prices: {} items, {} bazaar, {} BIN", nameToId.size(), bazaar.size(), lowestBin.size());
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(25))
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
        return resp.body();
    }

    private static void fetchItems() throws Exception {
        JsonObject json = JsonParser.parseString(get("https://api.hypixel.net/v2/resources/skyblock/items")).getAsJsonObject();
        for (var el : json.getAsJsonArray("items")) {
            JsonObject it = el.getAsJsonObject();
            if (!it.has("id") || !it.has("name")) continue;
            String name = strip(it.get("name").getAsString()).toLowerCase();
            nameToId.putIfAbsent(name, it.get("id").getAsString());
        }
    }

    private static void fetchBazaar() throws Exception {
        JsonObject json = JsonParser.parseString(get("https://api.hypixel.net/v2/skyblock/bazaar")).getAsJsonObject();
        JsonObject products = json.getAsJsonObject("products");
        for (var e : products.entrySet()) {
            productIds.add(e.getKey());
            JsonObject product = e.getValue().getAsJsonObject();
            JsonObject qs = product.getAsJsonObject("quick_status");
            if (qs == null) continue;
            if (qs.has("buyPrice")) bazaar.put(e.getKey(), qs.get("buyPrice").getAsDouble());
            if (qs.has("sellPrice")) bazaarSell.put(e.getKey(), qs.get("sellPrice").getAsDouble());
            if (qs.has("buyMovingWeek")) buyWeek.put(e.getKey(), qs.get("buyMovingWeek").getAsLong());
            if (qs.has("sellMovingWeek")) sellWeek.put(e.getKey(), qs.get("sellMovingWeek").getAsLong());
            if (depthWatch.contains(e.getKey())) {
                depthBuyOrders.put(e.getKey(), rows(product.getAsJsonArray("sell_summary")));
                depthSellOffers.put(e.getKey(), rows(product.getAsJsonArray("buy_summary")));
            }
            // sell_summary = buy orders, buy_summary = sell offers
            putTop(buyOrder, e.getKey(), product.getAsJsonArray("sell_summary"));
            putTop(sellOffer, e.getKey(), product.getAsJsonArray("buy_summary"));
        }
        bazaarUpdated = json.has("lastUpdated") ? json.get("lastUpdated").getAsLong() : System.currentTimeMillis();
    }

    private static double[][] rows(com.google.gson.JsonArray arr) {
        if (arr == null) return new double[0][];
        double[][] out = new double[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonObject r = arr.get(i).getAsJsonObject();
            out[i] = new double[]{
                    r.has("pricePerUnit") ? r.get("pricePerUnit").getAsDouble() : 0,
                    r.has("amount") ? r.get("amount").getAsDouble() : 0,
                    r.has("orders") ? r.get("orders").getAsDouble() : 0};
        }
        return out;
    }

    private static final Map<String, Long> buyWeek = new ConcurrentHashMap<>();
    private static final Map<String, Long> sellWeek = new ConcurrentHashMap<>();
    private static final java.util.Set<String> depthWatch = ConcurrentHashMap.newKeySet();
    private static final Map<String, double[][]> depthBuyOrders = new ConcurrentHashMap<>();
    private static final Map<String, double[][]> depthSellOffers = new ConcurrentHashMap<>();
    private static volatile long depthFetch = 0L;
    private static volatile int depthVersion = 0;

    /** Insta-buys over the last 7 days, null when unknown. */
    public static Long bazaarWeekBuysOrNull(String id) { return id == null ? null : buyWeek.get(id); }

    /** Insta-sells over the last 7 days, null when unknown. */
    public static Long bazaarWeekSellsOrNull(String id) { return id == null ? null : sellWeek.get(id); }

    /** Products whose order book rows are kept; the rest only keep the top price. */
    public static void watchDepth(java.util.Set<String> ids) {
        depthWatch.retainAll(ids);
        depthWatch.addAll(ids);
        depthBuyOrders.keySet().retainAll(ids);
        depthSellOffers.keySet().retainAll(ids);
    }

    /** Bazaar only, at most every {@code intervalMs}; used while order books are watched. */
    public static void refreshDepth(long intervalMs) {
        long now = System.currentTimeMillis();
        if (depthWatch.isEmpty() || now - depthFetch < intervalMs) return;
        depthFetch = now;
        EXEC.submit(() -> {
            try {
                fetchBazaar();
                depthVersion++;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Croesus] bazaar depth fetch failed: {}", e.toString());
            }
        });
    }

    public static int depthVersion() { return depthVersion; }

    /** Rows {price, amount, orders}, best first. Buy orders for a buy order, sell offers otherwise. */
    public static double[][] depthOrNull(String id, boolean buyOrders) {
        return id == null ? null : (buyOrders ? depthBuyOrders : depthSellOffers).get(id);
    }

    private static void putTop(Map<String, Double> into, String id, com.google.gson.JsonArray rows) {
        if (rows == null || rows.isEmpty()) {
            into.remove(id);
            return;
        }
        JsonObject top = rows.get(0).getAsJsonObject();
        if (top.has("pricePerUnit")) into.put(id, top.get("pricePerUnit").getAsDouble());
    }

    private static final String[] BIN_URLS = {
        "https://lb.tricked.pro/lowestbins",
        "https://moulberry.codes/lowestbin.json",
    };

    private static void fetchLowestBin() throws Exception {
        Exception last = null;
        for (String url : BIN_URLS) {
            try {
                JsonObject json = JsonParser.parseString(get(url)).getAsJsonObject();
                int before = lowestBin.size();
                for (var e : json.entrySet()) {
                    try { lowestBin.put(e.getKey(), e.getValue().getAsDouble()); } catch (Exception ignored) {}
                }
                if (lowestBin.size() > before) return; // got data
            } catch (Exception ex) { last = ex; }
        }
        if (last != null) throw last;
    }

    public static double getPrice(String id) {
        if (id == null) return 0;
        Double b = bazaar.get(id);
        if (b != null) return b;
        Double bin = lowestBin.get(id);
        if (bin != null) return bin;
        if (!craftFallback()) return 0;
        Double craft = CraftCost.costOrNull(id, SOURCE_BAZAAR_BUY);
        return craft != null ? craft : 0;
    }

    private static boolean craftFallback() {
        return com.teslamaps.config.TeslaMapsConfig.get().priceCraftFallback;
    }

    public static boolean hasBazaar(String id) { return id != null && bazaar.containsKey(id); }

    /** BZ instant buy. */
    public static final int SOURCE_BAZAAR_BUY = 0;
    /** BZ instant sell (SkyHanni's default). */
    public static final int SOURCE_BAZAAR_SELL = 1;
    /** NPC sell only. */
    public static final int SOURCE_NPC = 2;
    /** Lowest BIN first, bazaar only as the fallback. */
    public static final int SOURCE_LOWEST_BIN = 3;

    /**
     * What one of the given item goes for, or null when nothing knows. Mirrors
     * SkyHanni's price source order: bazaar, then lowest BIN, then the NPC price,
     * then (opt-in) the raw craft cost from the NEU recipe.
     * SOURCE_LOWEST_BIN flips the first two, for the items that trade on the AH.
     */
    public static Double getPriceOrNull(String id, int source) {
        Double market = marketPriceOrNull(id, source);
        if (market != null || source == SOURCE_NPC || id == null || id.isEmpty() || !craftFallback()) return market;
        return CraftCost.costOrNull(id, source);
    }

    /** {@link #getPriceOrNull} without the craft-cost fallback. */
    public static Double marketPriceOrNull(String id, int source) {
        if (id == null || id.isEmpty()) return null;
        if (id.equals("SKYBLOCK_COIN")) return 1.0;

        if (source == SOURCE_LOWEST_BIN) {
            Double bin = lowestBin.get(id);
            if (bin != null) return bin;
        }

        if (source != SOURCE_NPC) {
            Double bz = source == SOURCE_BAZAAR_SELL ? bazaarSell.get(id) : bazaar.get(id);
            if (bz == null) bz = bazaar.get(id);
            if (bz != null) return bz;

            Double bin = lowestBin.get(id);
            if (bin != null) return bin;
        }

        return com.teslamaps.utils.SkyblockItems.hasNpcPrice(id)
                ? com.teslamaps.utils.SkyblockItems.npcPrice(id) : null;
    }

    /** Lowest BIN on its own, for showing it next to whatever the active source says. */
    public static Double lowestBinOrNull(String id) {
        return id == null ? null : lowestBin.get(id);
    }

    /** BZ instant buy, what it costs you to buy one right now. */
    public static Double bazaarBuyOrNull(String id) {
        return id == null ? null : bazaar.get(id);
    }

    /** BZ instant sell, what you get for selling one right now. */
    public static Double bazaarSellOrNull(String id) {
        return id == null ? null : bazaarSell.get(id);
    }

    /** Best buy order right now, the price a new buy order has to beat. */
    public static Double bazaarBuyOrderOrNull(String id) {
        return id == null ? null : buyOrder.get(id);
    }

    /** Best sell offer right now, the price a new sell offer has to undercut. */
    public static Double bazaarSellOfferOrNull(String id) {
        return id == null ? null : sellOffer.get(id);
    }

    /** Every product id the bazaar returned. */
    public static java.util.Set<String> bazaarProductIds() { return productIds; }

    /** Hypixel's lastUpdated of the bazaar data in hand, 0 before the first fetch. */
    public static long bazaarUpdatedAt() { return bazaarUpdated; }

    public static int lowestBinCount() { return lowestBin.size(); }

    public static int bazaarCount() { return bazaar.size(); }

    public static String idForName(String displayName) {
        if (displayName == null) return null;
        String clean = strip(displayName);
        Matcher m = ESSENCE.matcher(clean);
        if (m.matches()) return "ESSENCE_" + m.group(1).toUpperCase();
        Matcher sh = SHARD.matcher(clean);
        if (sh.matches()) return "SHARD_" + sh.group(1).toUpperCase().replace(" ", "_");
        return nameToId.get(clean.toLowerCase());
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.stripTrim(s);
    }
}
