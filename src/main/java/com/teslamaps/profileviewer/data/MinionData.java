package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Minion ids per collection category, their top tier and the crafted tiers of a profile (coop included). */
public class MinionData {
    private static Map<String, List<String>> categories;
    private static Map<String, Integer> maxTiers;
    private static Map<Integer, Integer> tierXp;

    private static void load() {
        if (categories != null) return;
        Map<String, List<String>> cats = new LinkedHashMap<>();
        Map<String, Integer> max = new HashMap<>();
        Map<Integer, Integer> xp = new HashMap<>();
        try (InputStream stream = MinionData.class.getResourceAsStream("/assets/teslamaps/pv/minions.json")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("categories").entrySet()) {
                    List<String> ids = new ArrayList<>();
                    for (JsonElement id : e.getValue().getAsJsonArray()) ids.add(id.getAsString());
                    cats.put(e.getKey(), List.copyOf(ids));
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("maxTier").entrySet()) {
                    max.put(e.getKey(), e.getValue().getAsInt());
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("xp").entrySet()) {
                    xp.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read minions.json: {}", e.toString());
        }
        categories = cats;
        maxTiers = max;
        tierXp = xp;
    }

    public static Map<String, List<String>> categories() {
        load();
        return categories;
    }

    public static int maxTier(String minion) {
        load();
        return maxTiers.getOrDefault(minion, 11);
    }

    /** SkyBlock xp for crafting one tier, from NEU's minionXp. */
    public static int tierXp(int tier) {
        load();
        return tierXp.getOrDefault(tier, 0);
    }

    /** minion id -> crafted tiers, over every member of the profile like the minion slots count. */
    public static Map<String, Set<Integer>> crafted(SkyblockProfile profile) {
        Map<String, Set<Integer>> out = new HashMap<>();
        if (profile == null) return out;
        JsonObject full = profile.getFullProfileJson();
        List<JsonObject> members = new ArrayList<>();
        if (full != null && full.has("members") && full.get("members").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : full.getAsJsonObject("members").entrySet()) {
                if (e.getValue().isJsonObject()) members.add(e.getValue().getAsJsonObject());
            }
        }
        if (members.isEmpty() && profile.getMemberData() != null) members.add(profile.getMemberData());

        for (JsonObject member : members) {
            craftedBy(member).forEach((id, tiers) -> out.computeIfAbsent(id, k -> new TreeSet<>()).addAll(tiers));
        }
        return out;
    }

    /** Own crafted tiers only, for the SkyBlock level (xp is per player). */
    public static Map<String, Set<Integer>> craftedBy(JsonObject member) {
        Map<String, Set<Integer>> out = new HashMap<>();
        JsonObject data = member != null && member.has("player_data") && member.get("player_data").isJsonObject()
                ? member.getAsJsonObject("player_data") : null;
        if (data == null || !data.has("crafted_generators") || !data.get("crafted_generators").isJsonArray()) return out;
        for (JsonElement element : data.getAsJsonArray("crafted_generators")) {
            if (!element.isJsonPrimitive()) continue;
            String raw = element.getAsString();
            int split = raw.lastIndexOf('_');
            if (split <= 0) continue;
            try {
                out.computeIfAbsent(raw.substring(0, split), k -> new TreeSet<>()).add(Integer.parseInt(raw.substring(split + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
