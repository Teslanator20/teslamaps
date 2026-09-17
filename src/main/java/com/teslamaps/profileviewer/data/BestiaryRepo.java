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
 * Bestiary data in NEU's format - NEU gets cap/bracket fixes within days, so it is the source
 * of truth. A snapshot ships as a resource so the page works offline and instantly; the live
 * repo file replaces it once fetched.
 */
public class BestiaryRepo {
    private static final String NEU_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/bestiary.json";

    /** Steps are resolved at parse time, so tier math never cares about brackets again. */
    public record Mob(String name, String texture, String item, long cap, List<Integer> steps,
                      List<String> keys) {}

    public record Category(String id, String name, String icon, String iconItem, List<Mob> mobs,
                           List<Category> subs) {}

    private static volatile List<Category> categories = List.of();
    private static boolean loading = false;
    private static boolean loaded = false;
    private static boolean live = false;

    public static boolean isLoaded() {
        return loaded;
    }

    public static List<Category> categories() {
        return categories;
    }

    public static synchronized void load() {
        if (live || loading) return;
        loading = true;

        if (!loaded) {
            try (InputStream stream = BestiaryRepo.class
                    .getResourceAsStream("/assets/teslamaps/pv/bestiary.json")) {
                if (stream != null) {
                    categories = parse(JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                    loaded = true;
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to read the bundled bestiary data: {}", e.toString());
            }
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
                    categories = parse(JsonParser.parseString(response.body()).getAsJsonObject());
                    loaded = true;
                    live = true;
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to load the NEU bestiary data: {}", e.toString());
            }
            loading = false;
        });
    }

    private static List<Category> parse(JsonObject root) {
        Map<String, List<Integer>> brackets = readBrackets(
                root.has("brackets") && root.get("brackets").isJsonObject()
                        ? root.getAsJsonObject("brackets") : null);

        // critters and co level on their own shorter step tables (bracketSets.<TYPE>.<bracket>)
        Map<String, Map<String, List<Integer>>> bracketSets = new HashMap<>();
        if (root.has("bracketSets") && root.get("bracketSets").isJsonObject()) {
            JsonObject sets = root.getAsJsonObject("bracketSets");
            for (String type : sets.keySet()) {
                if (sets.get(type).isJsonObject()) {
                    bracketSets.put(type, readBrackets(sets.getAsJsonObject(type)));
                }
            }
        }

        List<Category> out = new ArrayList<>();
        for (String key : root.keySet()) {
            if (key.equals("brackets") || key.equals("bracketSets")) continue;
            JsonElement element = root.get(key);
            if (!element.isJsonObject()) continue;
            JsonObject category = element.getAsJsonObject();
            if (!category.has("name")) continue;

            String name = category.get("name").getAsString();

            // fishing holds its mobs one level deeper; those groups stay inside their parent
            // and are picked above the grid, not stacked into the rail
            if (!category.has("mobs")) {
                List<Category> subs = new ArrayList<>();
                for (String subKey : category.keySet()) {
                    if (!category.get(subKey).isJsonObject()) continue;
                    JsonObject sub = category.getAsJsonObject(subKey);
                    if (!sub.has("name")) continue;

                    List<Mob> subMobs = new ArrayList<>();
                    collectMobs(sub, subMobs, brackets, bracketSets);
                    if (subMobs.isEmpty()) continue;

                    subs.add(new Category(key + "." + subKey, sub.get("name").getAsString(),
                            texture(sub), item(sub), subMobs, List.of()));
                }
                if (subs.isEmpty()) continue;

                out.add(new Category(key, name, texture(category), item(category),
                        subs.getFirst().mobs(), subs));
                continue;
            }

            List<Mob> mobs = new ArrayList<>();
            collectMobs(category, mobs, brackets, bracketSets);
            if (mobs.isEmpty()) continue;

            out.add(new Category(key, name, texture(category), item(category), mobs, List.of()));
        }
        return out;
    }

    private static void collectMobs(JsonObject category, List<Mob> out,
                                    Map<String, List<Integer>> brackets,
                                    Map<String, Map<String, List<Integer>>> bracketSets) {
        if (category.has("mobs") && category.get("mobs").isJsonArray()) {
            for (JsonElement element : category.getAsJsonArray("mobs")) {
                if (!element.isJsonObject()) continue;
                JsonObject mob = element.getAsJsonObject();
                if (!mob.has("name")) continue;

                String name = mob.get("name").getAsString();
                String plain = com.teslamaps.utils.StringUtil.strip(name).trim();

                long cap = mob.has("cap") ? mob.get("cap").getAsLong() : 0;
                Long capFix = CAP_FIX.get(plain);
                if (capFix != null) cap = capFix;

                List<Integer> steps = steps(plain, mob, cap, brackets, bracketSets);

                List<String> keys = mob.has("mobs") && mob.get("mobs").isJsonArray()
                        ? strings(mob.getAsJsonArray("mobs")) : List.of();
                out.add(new Mob(name, string(mob, "texture"),
                        string(mob, "item"), cap, steps, keys));
            }
            return;
        }
        for (String key : category.keySet()) {
            if (category.get(key).isJsonObject()) {
                collectMobs(category.getAsJsonObject(key), out, brackets, bracketSets);
            }
        }
    }

    // NEU's caps lag behind the game; these were verified against in-game dumps (2026-09-17)
    private static final Map<String, Long> CAP_FIX = Map.of(
            "Beeheemoth", 50L, "Tidetot", 50L,
            "Mithril Grubber", 300L, "Oasis Rabbit", 300L, "Oasis Sheep", 300L,
            "Bogged", 1000L, "Cretan Bull", 3000L, "Stranded Nymph", 3000L);

    // critter families NEU has not tagged yet (verified in game: they level on critter tables)
    private static final java.util.Set<String> CRITTER_FIX = java.util.Set.of(
            "Honeyhog", "Lotusfish", "Flipflopper", "Lotum", "Tidetot");

    // 5 tiers ending on the 50 cap (in game: V at 50/50) - intermediates are a guess, the
    // family GUI was not dumped; only non-maxed progression can be off by a step
    private static final Map<String, List<Integer>> STEP_FIX = Map.of(
            "Queen Ant", List.of(1, 5, 15, 30, 50));

    /**
     * The tier table for a mob. For critters the repo's bracket field is unreliable - the cap
     * is what the game actually stops the family at, so the critter table whose end matches
     * the cap is the right one (verified against in-game dumps: Hewver/Murkbat sit on table 4,
     * Honeymite/Woodlouse on 3, Honeyhog/Stag Beetle on 2, despite other bracket fields).
     */
    private static List<Integer> steps(String plain, JsonObject mob, long cap,
                                       Map<String, List<Integer>> brackets,
                                       Map<String, Map<String, List<Integer>>> bracketSets) {
        List<Integer> fixed = STEP_FIX.get(plain);
        if (fixed != null) return fixed;

        String bracket = mob.has("bracket") ? mob.get("bracket").getAsString() : "1";
        String type = mob.has("bracketType")
                ? mob.get("bracketType").getAsString().toUpperCase(java.util.Locale.ROOT) : null;

        Map<String, List<Integer>> critters = bracketSets.getOrDefault("CRITTERS", Map.of());
        boolean critter = CRITTER_FIX.contains(plain) || (type != null && type.startsWith("CRITTER"));

        if (type != null && !critter) {
            Map<String, List<Integer>> set = bracketSets.get(type);
            if (set == null) set = bracketSets.get(type + "S");
            return capped(set != null ? set.get(bracket) : null, cap);
        }

        if (critter) {
            for (List<Integer> table : critters.values()) {
                if (!table.isEmpty() && table.getLast() == cap) return table;
            }
            return capped(critters.get(bracket), cap);
        }

        // untagged mob whose cap sits exactly on its critter table end = untagged critter
        // (grizzly bear, parched - queen ant is shielded by STEP_FIX above)
        List<Integer> match = critters.get(bracket);
        if (match != null && !match.isEmpty() && match.getLast() == cap) return match;

        return capped(brackets.get(bracket), cap);
    }

    private static String texture(JsonObject category) {
        JsonObject icon = category.has("icon") && category.get("icon").isJsonObject()
                ? category.getAsJsonObject("icon") : null;
        return string(icon, "texture");
    }

    private static String item(JsonObject category) {
        JsonObject icon = category.has("icon") && category.get("icon").isJsonObject()
                ? category.getAsJsonObject("icon") : null;
        return string(icon, "item");
    }

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

    /**
     * skyblock-pv's tier table: every step below the cap, then the cap itself as the last tier.
     * Taking only steps <= cap dropped the final tier for every mob whose cap is not a bracket
     * value (cap 125 on bracket 1 ended at tier 4 instead of 5).
     */
    private static List<Integer> capped(List<Integer> all, long cap) {
        if (all == null || all.isEmpty()) return List.of();
        if (cap <= 0) return all;
        List<Integer> out = new ArrayList<>();
        for (int step : all) {
            if (step >= cap) break;
            out.add(step);
        }
        out.add((int) cap);
        return out;
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
