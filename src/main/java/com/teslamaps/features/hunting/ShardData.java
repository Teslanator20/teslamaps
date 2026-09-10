package com.teslamaps.features.hunting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Attribute shard tables from the NEU repo plus the per-shard syphon/hunting-box amounts
 * we read out of the game menus. Backs the hunting box value and the shard overlay.
 */
public class ShardData {

    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/attribute_shards.json";

    public record Shard(String bazaarId, String displayName, String rarity,
                        String internalName, String abilityName, String shardId, String alignment) {}

    private static final Map<String, int[]> levelling = new ConcurrentHashMap<>();
    private static final List<Shard> shards = new ArrayList<>();
    private static final Map<String, Shard> byBazaarId = new ConcurrentHashMap<>();
    private static final Map<String, Shard> byInternal = new ConcurrentHashMap<>();
    private static final Map<String, Shard> byDisplay = new ConcurrentHashMap<>();
    private static final Map<String, Shard> byAbility = new ConcurrentHashMap<>();
    private static final java.util.Set<String> unconsumable = ConcurrentHashMap.newKeySet();

    private static volatile boolean loaded = false;
    private static volatile boolean loading = false;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "teslamaps-shards");
        t.setDaemon(true);
        return t;
    });

    public static boolean loaded() { return loaded; }

    public static void load() {
        if (loaded || loading) return;
        loading = true;
        EXEC.submit(() -> {
            try {
                parse(fetch());
                loaded = true;
                TeslaMaps.LOGGER.info("[Shards] {} attribute shards, {} rarities", shards.size(), levelling.size());
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Shards] load failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    private static JsonObject fetch() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(30)).header("User-Agent", "TeslaMaps").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void parse(JsonObject root) {
        if (root.has("attribute_levelling")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("attribute_levelling").entrySet()) {
                List<Integer> tiers = new ArrayList<>();
                for (JsonElement t : e.getValue().getAsJsonArray()) tiers.add(t.getAsInt());
                int[] arr = new int[tiers.size()];
                for (int i = 0; i < arr.length; i++) arr[i] = tiers.get(i);
                levelling.put(e.getKey().toUpperCase(Locale.ROOT), arr);
            }
        }
        if (root.has("unconsumable_attributes")) {
            for (JsonElement e : root.getAsJsonArray("unconsumable_attributes")) unconsumable.add(e.getAsString());
        }
        if (!root.has("attributes")) return;

        List<Shard> parsed = new ArrayList<>();
        for (JsonElement raw : root.getAsJsonArray("attributes")) {
            JsonObject o = raw.getAsJsonObject();
            Shard shard = new Shard(
                    str(o, "bazaarName"), str(o, "displayName"), str(o, "rarity").toUpperCase(Locale.ROOT),
                    str(o, "internalName"), str(o, "abilityName"), str(o, "shardId"), str(o, "alignment"));
            if (shard.bazaarId.isEmpty()) continue;
            parsed.add(shard);
            byBazaarId.put(shard.bazaarId, shard);
            byInternal.put(base(shard.internalName), shard);
            if (!shard.displayName.isEmpty()) byDisplay.put(shard.displayName.toLowerCase(Locale.ROOT), shard);
            if (!shard.abilityName.isEmpty()) byAbility.put(shard.abilityName.toLowerCase(Locale.ROOT), shard);
        }
        synchronized (shards) {
            shards.clear();
            shards.addAll(parsed);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    /** NEU ids carry the rarity behind a semicolon. */
    private static String base(String internalName) {
        int i = internalName.indexOf(';');
        return i < 0 ? internalName : internalName.substring(0, i);
    }

    public static List<Shard> all() {
        synchronized (shards) { return List.copyOf(shards); }
    }

    /** Shards that can actually be syphoned, so the denominator of "x/y maxed". */
    public static int maxShards() {
        synchronized (shards) { return Math.max(0, shards.size() - unconsumable.size()); }
    }

    public static boolean isConsumable(Shard shard) {
        return shard != null && !unconsumable.contains(shard.bazaarId);
    }

    /** Hypixel calls the item SHARD_GROVE, NEU calls it ATTRIBUTE_SHARD_NATURE_ELEMENTAL;1. */
    public static Shard bySkyblockId(String id) {
        if (id == null || id.isEmpty()) return null;
        Shard shard = byBazaarId.get(id);
        return shard != null ? shard : byInternal.get(base(id));
    }

    public static Shard byDisplayName(String name) {
        return name == null ? null : byDisplay.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Item names carry the suffix ("Grove Shard"), the repo display name does not. */
    public static Shard byItemName(String itemName) {
        if (itemName == null) return null;
        String name = itemName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(" shard")) name = name.substring(0, name.length() - 6);
        return byDisplayName(name);
    }

    public static Shard byAbilityName(String name) {
        return name == null ? null : byAbility.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static Shard byBazaarId(String id) {
        return id == null ? null : byBazaarId.get(id);
    }

    public static int[] levelling(String rarity) {
        return rarity == null ? null : levelling.get(rarity.toUpperCase(Locale.ROOT));
    }

    /** {current tier, shards to the next tier, shards to tier 10} for a total syphoned amount. */
    public static int[] tiers(Shard shard, int syphoned) {
        int[] costs = shard == null ? null : levelling(shard.rarity);
        if (costs == null) return new int[] { 0, 0, 0 };

        int tier = 0, cumulative = 0, toNext = 0;
        for (int cost : costs) {
            cumulative += cost;
            if (cumulative > syphoned) {
                if (toNext == 0) toNext = cumulative - syphoned;
            } else {
                tier++;
            }
        }
        return new int[] { tier, toNext, Math.max(0, cumulative - syphoned) };
    }

    /** Inverse of {@link #tiers}: the total syphoned amount a menu tier + "syphon n more" implies. */
    public static int totalSyphoned(Shard shard, int tier, int toNextTier) {
        int[] costs = shard == null ? null : levelling(shard.rarity);
        if (costs == null) return 0;
        int cumulative = 0;
        for (int i = 0; i < Math.min(tier + 1, costs.length); i++) cumulative += costs[i];
        return Math.max(0, cumulative - toNextTier);
    }

    // ---------------------------------------------------------------- stored amounts

    public static int syphoned(Shard shard) {
        if (shard == null) return 0;
        Integer v = TeslaMapsConfig.get().shardsSyphoned.get(shard.bazaarId);
        return v == null ? 0 : v;
    }

    public static int inBox(Shard shard) {
        if (shard == null) return 0;
        Integer v = TeslaMapsConfig.get().shardsInBox.get(shard.bazaarId);
        return v == null ? 0 : v;
    }

    public static void setSyphoned(Shard shard, int amount) {
        if (shard == null) return;
        TeslaMapsConfig.get().shardsSyphoned.put(shard.bazaarId, Math.max(0, amount));
    }

    public static void setInBox(Shard shard, int amount) {
        if (shard == null) return;
        TeslaMapsConfig.get().shardsInBox.put(shard.bazaarId, Math.max(0, amount));
    }

    public static void addInBox(Shard shard, int delta) {
        setInBox(shard, inBox(shard) + delta);
    }

    public static void resetBox() {
        TeslaMapsConfig.get().shardsInBox.clear();
    }

    // Shards sitting loose in the player inventory - what an instant buy leaves you with until
    // they are sent to the box. Live only, rescanned every tick, never stored.
    private static Map<String, Integer> inventory = Map.of();

    public static void setInventory(Map<String, Integer> counts) {
        inventory = counts;
    }

    public static int inInventory(Shard shard) {
        if (shard == null) return 0;
        Integer v = inventory.get(shard.bazaarId());
        return v == null ? 0 : v;
    }

    /** Shards we have any data for, so the overlay can tell "not owned" from "never seen". */
    public static Map<String, Integer> storedSyphoned() {
        return new HashMap<>(TeslaMapsConfig.get().shardsSyphoned);
    }

    // ---------------------------------------------------------------- lore helpers

    public static int romanToInt(String roman) {
        if (roman == null || roman.isEmpty()) return 0;
        int total = 0, previous = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int value = switch (roman.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                default -> 0;
            };
            if (value == 0) return 0;
            total += value < previous ? -value : value;
            previous = Math.max(previous, value);
        }
        return total;
    }

    private ShardData() {}
}
