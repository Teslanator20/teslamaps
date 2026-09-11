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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heart of the Mountain / Heart of the Forest tree layouts. Ported from the pv repo, which
 * carries the node grid, the reward formulas and the powder costs that the api does not expose.
 */
public class PerkRegistry {

    public record Perk(String type, String id, int x, int y, Integer max, String costKind, String cost,
                       Map<String, String> reward, String name, List<String> tooltip) {}

    private static final List<Perk> HOTM = new ArrayList<>();
    private static final List<Perk> HOTF = new ArrayList<>();
    private static boolean loaded = false;

    public static List<Perk> hotm() {
        load();
        return HOTM;
    }

    public static List<Perk> hotf() {
        load();
        return HOTF;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;

        try (InputStream stream = PerkRegistry.class.getResourceAsStream("/assets/teslamaps/pv/perks.json")) {
            if (stream == null) {
                TeslaMaps.LOGGER.warn("perks.json is missing from the jar");
                return;
            }
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            read(root, "hotm", HOTM);
            read(root, "hotf", HOTF);
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to read perks.json", e);
        }
    }

    private static void read(JsonObject root, String key, List<Perk> out) {
        if (!root.has(key)) return;
        for (JsonElement element : root.getAsJsonArray(key)) {
            JsonObject perk = element.getAsJsonObject();

            Map<String, String> reward = new HashMap<>();
            if (perk.has("reward") && perk.get("reward").isJsonObject()) {
                JsonObject rewards = perk.getAsJsonObject("reward");
                for (String rewardKey : rewards.keySet()) reward.put(rewardKey, rewards.get(rewardKey).getAsString());
            }

            List<String> tooltip = new ArrayList<>();
            if (perk.has("tooltip") && perk.get("tooltip").isJsonArray()) {
                for (JsonElement line : perk.getAsJsonArray("tooltip")) tooltip.add(line.getAsString());
            }

            out.add(new Perk(
                    string(perk, "type"),
                    string(perk, "id"),
                    perk.has("x") ? perk.get("x").getAsInt() : 0,
                    perk.has("y") ? perk.get("y").getAsInt() : 0,
                    perk.has("max_level") ? perk.get("max_level").getAsInt() : null,
                    string(perk, "costKind"),
                    string(perk, "cost"),
                    reward,
                    string(perk, "name"),
                    tooltip));
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    public static int tierNumber(String name) {
        if (name == null) return 0;
        StringBuilder digits = new StringBuilder();
        for (char c : name.toCharArray()) if (Character.isDigit(c)) digits.append(c);
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The node tooltip the game shows: name, level, effect at that level, powder spent, state. */
    public static List<String> tooltip(Perk perk, int level, int treeLevel) {
        if ("SPACER".equals(perk.type())) return List.of();
        if ("TIER".equals(perk.type())) return tierTooltip(perk, treeLevel);

        List<String> out = new ArrayList<>();
        boolean locked = level < 0;
        boolean maxed = perk.max() != null && level >= perk.max();

        out.add((locked ? "§c" : "§a") + (perk.name() == null ? "" : perk.name()));

        if (perk.max() != null) {
            out.add(maxed ? "§7Level §f" + level : "§7Level §f" + Math.max(level, 0) + "§7/§8" + perk.max());
            out.add("");
        }

        int effectLevel = level > 0 ? level : 1;
        Map<String, Double> vars = new HashMap<>();
        vars.put("level", (double) effectLevel);
        vars.put("effectiveLevel", (double) effectLevel);
        vars.put("hotmLevel", (double) treeLevel);

        for (String line : perk.tooltip()) {
            String text = line;
            for (Map.Entry<String, String> entry : perk.reward().entrySet()) {
                text = text.replace("%" + entry.getKey() + "%", number(Eval.eval(entry.getValue(), vars)));
            }
            out.add(tags(text));
        }

        if ("CORE".equals(perk.type())) {
            return out;
        }
        if (locked) {
            out.add("");
            out.add("§c§lLOCKED");
            return out;
        }
        if ("ABILITY".equals(perk.type())) {
            out.add("");
            out.add("§a§lUNLOCKED");
            return out;
        }

        out.add("");
        if (perk.max() != null && perk.cost() != null && perk.costKind() != null) {
            out.addAll(powderSpent(perk, level));
        }
        out.add("§a§lENABLED");
        return out;
    }

    private static List<String> tierTooltip(Perk perk, int treeLevel) {
        int tier = tierNumber(perk.name());
        boolean unlocked = treeLevel >= tier;
        boolean unlocking = !unlocked && treeLevel + 1 >= tier;
        String color = unlocked ? "§a" : unlocking ? "§e" : "§c";
        String state = unlocked ? "§a§lUNLOCKED" : unlocking ? "§e§lUNLOCKING" : "§c§lRequires Tier " + tier;
        return List.of(color + (perk.name() == null ? "Tier " + tier : perk.name()), "", state);
    }

    private static List<String> powderSpent(Perk perk, int level) {
        String kind = perk.costKind();
        String formula = perk.cost();
        if (kind == null || formula == null) return List.of();

        long spent = sum(formula, level);
        long required = sum(formula, perk.max() == null ? level : perk.max());
        String name = kind.charAt(0) + kind.substring(1).toLowerCase();
        String percent = required > 0 && spent < required ? " §e(" + (spent * 100 / required) + "%)" : "";

        return List.of("§7Powder Spent",
                powderColor(kind) + name + "§7: §f" + String.format("%,d", spent)
                        + "§7/§8" + String.format("%,d", required) + percent);
    }

    private static long sum(String formula, int toLevel) {
        long total = 0;
        for (int i = 1; i <= toLevel; i++) {
            total += (long) Math.floor(Eval.eval(formula, Map.of("level", (double) i)));
        }
        return total;
    }

    private static String powderColor(String kind) {
        return switch (kind) {
            case "MITHRIL" -> "§2";
            case "GEMSTONE" -> "§d";
            case "GLACITE" -> "§b";
            default -> "§7";
        };
    }

    private static String number(double value) {
        return value == Math.floor(value) ? String.format("%,d", (long) value) : String.format("%.1f", value);
    }

    private static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("black", "§0"), Map.entry("dark_blue", "§1"), Map.entry("dark_green", "§2"),
            Map.entry("dark_aqua", "§3"), Map.entry("dark_red", "§4"), Map.entry("dark_purple", "§5"),
            Map.entry("gold", "§6"), Map.entry("gray", "§7"), Map.entry("dark_gray", "§8"),
            Map.entry("blue", "§9"), Map.entry("green", "§a"), Map.entry("aqua", "§b"),
            Map.entry("red", "§c"), Map.entry("light_purple", "§d"), Map.entry("purple", "§d"),
            Map.entry("yellow", "§e"), Map.entry("white", "§f"), Map.entry("bold", "§l"),
            Map.entry("b", "§l"), Map.entry("italic", "§o"), Map.entry("i", "§o"),
            Map.entry("underline", "§n"), Map.entry("strikethrough", "§m"),
            Map.entry("obfuscated", "§k"), Map.entry("reset", "§r"));

    private static final Pattern TAG_PATTERN = Pattern.compile("</?([a-z_]+)>");

    /** The data uses minimessage style tags, the game wants paragraph codes. */
    private static String tags(String text) {
        Matcher matcher = TAG_PATTERN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(TAGS.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
