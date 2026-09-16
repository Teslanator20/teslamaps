package com.teslamaps.profileviewer.data;

import com.google.gson.JsonArray;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Bestiary data. Base is the curated file from schrumbo's pv (bundled - NEU's bestiary.json
 * carries stale caps/brackets for most mobs), the NEU repo only fills in islands the bundle
 * does not know yet (safari, new foraging isles).
 */
public class BestiaryRepo {
    private static final String NEU_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/bestiary.json";

    /** Steps are resolved at parse time, so tier math never cares where a mob came from. */
    public record Mob(String name, String texture, String item, long cap, List<Integer> steps,
                      List<String> keys) {}

    public record Category(String id, String name, String icon, String iconItem, List<Mob> mobs,
                           List<Category> subs) {}

    private static final List<Category> categories = new ArrayList<>();
    private static boolean loading = false;
    private static boolean loaded = false;

    public static boolean isLoaded() {
        return loaded;
    }

    public static List<Category> categories() {
        return categories;
    }

    public static synchronized void load() {
        if (loaded || loading) return;
        loading = true;

        try (InputStream stream = BestiaryRepo.class
                .getResourceAsStream("/assets/teslamaps/pv/bestiary.json")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                parseBundled(root);
                loaded = true;
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read the bundled bestiary data: {}", e.toString());
        }

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
                    appendNeuOnly(JsonParser.parseString(response.body()).getAsJsonObject());
                    loaded = true;
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to load the NEU bestiary data: {}", e.toString());
            }
            loading = false;
        });
    }

    // ---- bundled (schrumbo) format: {brackets: {id: [steps]}, islands: [{key,name,icon,mobs}]} --

    private static void parseBundled(JsonObject root) {
        Map<String, List<Integer>> brackets = readBrackets(root.getAsJsonObject("brackets"));

        categories.clear();
        for (JsonElement element : root.getAsJsonArray("islands")) {
            JsonObject island = element.getAsJsonObject();
            List<Mob> mobs = new ArrayList<>();
            for (JsonElement mobElement : island.getAsJsonArray("mobs")) {
                JsonObject mob = mobElement.getAsJsonObject();
                long cap = mob.get("cap").getAsLong();
                List<Integer> steps = capped(brackets.get(mob.get("bracket").getAsString()), cap);
                mobs.add(new Mob(
                        mob.get("name").getAsString(),
                        string(mob, "tex"),
                        string(mob, "item"),
                        cap, steps, strings(mob.getAsJsonArray("keys"))));
            }
            JsonObject icon = island.has("icon") && island.get("icon").isJsonObject()
                    ? island.getAsJsonObject("icon") : null;
            categories.add(new Category(island.get("key").getAsString(), island.get("name").getAsString(),
                    string(icon, "tex"), string(icon, "item"), mobs, List.of()));
        }
    }

    // ---- NEU format, only for islands the bundle lacks --------------------------------------

    private static synchronized void appendNeuOnly(JsonObject root) {
        Map<String, List<Integer>> brackets = readBrackets(root.getAsJsonObject("brackets"));

        Map<String, Map<String, List<Integer>>> bracketSets = new HashMap<>();
        if (root.has("bracketSets") && root.get("bracketSets").isJsonObject()) {
            JsonObject sets = root.getAsJsonObject("bracketSets");
            for (String type : sets.keySet()) {
                if (sets.get(type).isJsonObject()) {
                    bracketSets.put(type, readBrackets(sets.getAsJsonObject(type)));
                }
            }
        }

        java.util.Set<String> known = new java.util.HashSet<>();
        for (Category category : categories) known.add(category.id());

        for (String key : root.keySet()) {
            if (key.equals("brackets") || key.equals("bracketSets") || known.contains(key)) continue;
            // the bundle splits NEU's nested fishing island, its parts are known under other ids
            if (key.equals("fishing")) continue;
            JsonElement element = root.get(key);
            if (!element.isJsonObject()) continue;
            JsonObject category = element.getAsJsonObject();
            if (!category.has("name")) continue;

            List<Mob> mobs = new ArrayList<>();
            collectNeuMobs(category, mobs, brackets, bracketSets);
            if (mobs.isEmpty()) continue;

            categories.add(new Category(key, category.get("name").getAsString(),
                    neuTexture(category), neuItem(category), mobs, List.of()));
        }
    }

    private static void collectNeuMobs(JsonObject category, List<Mob> out,
                                       Map<String, List<Integer>> brackets,
                                       Map<String, Map<String, List<Integer>>> bracketSets) {
        if (category.has("mobs") && category.get("mobs").isJsonArray()) {
            for (JsonElement element : category.getAsJsonArray("mobs")) {
                if (!element.isJsonObject()) continue;
                JsonObject mob = element.getAsJsonObject();
                if (!mob.has("name")) continue;

                long cap = mob.has("cap") ? mob.get("cap").getAsLong() : 0;
                String bracket = mob.has("bracket") ? mob.get("bracket").getAsString() : "1";
                Map<String, List<Integer>> table = mob.has("bracketType")
                        ? bracketSets.getOrDefault(mob.get("bracketType").getAsString(), Map.of())
                        : brackets;
                List<Integer> steps = capped(table.get(bracket), cap);

                List<String> keys = mob.has("mobs") && mob.get("mobs").isJsonArray()
                        ? strings(mob.getAsJsonArray("mobs")) : List.of();
                out.add(new Mob(mob.get("name").getAsString(), string(mob, "texture"),
                        string(mob, "item"), cap, steps, keys));
            }
            return;
        }
        for (String key : category.keySet()) {
            if (category.get(key).isJsonObject()) {
                collectNeuMobs(category.getAsJsonObject(key), out, brackets, bracketSets);
            }
        }
    }

    private static String neuTexture(JsonObject category) {
        JsonObject icon = category.has("icon") && category.get("icon").isJsonObject()
                ? category.getAsJsonObject("icon") : null;
        return string(icon, "texture");
    }

    private static String neuItem(JsonObject category) {
        JsonObject icon = category.has("icon") && category.get("icon").isJsonObject()
                ? category.getAsJsonObject("icon") : null;
        return string(icon, "item");
    }

    // ---- shared ------------------------------------------------------------------------------

    private static Map<String, List<Integer>> readBrackets(JsonObject json) {
        Map<String, List<Integer>> out = new HashMap<>();
        if (json == null) return out;
        for (String key : json.keySet()) {
            if (!json.get(key).isJsonArray()) continue;
            List<Integer> steps = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray(key)) steps.add(element.getAsInt());
            out.put(key, steps);
        }
        return out;
    }

    /** A mob stops at its kill cap, the steps above it can never be reached. */
    private static List<Integer> capped(List<Integer> all, long cap) {
        if (all == null) return List.of();
        if (cap <= 0) return all;
        List<Integer> out = new ArrayList<>();
        for (int step : all) {
            if (step > cap) break;
            out.add(step);
        }
        return out.isEmpty() ? all : out;
    }

    private static String string(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : null;
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>(array.size());
        for (JsonElement element : array) out.add(element.getAsString());
        return out;
    }

    public static int tier(Mob mob, long kills) {
        int tier = 0;
        for (int step : mob.steps()) {
            if (kills >= step) tier++;
            else break;
        }
        return tier;
    }

    public static int maxTier(Mob mob) {
        return mob.steps().size();
    }
}
