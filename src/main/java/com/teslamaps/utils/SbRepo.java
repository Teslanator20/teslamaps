package com.teslamaps.utils;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Repo tables behind the estimated item value breakdown: reforge stone costs and
 * essence/gemstone upgrade recipes from the NEU repo, enchantment quirks and
 * crimson prestige costs from the SkyHanni repo.
 */
public class SbRepo {

    private static final String NEU =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/%s.json";
    private static final String SH =
            "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/%s.json";

    public record Reforge(String stoneId, String name, Map<String, Long> costs) {}
    public record EssenceTier(int essenceAmount, String essenceType, long coins, Map<String, Integer> items) {}
    public record Endcap(int requiredLevel, String item) {}
    public record AlwaysActive(int level, Set<String> items) {}

    private static final Map<String, Reforge> reforges = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, EssenceTier>> essence = new ConcurrentHashMap<>();
    private static final Map<String, Integer> dungeonize = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, List<String>>> gemstoneCosts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> bookBundle = new ConcurrentHashMap<>();
    private static final Map<String, AlwaysActive> alwaysActive = new ConcurrentHashMap<>();
    private static final Set<String> onlyTierOne = ConcurrentHashMap.newKeySet();
    private static final Set<String> onlyTierFive = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<Endcap>> endcaps = new ConcurrentHashMap<>();
    private static final Set<String> stacking = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> enchantNames = new ConcurrentHashMap<>();
    private static final Set<String> ultimateEnchants = ConcurrentHashMap.newKeySet();
    private static final Map<String, Map<String, Integer>> crimsonPrestige = new ConcurrentHashMap<>();
    private static final Set<String> legacyGemstoneSlots = ConcurrentHashMap.newKeySet();

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "teslamaps-repo");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean loaded = false;
    private static volatile boolean loading = false;

    public static boolean loaded() { return loaded; }

    /** Kicks off the one-off table load. Safe to call repeatedly. */
    public static void load() {
        if (loaded || loading) return;
        loading = true;
        RepoCache.onReload("SbRepo", SbRepo::reload);
        EXEC.submit(() -> {
            try {
                loadReforgeStones();
                loadEssenceCosts();
                loadGemstoneCosts();
                loadItems();
                loadEnchants();
                loaded = true;
                TeslaMaps.LOGGER.info("[Repo] {} reforges, {} essence items, {} gemstone items, {} enchants",
                        reforges.size(), essence.size(), gemstoneCosts.size(), enchantNames.size());
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Repo] load failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    /** Drops the loaded flag and fetches again; the old tables stay until the new ones are in. */
    public static void reload() {
        if (loading) return;
        loaded = false;
        load();
    }

    public static Reforge reforgeByModifier(String modifier) {
        return modifier == null ? null : reforges.get(modifier.toLowerCase(Locale.ROOT));
    }

    public static Map<Integer, EssenceTier> essencePrices(String id) { return essence.get(id); }

    public static Set<String> essenceItems() { return essence.keySet(); }

    /** Essence to dungeonize the item, 0 when the repo lists none. */
    public static int dungeonizeCost(String id) { return dungeonize.getOrDefault(id, 0); }

    public static Map<String, List<String>> gemstoneCosts(String id) { return gemstoneCosts.get(id); }

    public static boolean knowsGemstoneCosts(String id) { return gemstoneCosts.containsKey(id); }

    public static boolean hasLegacyGemstoneSlots(String id) { return legacyGemstoneSlots.contains(id); }

    public static int bookBundleAmount(String enchant) { return bookBundle.getOrDefault(enchant, 5); }

    public static AlwaysActive alwaysActive(String enchant) { return alwaysActive.get(enchant); }

    public static boolean onlyTierOnePrice(String enchant) { return onlyTierOne.contains(enchant); }

    public static boolean onlyTierFivePrice(String enchant) { return onlyTierFive.contains(enchant); }

    public static List<Endcap> endcaps(String enchant) { return endcaps.get(enchant); }

    public static boolean isStackingEnchant(String enchant) { return stacking.contains(enchant); }

    public static Map<String, Integer> crimsonPrestigeCost(String tier) { return crimsonPrestige.get(tier); }

    /** "§9Sharpness 6" / "§d§lUltimate Wise 5", the way the NEU book name reads. */
    public static String enchantName(String nbtName, int level) {
        String lore = enchantNames.get(nbtName);
        if (lore == null) lore = StringUtil.titleCase(nbtName.replace('_', ' '));
        String color = ultimateEnchants.contains(nbtName) ? "§d§l" : "§9";
        return color + lore + " " + level;
    }

    private static void loadReforgeStones() throws Exception {
        JsonObject root = fetch(String.format(NEU, "reforgestones"));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonObject o = entry.getValue().getAsJsonObject();
            String name = str(o, "reforgeName");
            String modifier = o.has("nbtModifier") ? o.get("nbtModifier").getAsString()
                    : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s_-]", "").replaceAll("[\\s-]", "_");
            String stone = o.has("internalName") ? o.get("internalName").getAsString() : entry.getKey();

            Map<String, Long> costs = new java.util.HashMap<>();
            if (o.has("reforgeCosts") && o.get("reforgeCosts").isJsonObject()) {
                for (Map.Entry<String, JsonElement> cost : o.getAsJsonObject("reforgeCosts").entrySet()) {
                    costs.put(cost.getKey(), cost.getValue().getAsLong());
                }
            }
            reforges.putIfAbsent(modifier, new Reforge(stone, name, costs));
        }
    }

    private static void loadEssenceCosts() throws Exception {
        JsonObject root = fetch(String.format(NEU, "essencecosts"));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonObject o = entry.getValue().getAsJsonObject();
            String type = str(o, "type");
            if (o.has("dungeonize") && o.get("dungeonize").isJsonPrimitive()) {
                dungeonize.put(entry.getKey(), o.get("dungeonize").getAsInt());
            }

            Map<Integer, Map<String, Integer>> tierItems = new java.util.HashMap<>();
            Map<Integer, Long> tierCoins = new java.util.HashMap<>();
            if (o.has("items") && o.get("items").isJsonObject()) {
                for (Map.Entry<String, JsonElement> extra : o.getAsJsonObject("items").entrySet()) {
                    int tier;
                    try { tier = Integer.parseInt(extra.getKey()); } catch (NumberFormatException e) { continue; }
                    Map<String, Integer> items = new java.util.HashMap<>();
                    for (JsonElement raw : extra.getValue().getAsJsonArray()) {
                        String[] split = raw.getAsString().split(":");
                        if (split.length != 2) continue;
                        long amount;
                        try { amount = Long.parseLong(split[1]); } catch (NumberFormatException e) { continue; }
                        if (split[0].equals("SKYBLOCK_COIN")) tierCoins.put(tier, amount);
                        else items.put(split[0], (int) amount);
                    }
                    tierItems.put(tier, items);
                }
            }

            Map<Integer, EssenceTier> tiers = new java.util.LinkedHashMap<>();
            for (int tier = 1; tier <= 15; tier++) {
                String key = String.valueOf(tier);
                if (!o.has(key) || !o.get(key).isJsonPrimitive()) continue;
                tiers.put(tier, new EssenceTier(
                        o.get(key).getAsInt(),
                        type,
                        tierCoins.getOrDefault(tier, 0L),
                        tierItems.getOrDefault(tier, Map.of())));
            }
            if (!tiers.isEmpty()) essence.put(entry.getKey(), tiers);
        }
    }

    private static void loadGemstoneCosts() throws Exception {
        JsonObject root = fetch(String.format(NEU, "gemstonecosts"));
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            Map<String, List<String>> slots = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> slot : entry.getValue().getAsJsonObject().entrySet()) {
                List<String> ingredients = new ArrayList<>();
                for (JsonElement raw : slot.getValue().getAsJsonArray()) ingredients.add(raw.getAsString());
                slots.put(slot.getKey(), ingredients);
            }
            gemstoneCosts.put(entry.getKey(), slots);
        }
    }

    private static void loadItems() throws Exception {
        JsonObject root = fetch(String.format(SH, "Items"));

        if (root.has("book_bundle_amount")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("book_bundle_amount").entrySet()) {
                bookBundle.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        if (root.has("crimson_prestige_costs")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("crimson_prestige_costs").entrySet()) {
                Map<String, Integer> cost = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> item : e.getValue().getAsJsonObject().entrySet()) {
                    cost.put(item.getKey(), item.getValue().getAsInt());
                }
                crimsonPrestige.put(e.getKey(), cost);
            }
        }
        if (root.has("has_legacy_gemstone_slots")) {
            for (JsonElement e : root.getAsJsonArray("has_legacy_gemstone_slots")) legacyGemstoneSlots.add(e.getAsString());
        }

        if (!root.has("value_calculation_data")) return;
        JsonObject data = root.getAsJsonObject("value_calculation_data");

        if (data.has("always_active_enchants")) {
            for (Map.Entry<String, JsonElement> e : data.getAsJsonObject("always_active_enchants").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Set<String> items = new HashSet<>();
                if (o.has("items")) for (JsonElement item : o.getAsJsonArray("items")) items.add(item.getAsString());
                alwaysActive.put(e.getKey(), new AlwaysActive(o.has("level") ? o.get("level").getAsInt() : 0, items));
            }
        }
        if (data.has("only_tier_one_prices")) {
            for (JsonElement e : data.getAsJsonArray("only_tier_one_prices")) onlyTierOne.add(e.getAsString());
        }
        if (data.has("only_tier_five_prices")) {
            for (JsonElement e : data.getAsJsonArray("only_tier_five_prices")) onlyTierFive.add(e.getAsString());
        }
        String endcapKey = data.has("endcap_enchants_new") ? "endcap_enchants_new" : "endcap_enchants";
        if (data.has(endcapKey)) {
            for (Map.Entry<String, JsonElement> e : data.getAsJsonObject(endcapKey).entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                List<Endcap> list = new ArrayList<>();
                for (JsonElement raw : e.getValue().getAsJsonArray()) {
                    JsonObject o = raw.getAsJsonObject();
                    list.add(new Endcap(o.get("required_level").getAsInt(), o.get("endcap_item").getAsString()));
                }
                if (!list.isEmpty()) endcaps.put(e.getKey(), list);
            }
        }
    }

    private static void loadEnchants() throws Exception {
        JsonObject root = fetch(String.format(SH, "Enchants"));
        readEnchantGroup(root, "NORMAL", false);
        readEnchantGroup(root, "ULTIMATE", true);
        readEnchantGroup(root, "STACKING", false);
        if (root.has("STACKING")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("STACKING").entrySet()) {
                stacking.add(str(e.getValue().getAsJsonObject(), "nbtName"));
            }
        }
    }

    private static void readEnchantGroup(JsonObject root, String group, boolean ultimate) {
        if (!root.has(group)) return;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(group).entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            String nbt = str(o, "nbtName");
            if (nbt.isEmpty()) continue;
            String lore = str(o, "loreName");
            if (!lore.isEmpty()) enchantNames.put(nbt, lore);
            if (ultimate) ultimateEnchants.add(nbt);
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static JsonObject fetch(String url) throws Exception {
        String file = url.substring(url.lastIndexOf('/') + 1).replace(".json", "");
        String name = (url.contains("SkyHanni-REPO") ? "sh-" : "neu-") + file;
        return RepoCache.fetch(name, url, 30).getAsJsonObject();
    }

    private SbRepo() {}
}
