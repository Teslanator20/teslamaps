package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Ported from Skyblocker's profileviewer2 AccessoryPowerCalculator + TuningSlotWidget
public final class PowerStats {
    private static final String[] ORDER = {"health", "defence", "true_defence", "strength", "critical_chance",
            "critical_damage", "attack_speed", "ferocity", "intelligence", "ability_damage", "walk_speed", "combat_wisdom"};

    private static JsonObject powers;

    private PowerStats() {}

    private static JsonObject powers() {
        if (powers != null) return powers;
        try (InputStream in = PowerStats.class.getResourceAsStream("/assets/teslamaps/data/magical_powers.json")) {
            powers = in == null ? new JsonObject()
                    : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("magical_powers.json failed: {}", e.toString());
            powers = new JsonObject();
        }
        return powers;
    }

    private static double multiplier(int mp) {
        return 29.97 * Math.pow(Math.log(0.0019 * mp + 1), 1.2);
    }

    /** Tooltip lines: power stone stats for the MP, then the active tuning slot. */
    public static List<Component> tooltip(JsonObject bag) {
        List<String> lines = new ArrayList<>();
        int mp = num(bag, "highest_magical_power");
        String selected = bag != null && bag.has("selected_power") && bag.get("selected_power").isJsonPrimitive()
                ? bag.get("selected_power").getAsString() : "";
        lines.add("§6Magical Power: §f" + mp);
        lines.add("§aPower: §f" + (selected.isEmpty() ? "None" : com.teslamaps.profileviewer.ui.Kit.prettify(selected)));
        lines.add("");
        lines.add("§f§lPower Stats");

        JsonObject power = powers().has(selected) && powers().get(selected).isJsonObject()
                ? powers().getAsJsonObject(selected) : null;
        JsonObject stats = obj(power, "stats");
        JsonObject bonus = obj(power, "bonus");
        double mult = multiplier(mp);
        int before = lines.size();
        for (String stat : ORDER) {
            boolean has = false;
            double amount = 0;
            if (stats != null && stats.has(stat)) { amount += dbl(stats.get(stat)) * mult; has = true; }
            if (bonus != null && bonus.has(stat)) { amount += dbl(bonus.get(stat)); has = true; }
            if (has) lines.add(format(stat, amount));
        }
        if (lines.size() == before) lines.add("§7Unknown stats");

        JsonObject slot = obj(obj(bag, "tuning"), "slot_0");
        lines.add("");
        lines.add("§f§lTuning Stats");
        before = lines.size();
        int points = 0;
        if (slot != null) {
            String[][] keys = {{"health", "health", "5"}, {"defense", "defence", "1"}, {"strength", "strength", "1"},
                    {"critical_chance", "critical_chance", "0.2"}, {"critical_damage", "critical_damage", "1"},
                    {"attack_speed", "attack_speed", "0.3"}, {"intelligence", "intelligence", "2"},
                    {"walk_speed", "walk_speed", "1.5"}};
            for (String[] k : keys) {
                int n = num(slot, k[0]);
                points += Math.max(0, n);
                if (n > 0) lines.add(format(k[1], n * Double.parseDouble(k[2])) + " §8(" + n + ")");
            }
        }
        if (lines.size() == before) lines.add("§7§oUnconfigured");
        else lines.add("§7Points: §f" + points);

        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(LegacyText.parse(line));
        return out;
    }

    /** Spent tuning points of the active slot, 0 when none. */
    public static int tuningPoints(JsonObject bag) {
        JsonObject slot = obj(obj(bag, "tuning"), "slot_0");
        if (slot == null) return 0;
        int total = 0;
        for (var e : slot.entrySet()) {
            if (e.getKey().equals("purchase_ts")) continue;
            total += Math.max(0, (int) dbl(e.getValue()));
        }
        return total;
    }

    private static String format(String stat, double amount) {
        String[] look = switch (stat) {
            case "health" -> new String[]{"§c", "❤ Health"};
            case "defence" -> new String[]{"§a", "❈ Defense"};
            case "true_defence" -> new String[]{"§f", "❂ True Defense"};
            case "strength" -> new String[]{"§c", "❁ Strength"};
            case "critical_chance" -> new String[]{"§9", "☣ Crit Chance"};
            case "critical_damage" -> new String[]{"§9", "☠ Crit Damage"};
            case "attack_speed" -> new String[]{"§e", "⚔ Attack Speed"};
            case "ferocity" -> new String[]{"§c", "⫽ Ferocity"};
            case "intelligence" -> new String[]{"§b", "✎ Intelligence"};
            case "ability_damage" -> new String[]{"§c", "๑ Ability Damage"};
            case "walk_speed" -> new String[]{"§f", "✦ Speed"};
            case "combat_wisdom" -> new String[]{"§3", "☯ Combat Wisdom"};
            default -> new String[]{"§7", com.teslamaps.profileviewer.ui.Kit.prettify(stat)};
        };
        String value = amount == Math.rint(amount) ? String.valueOf((long) amount)
                : String.format(Locale.ROOT, "%.1f", amount);
        return look[0] + (amount >= 0 ? "+" : "") + value + " " + look[1];
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static double dbl(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : 0;
    }

    private static int num(JsonObject o, String key) {
        return o != null && o.has(key) ? (int) dbl(o.get(key)) : 0;
    }
}
