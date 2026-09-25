package com.teslamaps.profileviewer.data;

import com.google.gson.JsonArray;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Pet levelling numbers from the NEU repo. The table is one shared list of level costs and
 * every rarity starts at its own offset - dragons carry their own list for 101 to 200.
 */
public class PetRepo {
    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/pets.json";

    private static final List<Long> levels = new ArrayList<>();
    private static final Map<String, Integer> offsets = new HashMap<>();
    private static final Map<String, List<Long>> customLevels = new HashMap<>();
    private static final Map<String, Integer> customMax = new HashMap<>();
    private static final java.util.Set<String> types = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean loading = false;
    private static boolean loaded = false;

    public static boolean isLoaded() {
        return loaded;
    }

    public static void load() {
        if (loaded || loading) return;
        loading = true;

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(URL)).timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    parse(JsonParser.parseString(response.body()).getAsJsonObject());
                    loaded = true;
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to load the pet data: {}", e.toString());
            }
            loading = false;
        });
    }

    private static void parse(JsonObject root) {
        levels.clear();
        if (root.has("pet_levels")) {
            for (JsonElement element : root.getAsJsonArray("pet_levels")) levels.add(element.getAsLong());
        }

        offsets.clear();
        if (root.has("pet_rarity_offset")) {
            JsonObject offsetJson = root.getAsJsonObject("pet_rarity_offset");
            for (String key : offsetJson.keySet()) offsets.put(key, offsetJson.get(key).getAsInt());
        }

        if (root.has("pet_types")) types.addAll(root.getAsJsonObject("pet_types").keySet());

        customLevels.clear();
        if (root.has("custom_pet_leveling")) {
            JsonObject custom = root.getAsJsonObject("custom_pet_leveling");
            for (String key : custom.keySet()) {
                JsonObject entry = custom.getAsJsonObject(key);
                if (!entry.has("pet_levels")) continue;
                List<Long> list = new ArrayList<>();
                JsonArray array = entry.getAsJsonArray("pet_levels");
                for (JsonElement element : array) list.add(element.getAsLong());
                if (list.isEmpty()) continue;
                customLevels.put(key, list);
                if (entry.has("max_level")) customMax.put(key, entry.get("max_level").getAsInt());
            }
        }
    }

    public static boolean isPetType(String type) {
        return types.contains(type);
    }

    public static int offset(String tier) {
        return offsets.getOrDefault(tier == null ? "COMMON" : tier.toUpperCase(), 0);
    }

    public static int maxLevel(String type) {
        Integer max = customMax.get(type);
        if (max != null) return max;
        return customLevels.containsKey(type) ? 200 : 100;
    }

    /** Xp needed to go from this level to the next one. */
    public static long costFor(String type, String tier, int level) {
        List<Long> custom = customLevels.get(type);
        // the custom list starts at the step from 100 to 101, so a dragon stalled at 100 before
        if (custom != null && level >= 100) {
            int index = level - 100;
            return index < custom.size() ? custom.get(index) : 0;
        }
        int index = offset(tier) + level - 1;
        if (index < 0 || index >= levels.size()) return 0;
        return levels.get(index);
    }
}
