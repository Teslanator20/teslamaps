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
    private static final Map<String, Double> lowestBin = new ConcurrentHashMap<>(); // internal id -> lowest BIN
    private static final Map<String, String> nameToId = new ConcurrentHashMap<>();  // lowercase clean name -> internal id

    private static volatile boolean loaded = false;
    private static volatile long lastFetch = 0L;

    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence$");
    private static final Pattern SHARD = Pattern.compile("^(.+) Shard$");

    public static boolean isLoaded() { return loaded; }

    public static void ensureFresh() {
        com.teslamaps.utils.SkyblockItems.load();
        long now = System.currentTimeMillis();
        if (loaded && now - lastFetch < 600_000) return;
        if (now - lastFetch < 30_000) return;
        lastFetch = now;
        EXEC.submit(PriceManager::refresh);
    }

    private static void refresh() {
        try { fetchItems(); } catch (Exception e) { TeslaMaps.LOGGER.warn("[Croesus] items fetch failed: {}", e.toString()); }
        try { fetchBazaar(); } catch (Exception e) { TeslaMaps.LOGGER.warn("[Croesus] bazaar fetch failed: {}", e.toString()); }
        try { fetchLowestBin(); } catch (Exception e) { TeslaMaps.LOGGER.warn("[Croesus] lowest-BIN fetch failed: {}", e.toString()); }
        loaded = !nameToId.isEmpty() && (!bazaar.isEmpty() || !lowestBin.isEmpty());
        TeslaMaps.LOGGER.info("[Croesus] prices: {} items, {} bazaar, {} BIN", nameToId.size(), bazaar.size(), lowestBin.size());
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", "TeslaMaps").GET().build();
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
            JsonObject qs = e.getValue().getAsJsonObject().getAsJsonObject("quick_status");
            if (qs == null) continue;
            if (qs.has("buyPrice")) bazaar.put(e.getKey(), qs.get("buyPrice").getAsDouble());
            if (qs.has("sellPrice")) bazaarSell.put(e.getKey(), qs.get("sellPrice").getAsDouble());
        }
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
        return bin != null ? bin : 0;
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
     * SkyHanni's price source order: bazaar, then lowest BIN, then the NPC price.
     * SOURCE_LOWEST_BIN flips the first two, for the items that trade on the AH.
     */
    public static Double getPriceOrNull(String id, int source) {
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
