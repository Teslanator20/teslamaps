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

    public record Mob(String name, String texture, int cap, int bracket, List<String> keys) {}

    public record Category(String id, String name, String icon, List<Mob> mobs) {}

    private static final List<Category> categories = new ArrayList<>();
    private static final Map<Integer, List<Integer>> brackets = new HashMap<>();
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

        categories.clear();
        for (String key : root.keySet()) {
            if (key.equals("brackets") || key.equals("bracketSets") || key.equals("dynamic")) continue;
            JsonElement element = root.get(key);
            if (!element.isJsonObject()) continue;
            JsonObject category = element.getAsJsonObject();
            if (!category.has("name")) continue;

            List<Mob> mobs = new ArrayList<>();
            collectMobs(category, mobs);
            if (mobs.isEmpty()) continue;

            categories.add(new Category(key, category.get("name").getAsString(), icon(category), mobs));
        }
    }

    /** Fishing and safari nest their mobs one level deeper. */
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
                mob.has("cap") ? mob.get("cap").getAsInt() : 0,
                mob.has("bracket") ? mob.get("bracket").getAsInt() : 1,
                keys);
    }

    private static String icon(JsonObject category) {
        if (!category.has("icon")) return null;
        JsonElement icon = category.get("icon");
        if (icon.isJsonObject() && icon.getAsJsonObject().has("texture")) {
            return icon.getAsJsonObject().get("texture").getAsString();
        }
        return null;
    }

    /** Tier for a kill count, using the bracket the repo assigns to that mob. */
    public static int tier(Mob mob, long kills) {
        List<Integer> steps = brackets.get(mob.bracket());
        if (steps == null) return 0;
        int tier = 0;
        for (int step : steps) {
            if (kills >= step) tier++;
            else break;
        }
        return tier;
    }

    public static int maxTier(Mob mob) {
        List<Integer> steps = brackets.get(mob.bracket());
        return steps == null ? 0 : steps.size();
    }
}
