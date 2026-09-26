package com.teslamaps.features.pets;

// Data bundled from Skysoft's pet_constants.json (NEU-style)
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PetConstants {

    private static final List<Long> LEVELS = new ArrayList<>();
    private static final Map<String, Integer> OFFSETS = new HashMap<>();
    private static final Map<String, List<Long>> CUSTOM_LEVELS = new HashMap<>();
    private static final Map<String, Integer> CUSTOM_MAX = new HashMap<>();
    private static final Map<String, Double> CUSTOM_MULTIPLIER = new HashMap<>();
    private static final Map<String, String> PET_TYPES = new HashMap<>();
    private static final Map<String, String> ID_TO_DISPLAY = new HashMap<>();
    private static final Map<String, String> ITEM_NAME_TO_ID = new HashMap<>();

    private static boolean loaded = false;

    private static void load() {
        if (loaded) return;
        loaded = true;
        try (var in = PetConstants.class.getResourceAsStream("/assets/teslamaps/data/pet_constants.json")) {
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("pet_levels")) LEVELS.add(e.getAsLong());
            JsonObject offsets = root.getAsJsonObject("pet_rarity_offset");
            for (String key : offsets.keySet()) OFFSETS.put(key, offsets.get(key).getAsInt());
            JsonObject custom = root.getAsJsonObject("custom_pet_leveling");
            for (String key : custom.keySet()) {
                JsonObject entry = custom.getAsJsonObject(key);
                if (entry.has("pet_levels")) {
                    List<Long> list = new ArrayList<>();
                    for (JsonElement e : entry.getAsJsonArray("pet_levels")) list.add(e.getAsLong());
                    CUSTOM_LEVELS.put(key, list);
                }
                if (entry.has("max_level")) CUSTOM_MAX.put(key, entry.get("max_level").getAsInt());
                if (entry.has("xp_multiplier")) CUSTOM_MULTIPLIER.put(key, entry.get("xp_multiplier").getAsDouble());
            }
            JsonObject types = root.getAsJsonObject("pet_types");
            for (String key : types.keySet()) PET_TYPES.put(key, types.get(key).getAsString());
            JsonObject names = root.getAsJsonObject("id_to_display_name");
            for (String key : names.keySet()) ID_TO_DISPLAY.put(key, names.get(key).getAsString());
            JsonObject items = root.getAsJsonObject("pet_item_display_name_to_id");
            for (String key : items.keySet()) ITEM_NAME_TO_ID.put(key, items.get(key).getAsString());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read pet_constants.json: {}", e.toString());
        }
    }

    /** "Golden Dragon" -> GOLDEN_DRAGON, "T-Rex" -> TYRANNOSAURUS. */
    public static String internalName(String displayName) {
        load();
        String clean = displayName.trim();
        for (Map.Entry<String, String> entry : ID_TO_DISPLAY.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(clean)) return entry.getKey();
        }
        return clean.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** GOLDEN_DRAGON -> "Golden Dragon". */
    public static String displayName(String internalName) {
        load();
        String name = ID_TO_DISPLAY.get(internalName);
        if (name != null) return name;
        StringBuilder out = new StringBuilder();
        for (String w : internalName.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    /** COMBAT / MINING / ... / ALL, null when unknown. */
    public static String petType(String internalName) {
        load();
        return PET_TYPES.get(internalName);
    }

    public static int maxLevel(String internalName) {
        load();
        Integer max = CUSTOM_MAX.get(internalName);
        if (max != null) return max;
        return 100;
    }

    public static double customMultiplier(String internalName) {
        load();
        return CUSTOM_MULTIPLIER.getOrDefault(internalName, 1.0);
    }

    /** Total xp needed to reach the given level from level 1. */
    public static double levelToXp(String internalName, String rarity, int level) {
        load();
        List<Long> tree = tree(internalName, rarity);
        double sum = 0;
        for (int i = 0; i < level - 1 && i < tree.size(); i++) sum += tree.get(i);
        return sum;
    }

    public static int xpToLevel(String internalName, String rarity, double totalXp) {
        load();
        List<Long> tree = tree(internalName, rarity);
        int max = maxLevel(internalName);
        double sum = 0;
        for (int level = 1; level < max && level - 1 < tree.size(); level++) {
            sum += tree.get(level - 1);
            if (totalXp < sum) return level;
        }
        return max;
    }

    /** Xp from this level to the next, 0 at max. */
    public static double costForLevel(String internalName, String rarity, int level) {
        load();
        List<Long> tree = tree(internalName, rarity);
        if (level >= maxLevel(internalName)) return 0;
        int index = level - 1;
        return index >= 0 && index < tree.size() ? tree.get(index) : 0;
    }

    private static List<Long> tree(String internalName, String rarity) {
        int offset = OFFSETS.getOrDefault(rarity == null ? "COMMON" : rarity.toUpperCase(Locale.ROOT), 0);
        List<Long> base = LEVELS.subList(Math.min(offset, LEVELS.size()), LEVELS.size());
        List<Long> custom = CUSTOM_LEVELS.get(internalName);
        if (custom == null) return base;
        List<Long> out = new ArrayList<>(base.subList(0, Math.min(99, base.size())));
        out.addAll(custom);
        return out;
    }

    public static java.util.Collection<String> itemIds() {
        load();
        return ITEM_NAME_TO_ID.values();
    }

    /** Pet item id from the widget's colored display name; falls back to a stripped-name match. */
    public static String itemId(String coloredName) {
        load();
        String direct = ITEM_NAME_TO_ID.get(coloredName.trim());
        if (direct != null) return direct;
        String stripped = coloredName.replaceAll("§.", "").trim();
        for (Map.Entry<String, String> entry : ITEM_NAME_TO_ID.entrySet()) {
            if (entry.getKey().replaceAll("§.", "").trim().equalsIgnoreCase(stripped)) return entry.getValue();
        }
        return null;
    }
}
