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
 * The bestiary categories the game itself shows, taken from the NEU repo so the grouping,
 * the icons and the tier brackets match.
 */
public class BestiaryRepo {
    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/bestiary.json";

    public record Mob(String name, String texture, String item, int cap, int bracket,
                      String bracketType, List<String> keys) {}

    /** An icon is either a skull texture or a plain vanilla item id, the repo mixes both. */
    public record Category(String id, String name, String icon, String iconItem, List<Mob> mobs,
                           List<Category> subs) {}

    private static final List<Category> categories = new ArrayList<>();
    private static final Map<Integer, List<Integer>> brackets = new HashMap<>();
    // critters and co level on their own shorter step tables (bracketSets.<TYPE>.<bracket>)
    private static final Map<String, Map<Integer, List<Integer>>> bracketSets = new HashMap<>();
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
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    loading = false;
                    return;
                }

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                parse(root);
                loaded = true;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to load the bestiary data: {}", e.toString());
            }
            loading = false;
        });
    }

    private static void parse(JsonObject root) {
        if (root.has("brackets")) {
            JsonObject bracketJson = root.getAsJsonObject("brackets");
            for (String key : bracketJson.keySet()) {
                List<Integer> steps = new ArrayList<>();
                for (JsonElement element : bracketJson.getAsJsonArray(key)) steps.add(element.getAsInt());
                try {
                    brackets.put(Integer.parseInt(key), steps);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (root.has("bracketSets") && root.get("bracketSets").isJsonObject()) {
            JsonObject sets = root.getAsJsonObject("bracketSets");
            for (String type : sets.keySet()) {
                if (!sets.get(type).isJsonObject()) continue;
                Map<Integer, List<Integer>> table = new HashMap<>();
                JsonObject set = sets.getAsJsonObject(type);
                for (String key : set.keySet()) {
                    List<Integer> steps = new ArrayList<>();
                    for (JsonElement element : set.getAsJsonArray(key)) steps.add(element.getAsInt());
                    try {
                        table.put(Integer.parseInt(key), steps);
                    } catch (NumberFormatException ignored) {
                    }
                }
                bracketSets.put(type, table);
            }
        }

        categories.clear();
        for (String key : root.keySet()) {
            if (key.equals("brackets") || key.equals("bracketSets")) continue;
            JsonElement element = root.get(key);
            if (!element.isJsonObject()) continue;
            JsonObject category = element.getAsJsonObject();
            if (!category.has("name")) continue;

            String name = category.get("name").getAsString();

            // fishing and safari hold their mobs one level deeper; those groups stay inside
            // their parent and are picked above the grid, not stacked into the rail
            if (!category.has("mobs")) {
                List<Category> subs = new ArrayList<>();
                for (String subKey : category.keySet()) {
                    if (!category.get(subKey).isJsonObject()) continue;
                    JsonObject sub = category.getAsJsonObject(subKey);
                    if (!sub.has("name")) continue;

                    List<Mob> subMobs = new ArrayList<>();
                    collectMobs(sub, subMobs);
                    if (subMobs.isEmpty()) continue;

                    subs.add(new Category(key + "." + subKey, sub.get("name").getAsString(),
                            texture(sub), item(sub), subMobs, List.of()));
                }
                if (subs.isEmpty()) continue;

                categories.add(new Category(key, name, texture(category), item(category),
                        subs.getFirst().mobs(), subs));
                continue;
            }

            List<Mob> mobs = new ArrayList<>();
            collectMobs(category, mobs);
            if (mobs.isEmpty()) continue;

            categories.add(new Category(key, name, texture(category), item(category), mobs, List.of()));
        }
    }

    private static void collectMobs(JsonObject category, List<Mob> out) {
        if (category.has("mobs") && category.get("mobs").isJsonArray()) {
            for (JsonElement element : category.getAsJsonArray("mobs")) {
                Mob mob = readMob(element);
                if (mob != null) out.add(mob);
            }
            return;
        }
        for (String key : category.keySet()) {
            if (!category.get(key).isJsonObject()) continue;
            collectMobs(category.getAsJsonObject(key), out);
        }
    }

    private static Mob readMob(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject mob = element.getAsJsonObject();
        if (!mob.has("name")) return null;

        List<String> keys = new ArrayList<>();
        if (mob.has("mobs") && mob.get("mobs").isJsonArray()) {
            JsonArray array = mob.getAsJsonArray("mobs");
            for (JsonElement key : array) keys.add(key.getAsString());
        }
        return new Mob(
                mob.get("name").getAsString(),
                mob.has("texture") ? mob.get("texture").getAsString() : null,
                mob.has("item") ? mob.get("item").getAsString() : null,
                mob.has("cap") ? mob.get("cap").getAsInt() : 0,
                mob.has("bracket") ? mob.get("bracket").getAsInt() : 1,
                mob.has("bracketType") ? mob.get("bracketType").getAsString() : null,
                keys);
    }

    private static String texture(JsonObject category) {
        JsonObject icon = iconObject(category);
        return icon != null && icon.has("texture") ? icon.get("texture").getAsString() : null;
    }

    /** Fishing and the spooky festival carry a plain item instead of a skull, that was dropped before. */
    private static String item(JsonObject category) {
        JsonObject icon = iconObject(category);
        return icon != null && icon.has("item") ? icon.get("item").getAsString() : null;
    }

    private static JsonObject iconObject(JsonObject category) {
        if (!category.has("icon") || !category.get("icon").isJsonObject()) return null;
        return category.getAsJsonObject("icon");
    }

    private static final Map<Mob, List<Integer>> cappedSteps = new HashMap<>();

    /**
     * Every bracket holds 25 steps, but a mob stops at its cap - the steps above it can never
     * be reached. Counting all 25 is what showed a maxed mob as 15/25.
     */
    private static List<Integer> steps(Mob mob) {
        List<Integer> all = mob.bracketType() != null
                ? bracketSets.getOrDefault(mob.bracketType(), Map.of()).get(mob.bracket())
                : brackets.get(mob.bracket());
        if (all == null) return List.of();
        if (mob.cap() <= 0) return all;

        return cappedSteps.computeIfAbsent(mob, key -> {
            List<Integer> capped = new ArrayList<>();
            for (int step : all) {
                if (step > key.cap()) break;
                capped.add(step);
            }
            return capped.isEmpty() ? all : capped;
        });
    }

    /** Tier for a kill count, using the bracket the repo assigns to that mob. */
    public static int tier(Mob mob, long kills) {
        int tier = 0;
        for (int step : steps(mob)) {
            if (kills >= step) tier++;
            else break;
        }
        return tier;
    }

    public static int maxTier(Mob mob) {
        return steps(mob).size();
    }
}
