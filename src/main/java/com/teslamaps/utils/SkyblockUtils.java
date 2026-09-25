package com.teslamaps.utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public class SkyblockUtils {
    private static final Pattern HYPIXEL_PATTERN = Pattern.compile(".*\\.hypixel\\.net", Pattern.CASE_INSENSITIVE);
    private static final Pattern STRIP_PATTERN = Pattern.compile("§.");

    public static boolean isOnHypixel() {
        if (HypixelModApi.live()) return true;
        Minecraft mc = Minecraft.getInstance();
        ServerData serverInfo = mc.getCurrentServer();
        if (serverInfo == null) return false;

        String address = serverInfo.ip.toLowerCase();
        return HYPIXEL_PATTERN.matcher(address).matches() || address.contains("hypixel");
    }

    private static boolean sbCached;
    private static long sbCachedAt;

    /** isInSkyblock, re-checked at most once a second; for per-frame / per-slot paths. */
    public static boolean inSkyblockCached() {
        long now = System.currentTimeMillis();
        if (now - sbCachedAt > 1000) {
            sbCachedAt = now;
            sbCached = isInSkyblock();
        }
        return sbCached;
    }

    public static boolean isInSkyblock() {
        if (areaOverride != null) return true;
        if (!isOnHypixel()) return false;
        String type = HypixelModApi.serverType();
        if (type != null) return type.equals("SKYBLOCK");

        List<String> scoreboard = ScoreboardUtils.getScoreboardLines();
        for (String line : scoreboard) {
            if (line.contains("SKYBLOCK") || line.contains("\u23E3")) { // \u23E3 is the Skyblock icon
                return true;
            }
        }
        return false;
    }

    public static boolean isInDungeonArea() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        double x = mc.player.getX();
        double z = mc.player.getZ();

        return x >= -200 && x <= -10 && z >= -200 && z <= -10;
    }

    private static final Pattern AREA_PATTERN = Pattern.compile("(?:Area|Dungeon):\\s*(.+)");
    private static String area = "";
    private static long areaTime = 0;

    /** /tmap debug island: pretend to be on this island, null = real detection. */
    public static String areaOverride = null;

    // tab list carries "Area: <island>" (or "Dungeon: The Catacombs")
    public static String getArea() {
        if (areaOverride != null) return areaOverride;
        long now = System.currentTimeMillis();
        if (now - areaTime < 1000) return area;
        areaTime = now;
        if (!isOnHypixel()) return area = "";
        for (String line : TabListUtils.getTabListLines()) {
            Matcher m = AREA_PATTERN.matcher(STRIP_PATTERN.matcher(line).replaceAll("").trim());
            if (m.find()) return area = m.group(1).trim();
        }
        return area = "";
    }

    public static boolean isInArea(String name) {
        return getArea().equalsIgnoreCase(name);
    }

    private static boolean inGarden = false;
    private static long gardenTime = 0;

    // several HUD overlays ask this every frame, so it gets the same throttle as getArea
    public static boolean isInGarden() {
        long now = System.currentTimeMillis();
        if (now - gardenTime < 500) return inGarden;
        gardenTime = now;
        return inGarden = computeInGarden();
    }

    private static boolean computeInGarden() {
        if (areaOverride != null) return areaOverride.toLowerCase().contains("garden");
        if (!isOnHypixel()) return false;

        // The scoreboard shows the plot name while standing on a plot, so the
        // tab list area is the reliable signal there.
        if (getArea().toLowerCase().contains("garden")) return true;

        List<String> scoreboard = ScoreboardUtils.getScoreboardLines();
        for (String line : scoreboard) {
            String stripped = STRIP_PATTERN.matcher(line).replaceAll("").toLowerCase();
            if (stripped.contains("garden")) {
                return true;
            }
        }
        return false;
    }
}
