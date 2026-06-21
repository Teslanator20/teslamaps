/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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
    private static final Map<String, Double> lowestBin = new ConcurrentHashMap<>(); // internal id -> lowest BIN
    private static final Map<String, String> nameToId = new ConcurrentHashMap<>();  // lowercase clean name -> internal id

    private static volatile boolean loaded = false;
    private static volatile long lastFetch = 0L;

    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence$");
    private static final Pattern SHARD = Pattern.compile("^(.+) Shard$");

    public static boolean isLoaded() { return loaded; }

    public static void ensureFresh() {
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
            if (qs != null && qs.has("buyPrice")) bazaar.put(e.getKey(), qs.get("buyPrice").getAsDouble());
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
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
