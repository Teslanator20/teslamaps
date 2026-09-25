package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** NEU's museum constants: every donatable id per category. Bundled snapshot, live repo file replaces it. */
public class MuseumRepo {
    private static final String NEU_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/museum.json";

    public record Data(Map<String, List<String>> items, Map<String, String> armorToId,
                       Map<String, String> children, Map<String, String> mappedIds,
                       Map<String, List<String>> setsToItems, List<String> special, Map<String, Integer> itemToXp) {}

    private static volatile Data data;
    private static volatile boolean loading = false;
    private static volatile boolean live = false;
    private static boolean bundledTried = false;

    public static Data get() {
        load();
        return data;
    }

    public static synchronized void load() {
        if (data == null && !bundledTried) {
            bundledTried = true;
            try (InputStream stream = MuseumRepo.class.getResourceAsStream("/assets/teslamaps/pv/museum.json")) {
                if (stream != null) {
                    data = parse(JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to read the bundled museum data: {}", e.toString());
            }
        }
        if (live || loading) return;
        loading = true;

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(NEU_URL))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Data parsed = parse(JsonParser.parseString(response.body()).getAsJsonObject());
                    if (!parsed.items().isEmpty()) data = parsed;
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to load the NEU museum data: {}", e.toString());
            }
            live = true;
            loading = false;
        });
    }

    private static Data parse(JsonObject root) {
        Map<String, List<String>> items = new LinkedHashMap<>();
        List<String> special = List.of();
        if (root.has("items") && root.get("items").isJsonObject()) {
            JsonObject json = root.getAsJsonObject("items");
            for (String category : json.keySet()) {
                if (!json.get(category).isJsonArray()) continue;
                List<String> ids = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray(category)) {
                    if (element.isJsonPrimitive()) ids.add(element.getAsString());
                }
                if (category.equals("special")) special = ids;
                else items.put(category, ids);
            }
        }
        Map<String, List<String>> sets = new HashMap<>();
        if (root.has("sets_to_items") && root.get("sets_to_items").isJsonObject()) {
            JsonObject json = root.getAsJsonObject("sets_to_items");
            for (String k : json.keySet()) {
                if (!json.get(k).isJsonArray()) continue;
                List<String> ids = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray(k)) {
                    if (element.isJsonPrimitive()) ids.add(element.getAsString());
                }
                sets.put(k, ids);
            }
        }
        Map<String, Integer> xp = new HashMap<>();
        if (root.has("itemToXp") && root.get("itemToXp").isJsonObject()) {
            JsonObject json = root.getAsJsonObject("itemToXp");
            for (String k : json.keySet()) {
                try { xp.put(k, json.get(k).getAsInt()); } catch (Exception ignored) {}
            }
        }
        return new Data(items, strings(root, "armor_to_id"), strings(root, "children"), strings(root, "mapped_ids"),
                sets, special, xp);
    }

    private static Map<String, String> strings(JsonObject root, String key) {
        Map<String, String> out = new HashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) return out;
        JsonObject json = root.getAsJsonObject(key);
        for (String k : json.keySet()) {
            if (json.get(k).isJsonPrimitive()) out.put(k, json.get(k).getAsString());
        }
        return out;
    }

    /** Donated ids plus everything they stand in for: starred variants and lower tiers. */
    public static Set<String> covered(Data data, Set<String> donated) {
        Set<String> out = new HashSet<>();
        for (String id : donated) {
            out.add(id);
            String current = data.mappedIds().getOrDefault(id, id);
            out.add(current);
            for (int i = 0; i < 32; i++) {
                current = data.children().get(current);
                if (current == null || !out.add(current)) break;
            }
        }
        return out;
    }
}
