package com.teslamaps.utils;

import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public class SkyblockUtils {
    private static final Pattern HYPIXEL_PATTERN = Pattern.compile(".*\\.hypixel\\.net", Pattern.CASE_INSENSITIVE);

    public static boolean isOnHypixel() {
        Minecraft mc = Minecraft.getInstance();
        ServerData serverInfo = mc.getCurrentServer();
        if (serverInfo == null) return false;

        String address = serverInfo.ip.toLowerCase();
        return HYPIXEL_PATTERN.matcher(address).matches() || address.contains("hypixel");
    }

    public static boolean isInSkyblock() {
        if (!isOnHypixel()) return false;

        List<String> scoreboard = ScoreboardUtils.getScoreboardLines();
        for (String line : scoreboard) {
            // Check for Skyblock indicators
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

        // Dungeon bounds: -200,-200 to -10,-10
        return x >= -200 && x <= -10 && z >= -200 && z <= -10;
    }

    /**
     * Check if player is in the Garden island.
     */
    public static boolean isInGarden() {
        if (!isOnHypixel()) return false;

        List<String> scoreboard = ScoreboardUtils.getScoreboardLines();
        for (String line : scoreboard) {
            // Garden shows as "⏣ The Garden" or similar on scoreboard
            String stripped = line.replaceAll("§.", "").toLowerCase();
            if (stripped.contains("garden")) {
                return true;
            }
        }
        return false;
    }
}
