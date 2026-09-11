package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Collections straight from hypixel's own resource endpoint - it needs no key and carries
 * the display names and tier thresholds, so no hardcoded "LOG:1" lists.
 */
public class CollectionRepo {
    private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/collections";

    public record Entry(String id, String name, int maxTiers, List<Long> tiers) {}

    public record Category(String id, String name, List<Entry> items) {}

    private static final List<Category> categories = new ArrayList<>();
    private static boolean loading = false;
    private static boolean loaded = false;

    public static boolean isLoaded() {
        return loaded;
    }

    public static List<Category> categories() {
        return categories;
    }

    public static void load() {
        if (loaded || loading) return;
        loading = true;

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(URL))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "TeslaMaps/1.0")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    parse(JsonParser.parseString(response.body()).getAsJsonObject());
                    loaded = true;
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to load the collection data: {}", e.toString());
            }
            loading = false;
        });
    }

    private static void parse(JsonObject root) {
        if (!root.has("collections")) return;
        categories.clear();

        JsonObject collections = root.getAsJsonObject("collections");
        for (String categoryId : collections.keySet()) {
            JsonObject category = collections.getAsJsonObject(categoryId);
            if (!category.has("items")) continue;

            List<Entry> entries = new ArrayList<>();
            JsonObject items = category.getAsJsonObject("items");
            for (String itemId : items.keySet()) {
                JsonObject item = items.getAsJsonObject(itemId);
                List<Long> tiers = new ArrayList<>();
                if (item.has("tiers")) {
                    for (JsonElement element : item.getAsJsonArray("tiers")) {
                        JsonObject tier = element.getAsJsonObject();
                        tiers.add(tier.has("amountRequired") ? tier.get("amountRequired").getAsLong() : 0);
                    }
                }
                entries.add(new Entry(
                        itemId,
                        item.has("name") ? item.get("name").getAsString() : itemId,
                        item.has("maxTiers") ? item.get("maxTiers").getAsInt() : tiers.size(),
                        tiers));
            }

            entries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            categories.add(new Category(
                    categoryId,
                    category.has("name") ? category.get("name").getAsString() : categoryId,
                    entries));
        }

        categories.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
    }

    public static int tier(Entry entry, long amount) {
        int tier = 0;
        for (long required : entry.tiers()) {
            if (amount >= required) tier++;
            else break;
        }
        return tier;
    }
}
