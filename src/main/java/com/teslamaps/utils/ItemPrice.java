package com.teslamaps.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * What a single item goes for, from Coflnet. Covers auction house and bazaar in
 * one call, which the bazaar endpoint on its own cannot do — the drops worth
 * announcing are mostly auction-only.
 */
public class ItemPrice {

    private static final String URL = "https://sky.coflnet.com/api/item/price/%s/current";
    private static final long STALE_MS = 10 * 60 * 1000L;

    private record Entry(double price, long fetched) {}

    private static final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private static final java.util.Set<String> pending = ConcurrentHashMap.newKeySet();

    /** Calls back on the main thread with the price, once, and only if one is known. */
    public static void resolve(String id, Consumer<Double> callback) {
        if (id == null || id.isEmpty()) return;

        Entry cached = cache.get(id);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetched() < STALE_MS) {
            if (cached.price() > 0) callback.accept(cached.price());
            return;
        }

        if (!pending.add(id)) return;
        CompletableFuture.runAsync(() -> {
            double price = 0;
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(URL, id)))
                        .timeout(Duration.ofSeconds(15)).GET().build();
                String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                if (root.has("buy")) price = root.get("buy").getAsDouble();
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Price lookup for {} failed: {}", id, e.toString());
            } finally {
                pending.remove(id);
            }

            double finalPrice = price;
            cache.put(id, new Entry(finalPrice, System.currentTimeMillis()));
            if (finalPrice > 0) Minecraft.getInstance().execute(() -> callback.accept(finalPrice));
        });
    }

    /** 655000000 -> "655M". */
    public static String format(double coins) {
        if (coins >= 1_000_000_000L) return trim(coins / 1_000_000_000.0) + "B";
        if (coins >= 1_000_000L) return trim(coins / 1_000_000.0) + "M";
        if (coins >= 1_000L) return trim(coins / 1_000.0) + "k";
        return String.valueOf(Math.round(coins));
    }

    private static String trim(double value) {
        if (value >= 100) return String.valueOf(Math.round(value));
        return String.format(java.util.Locale.ROOT, "%.1f", value).replace(".0", "");
    }
}
