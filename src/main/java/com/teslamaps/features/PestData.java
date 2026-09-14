package com.teslamaps.features;

import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.TabListUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything the game already tells us about garden pests, read straight from
 * the tab list and scoreboard so no other mod is needed for it.
 */
public class PestData {

    // " Cooldown: 1m 20s" / " Cooldown: 12s" / " Cooldown: READY" / "MAX PESTS"
    private static final Pattern COOLDOWN = Pattern.compile(
            "\\sCooldown: (?<time>\\d{1,2}[ms](?: \\d{1,2}s?)?)?(?<ready>READY)?(?<maxPests>MAX PESTS)?.*");
    private static final Pattern ALIVE = Pattern.compile("\\sAlive: (?<alive>\\d+)");
    private static final Pattern INFESTED_PLOTS = Pattern.compile("\\sPlots: (?<plots>.*)");
    // Scoreboard line for the plot you are standing on. The pest icon is a
    // private-use glyph, not the wrench sign, so anything is allowed between the
    // plot name and the count.
    private static final Pattern PLOT_PESTS = Pattern.compile(
            "\\s*Plot - (?<plot>.+?)\\s+\\S*\\s*x(?<pests>\\d+)");
    // Same line without the pest part, for "which plot am I on".
    private static final Pattern CURRENT_PLOT = Pattern.compile(
            "\\s*Plot - (?<plot>.+?)(?:\\s+\\S*\\s*x\\d+)?\\s*$");
    private static final Pattern SPRAY = Pattern.compile(
            "\\s*Spray: (?<spray>.+?) \\((?<time>[^)]+)\\)");
    private static final Pattern BONUS_PEST_CHANCE = Pattern.compile("\\s*Bonus Pest Chance: (?<value>.+)");
    private static final Pattern PEST_TRAPS = Pattern.compile("\\s*Pest Traps: (?<traps>\\d+/\\d+)");
    private static final Pattern REPELLENT = Pattern.compile("\\s*Repellent: (?<repellent>.+)");
    // " Bonus: +400<fortune icon> 22m" — the pest fortune buff from Philip.
    private static final Pattern PHILIP_BONUS = Pattern.compile(
            "\\s*Bonus: \\+(?<value>[\\d,]+)\\S*\\s+(?<time>.+)");

    private static final Pattern TIME_PART = Pattern.compile("(\\d{1,3})([hms])");

    private static long lastSpawnMillis = 0L;

    /** Call when a pest spawn is announced; starts the custom countdown. */
    public static void onPestSpawn() {
        lastSpawnMillis = System.currentTimeMillis();
    }

    /** Seconds since the last pest spawn we saw, or -1 if none this session. */
    public static int secondsSinceLastSpawn() {
        if (lastSpawnMillis == 0L) return -1;
        return (int) ((System.currentTimeMillis() - lastSpawnMillis) / 1000L);
    }

    /**
     * Seconds left, honouring a configured cooldown length.
     * <p>
     * The tab list timer reflects the cooldown the server started the cycle with,
     * so swapping cooldown-reduction armour mid-cycle makes it wrong. With a
     * custom length set we count from the last spawn ourselves instead.
     */
    public static int effectiveCooldownSeconds() {
        com.teslamaps.config.TeslaMapsConfig config = com.teslamaps.config.TeslaMapsConfig.get();
        if (!config.pestCustomCooldown || lastSpawnMillis == 0L) return cooldownSeconds();

        long elapsed = (System.currentTimeMillis() - lastSpawnMillis) / 1000L;
        return (int) Math.max(0, config.pestCustomCooldownSeconds - elapsed);
    }

    /** True when spawns should be possible, by whichever clock is in use. */
    public static boolean effectiveReady() {
        com.teslamaps.config.TeslaMapsConfig config = com.teslamaps.config.TeslaMapsConfig.get();
        if (!config.pestCustomCooldown || lastSpawnMillis == 0L) return ready();
        return effectiveCooldownSeconds() <= 0;
    }

    /** True while the custom clock is the one being shown. */
    public static boolean usingCustomCooldown() {
        return com.teslamaps.config.TeslaMapsConfig.get().pestCustomCooldown && lastSpawnMillis != 0L;
    }

    private static int cooldownSeconds = -1;
    private static boolean ready = false;
    private static boolean maxPests = false;
    private static int alive = 0;
    private static String spray = null;
    private static int spraySeconds = 0;
    private static int bonusPestChance = 0;
    private static String pestTraps = null;
    private static String repellent = null;
    private static int philipBonus = 0;
    private static int philipSeconds = -1;
    private static long lastParse = 0L;

    /** Farming fortune from the active Philip buff, or 0. */
    public static int philipBonus() { parse(); return philipBonus; }

    /** Seconds left on the Philip buff, or -1 when there is none. */
    public static int philipSeconds() { parse(); return philipSeconds; }

    /** True while the Philip buff is running. */
    public static boolean hasPhilipBonus() { parse(); return philipSeconds >= 0; }

    /** Active spray name, or null. */
    public static String spray() { parse(); return spray; }

    /** Seconds left on the active spray. */
    public static int spraySeconds() { parse(); return spraySeconds; }

    /** Bonus pest chance in percent. */
    public static int bonusPestChance() { parse(); return bonusPestChance; }

    /** Pest traps as "3/3", or null. */
    public static String pestTraps() { parse(); return pestTraps; }

    /** Active repellent, or null when "None". */
    public static String repellent() { parse(); return repellent; }

    /** Seconds until pests can spawn again, or -1 when unknown. */
    public static int cooldownSeconds() { parse(); return cooldownSeconds; }

    /** True when the game reports the spawn cooldown as READY. */
    public static boolean ready() { parse(); return ready; }

    /** True when the plot is already at the pest cap. */
    public static boolean maxPests() { parse(); return maxPests; }

    /** Pests currently alive across the garden. */
    public static int alive() { parse(); return alive; }

    private static void parse() {
        // The tab list only changes a few times a second; parsing it per frame
        // would be wasted work in a render path.
        long now = System.currentTimeMillis();
        if (now - lastParse < 250) return;
        lastParse = now;

        cooldownSeconds = -1;
        ready = false;
        maxPests = false;
        alive = 0;
        spray = null;
        spraySeconds = 0;
        bonusPestChance = 0;
        pestTraps = null;
        repellent = null;
        philipBonus = 0;
        philipSeconds = -1;

        if (!SkyblockUtils.isInGarden()) return;

        for (String raw : TabListUtils.getTabListLines()) {
            String line = strip(raw);

            Matcher cooldown = COOLDOWN.matcher(line);
            if (cooldown.matches()) {
                if (cooldown.group("ready") != null) {
                    ready = true;
                    cooldownSeconds = 0;
                } else if (cooldown.group("time") != null) {
                    cooldownSeconds = parseTime(cooldown.group("time"));
                }
                // The group only catches it when it stands alone; a combined
                // "1m MAX PESTS" would otherwise fall into the trailing .*
                if (cooldown.group("maxPests") != null || line.contains("MAX PESTS")) maxPests = true;
                continue;
            }

            Matcher aliveMatcher = ALIVE.matcher(line);
            if (aliveMatcher.find()) {
                alive = Integer.parseInt(aliveMatcher.group("alive"));
                continue;
            }

            Matcher sprayMatcher = SPRAY.matcher(line);
            if (sprayMatcher.find()) {
                spray = sprayMatcher.group("spray").trim();
                spraySeconds = parseTime(sprayMatcher.group("time"));
                continue;
            }

            Matcher bpcMatcher = BONUS_PEST_CHANCE.matcher(line);
            if (bpcMatcher.find()) {
                bonusPestChance = parseNumber(bpcMatcher.group("value"));
                continue;
            }

            Matcher trapMatcher = PEST_TRAPS.matcher(line);
            if (trapMatcher.find()) {
                pestTraps = trapMatcher.group("traps");
                continue;
            }

            Matcher repellentMatcher = REPELLENT.matcher(line);
            if (repellentMatcher.find()) {
                String value = repellentMatcher.group("repellent").trim();
                repellent = value.equalsIgnoreCase("None") ? null : value;
                continue;
            }

            Matcher philipMatcher = PHILIP_BONUS.matcher(line);
            if (philipMatcher.find()) {
                philipBonus = parseNumber(philipMatcher.group("value"));
                philipSeconds = parseTime(philipMatcher.group("time"));
            }
        }
    }

    /** True once the tab list actually carries the pest block. */
    public static boolean hasPestSection() {
        parse();
        return ready || maxPests || cooldownSeconds >= 0;
    }

    /** Plot names the tab list currently reports as infested. */
    public static List<String> infestedPlots() {
        List<String> plots = new ArrayList<>();
        for (String raw : TabListUtils.getTabListLines()) {
            Matcher matcher = INFESTED_PLOTS.matcher(strip(raw));
            if (!matcher.find()) continue;
            for (String part : matcher.group("plots").split(",")) {
                String name = part.strip();
                if (!name.isEmpty()) plots.add(name);
            }
            break;
        }
        return plots;
    }

    /** Pests on the plot the player is standing on, or 0. */
    public static int pestsOnCurrentPlot() {
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            Matcher matcher = PLOT_PESTS.matcher(strip(raw));
            if (matcher.find()) return Integer.parseInt(matcher.group("pests"));
        }
        return 0;
    }

    /** Plot the player is standing on, or null. */
    public static String currentPlot() {
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            Matcher matcher = CURRENT_PLOT.matcher(strip(raw));
            if (matcher.find()) return matcher.group("plot").trim();
        }
        return null;
    }

    /** "1m 20s" -> 80. */
    private static int parseTime(String text) {
        int seconds = 0;
        Matcher matcher = TIME_PART.matcher(text);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            seconds += switch (matcher.group(2)) {
                case "h" -> value * 3600;
                case "m" -> value * 60;
                default -> value;
            };
        }
        return seconds;
    }

    /**
     * Digits out of a widget value. Hypixel prefixes several of these with a
     * private-use icon glyph, so anything non-numeric is dropped.
     */
    private static int parseNumber(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String strip(String text) {
        return text == null ? "" : text.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }
}
