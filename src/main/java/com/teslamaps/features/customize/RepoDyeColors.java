package com.teslamaps.features.customize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The real skyblock dyes, static and animated, out of the NEU repo. Ported from Skyblocker. */
public class RepoDyeColors {
    public static final Map<String, Integer> STATIC_DYES = new LinkedHashMap<>();
    public static final Map<String, List<Integer>> ANIMATED_DYES = new LinkedHashMap<>();

    /** Dyes the NEU repo does not carry yet. */
    private static final Map<String, List<Integer>> EXTRA_ANIMATED = Map.of(
            "DYE_CHARCOAL", List.of(0x21262A, 0x202429));

    private static boolean requested = false;

    public static void init() {
        if (requested) return;
        requested = true;
        addExtras();
        CustomizeRepo.load("dyes", RepoDyeColors::parse);
    }

    private static void addExtras() {
        EXTRA_ANIMATED.forEach(ANIMATED_DYES::putIfAbsent);
    }

    private static void parse(JsonElement root) {
        Map<String, Integer> statics = new LinkedHashMap<>();
        Map<String, List<Integer>> animated = new LinkedHashMap<>();
        try {
            JsonObject object = root.getAsJsonObject();
            if (object.has("static")) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("static").entrySet()) {
                    Integer color = parseColor(entry.getValue().getAsString());
                    if (color != null) statics.put(entry.getKey(), color);
                }
            }
            if (object.has("animated")) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("animated").entrySet()) {
                    List<Integer> colors = new ArrayList<>();
                    for (JsonElement element : entry.getValue().getAsJsonArray()) {
                        Integer color = parseColor(element.getAsString());
                        if (color != null) colors.add(color);
                    }
                    if (colors.size() >= 2) animated.put(entry.getKey(), colors);
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Customize] malformed dyes.json: {}", e.toString());
            return;
        }
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            STATIC_DYES.clear();
            STATIC_DYES.putAll(statics);
            ANIMATED_DYES.clear();
            ANIMATED_DYES.putAll(animated);
            addExtras();
            TeslaMaps.LOGGER.info("[Customize] {} static dyes, {} animated dyes", STATIC_DYES.size(), ANIMATED_DYES.size());
        });
    }

    /** The repo writes colours as "r,g,b" or as a hex string. */
    private static Integer parseColor(String raw) {
        String value = raw.trim();
        try {
            if (value.contains(",")) {
                String[] parts = value.split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return (r << 16) | (g << 8) | b;
            }
            if (value.startsWith("#")) value = value.substring(1);
            return Integer.parseUnsignedInt(value, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return null;
        }
    }
}
