package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tree gift milestones and the starlyn caps, from the meowdding repo (pv/foraging). */
public class ForagingRepo {

    public record Milestone(int tier, int maxTier, double progress, long next) {}

    private static Map<String, long[]> gifts;
    private static JsonObject misc;

    private static synchronized void load() {
        if (gifts != null) return;
        Map<String, long[]> loaded = new HashMap<>();
        try (InputStream stream = ForagingRepo.class.getResourceAsStream("/assets/teslamaps/pv/tree_gifts.json")) {
            if (stream != null) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    var array = entry.getValue().getAsJsonArray();
                    long[] values = new long[array.size()];
                    long sum = 0;
                    for (int i = 0; i < array.size(); i++) {
                        sum += array.get(i).getAsLong();
                        values[i] = sum;
                    }
                    loaded.put(entry.getKey().toUpperCase(), values);
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read tree_gifts.json: {}", e.toString());
        }
        gifts = loaded;

        try (InputStream stream = ForagingRepo.class.getResourceAsStream("/assets/teslamaps/pv/foraging_misc.json")) {
            misc = stream == null ? new JsonObject() : JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            misc = new JsonObject();
        }
    }

    public static List<String> trees() {
        return List.of("FIG", "MANGROVE", "HELIX");
    }

    /** Milestone for a tree's total gift count; the tiers themselves are claimed in game. */
    public static Milestone milestone(String tree, long collected) {
        load();
        long[] steps = gifts.get(tree.toUpperCase());
        if (steps == null || steps.length == 0) return new Milestone(0, 0, 0, 0);

        for (int i = 0; i < steps.length; i++) {
            if (collected < steps[i]) {
                long base = i == 0 ? 0 : steps[i - 1];
                return new Milestone(i, steps.length, (double) (collected - base) / (steps[i] - base), steps[i]);
            }
        }
        return new Milestone(steps.length, steps.length, 1, steps[steps.length - 1]);
    }

    /** e.g. fig_personal_best, helix_fortune, agatha_power. */
    public static long value(String key) {
        load();
        return misc.has(key) && misc.get(key).isJsonPrimitive() && misc.get(key).getAsJsonPrimitive().isNumber()
                ? misc.get(key).getAsLong() : 0;
    }
}
