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
import java.util.List;
import java.util.Map;

/** Garden crops, level curve and milestone brackets. Ported from schrumbo's pv. */
public class GardenRegistry {

    public record Crop(String key, String display, String icon) {}

    public record Level(int level, int max, double progress) {}

    public record Milestone(int tier, double progress, boolean maxed) {}

    public static final int PLOTS_TOTAL = 24;

    /** Incremental xp per garden level, index i is the cost from level i to i+1. */
    private static final long[] GARDEN_EXP = {0, 70, 70, 140, 240, 600, 1500, 2000, 2500, 3000,
            10000, 10000, 10000, 10000, 10000};

    public static final List<Crop> CROPS = List.of(
            new Crop("WHEAT", "Wheat", "wheat"),
            new Crop("CARROT_ITEM", "Carrot", "carrot"),
            new Crop("POTATO_ITEM", "Potato", "potato"),
            new Crop("PUMPKIN", "Pumpkin", "pumpkin"),
            new Crop("MELON", "Melon", "melon_slice"),
            new Crop("SUGAR_CANE", "Sugar Cane", "sugar_cane"),
            new Crop("CACTUS", "Cactus", "cactus"),
            new Crop("INK_SACK:3", "Cocoa Beans", "cocoa_beans"),
            new Crop("NETHER_STALK", "Nether Wart", "nether_wart"),
            new Crop("MUSHROOM_COLLECTION", "Mushroom", "red_mushroom"),
            new Crop("DOUBLE_PLANT", "Sunflower", "sunflower"),
            new Crop("MOONFLOWER", "Moonflower", "lily_of_the_valley"),
            new Crop("WILD_ROSE", "Wild Rose", "poppy"));

    private static Map<String, long[]> milestones;

    private static synchronized Map<String, long[]> milestones() {
        if (milestones != null) return milestones;
        Map<String, long[]> loaded = new HashMap<>();
        try (InputStream stream = GardenRegistry.class
                .getResourceAsStream("/assets/teslamaps/pv/crop_milestones.json")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    List<Long> values = new ArrayList<>();
                    for (JsonElement element : entry.getValue().getAsJsonArray()) values.add(element.getAsLong());
                    long[] brackets = new long[values.size()];
                    for (int i = 0; i < brackets.length; i++) brackets[i] = values.get(i);
                    loaded.put(entry.getKey(), brackets);
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to read crop_milestones.json", e);
        }
        milestones = loaded;
        return milestones;
    }

    /** Crops per milestone tier for a crop key, null if unknown. */
    public static long[] brackets(String key) {
        return milestones().get(key);
    }

    /** Garden level with its cap; past the cap it overflows at the last bracket's cost. */
    public static Level level(long experience) {
        int max = GARDEN_EXP.length - 1;
        long accumulated = 0;
        for (int level = 1; level < GARDEN_EXP.length; level++) {
            long need = GARDEN_EXP[level];
            if (experience < accumulated + need) {
                return new Level(level, max, clamp((double) (experience - accumulated) / need));
            }
            accumulated += need;
        }
        long step = GARDEN_EXP[GARDEN_EXP.length - 1];
        long over = experience - accumulated;
        return new Level(max + (int) (over / step), max, (double) (over % step) / step);
    }

    /** Crop milestone tier, overflowing past the 46 tier cap like the game does. */
    public static Milestone milestone(String key, long collected) {
        long[] brackets = milestones().get(key);
        if (brackets == null || brackets.length == 0) return new Milestone(0, 0, false);
        long accumulated = 0;
        for (int i = 0; i < brackets.length; i++) {
            long need = brackets[i];
            if (collected < accumulated + need) {
                return new Milestone(i, clamp((double) (collected - accumulated) / need), false);
            }
            accumulated += need;
        }
        long step = brackets[brackets.length - 1];
        long over = collected - accumulated;
        return new Milestone(brackets.length + (int) (over / step), (double) (over % step) / step, true);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
