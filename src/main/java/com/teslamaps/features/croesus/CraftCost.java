package com.teslamaps.features.croesus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Raw craft cost from the NEU crafting recipes, priced with the caller's source.
 * Ported from SkyHanni's ItemPriceUtils.getRawCraftCostOrNull: cheapest recipe wins,
 * ingredients without a market price recurse into their own recipe.
 * Recipes load per item in the background, so the first ask for an item is null.
 */
public final class CraftCost {

    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/%s.json";
    private static final int MAX_DEPTH = 4;
    private static final long RETRY_MS = 5 * 60_000L;
    private static final long CACHE_MS = 10_000L;

    private record Recipe(Map<String, Integer> ingredients, int count) {}
    private record Cached(Double cost, long at) {}

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "teslamaps-recipes");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, List<Recipe>> recipes = new ConcurrentHashMap<>();
    private static final Map<String, Long> failed = new ConcurrentHashMap<>();
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();
    private static final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private CraftCost() {}

    /** Cheapest raw craft cost for one item, null while unknown or uncraftable. */
    public static Double costOrNull(String id, int source) {
        if (id == null || id.isEmpty()) return null;
        String key = source + "|" + id;
        long now = System.currentTimeMillis();
        Cached c = cache.get(key);
        if (c != null && now - c.at() < CACHE_MS) return c.cost();
        Double cost = compute(id, source, 0, new HashSet<>());
        if (cache.size() > 4096) cache.clear();
        cache.put(key, new Cached(cost, now));
        return cost;
    }

    /** True once the recipe file for the item has been loaded (with or without a recipe). */
    public static boolean known(String id) {
        return recipes.containsKey(id);
    }

    /** Ingredients of the first crafting recipe; null while loading, empty when uncraftable. */
    public static Map<String, Integer> ingredientsOrNull(String id) {
        if (id == null || id.isEmpty()) return null;
        List<Recipe> list = recipes.get(id);
        if (list == null) {
            fetch(id);
            return null;
        }
        return list.isEmpty() ? Map.of() : list.getFirst().ingredients();
    }

    private static Double compute(String id, int source, int depth, Set<String> path) {
        List<Recipe> list = recipes.get(id);
        if (list == null) {
            fetch(id);
            return null;
        }
        if (list.isEmpty() || !path.add(id)) return null;
        Double best = null;
        for (Recipe r : list) {
            double sum = 0;
            boolean ok = true;
            for (Map.Entry<String, Integer> ing : r.ingredients().entrySet()) {
                Double p = ingredientPrice(ing.getKey(), source, depth, path);
                if (p == null) {
                    ok = false;
                    break;
                }
                sum += p * ing.getValue();
            }
            if (!ok || sum <= 0) continue;
            double per = sum / Math.max(1, r.count());
            if (best == null || per < best) best = per;
        }
        path.remove(id);
        return best;
    }

    private static Double ingredientPrice(String neuId, int source, int depth, Set<String> path) {
        String id = neuId.replace('-', ':');
        Double p = PriceManager.marketPriceOrNull(id, source);
        if (p == null && !id.equals(neuId)) p = PriceManager.marketPriceOrNull(neuId, source);
        if (p != null || source == PriceManager.SOURCE_NPC || depth >= MAX_DEPTH) return p;
        return compute(neuId, source, depth + 1, path);
    }

    private static void fetch(String id) {
        Long f = failed.get(id);
        if (f != null && System.currentTimeMillis() - f < RETRY_MS) return;
        if (!pending.add(id)) return;
        EXEC.submit(() -> {
            try {
                String file = URLEncoder.encode(id.replace(':', '-'), StandardCharsets.UTF_8).replace("+", "%20");
                HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(URL, file)))
                        .timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 404) {
                    recipes.put(id, List.of());
                } else if (resp.statusCode() != 200) {
                    failed.put(id, System.currentTimeMillis());
                } else {
                    recipes.put(id, parse(JsonParser.parseString(resp.body()).getAsJsonObject()));
                    cache.clear();
                }
            } catch (Exception e) {
                failed.put(id, System.currentTimeMillis());
                TeslaMaps.LOGGER.warn("[Price] recipe for {} failed: {}", id, e.toString());
            } finally {
                pending.remove(id);
            }
        });
    }

    private static final String[] SLOTS = {"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

    private static List<Recipe> parse(JsonObject item) {
        List<Recipe> out = new ArrayList<>();
        if (item.has("recipe") && item.get("recipe").isJsonObject()) {
            Recipe r = grid(item.getAsJsonObject("recipe"));
            if (r != null) out.add(r);
        }
        if (item.has("recipes") && item.get("recipes").isJsonArray()) {
            for (JsonElement el : item.getAsJsonArray("recipes")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String type = o.has("type") ? o.get("type").getAsString() : "crafting";
                if (!type.equals("crafting")) continue;
                Recipe r = grid(o);
                if (r != null) out.add(r);
            }
        }
        return out;
    }

    private static Recipe grid(JsonObject o) {
        Map<String, Integer> ing = new HashMap<>();
        for (String slot : SLOTS) {
            if (!o.has(slot)) continue;
            String v = o.get(slot).getAsString();
            if (v.isEmpty()) continue;
            int colon = v.lastIndexOf(':');
            String ingId = colon > 0 ? v.substring(0, colon) : v;
            int amount = 1;
            if (colon > 0) {
                try {
                    amount = (int) Math.round(Double.parseDouble(v.substring(colon + 1)));
                } catch (NumberFormatException e) {
                    ingId = v;
                }
            }
            ing.merge(ingId, amount, Integer::sum);
        }
        if (ing.isEmpty()) return null;
        int count = 1;
        if (o.has("count")) {
            try { count = Math.max(1, o.get("count").getAsInt()); } catch (Exception ignored) {}
        }
        return new Recipe(ing, count);
    }
}
