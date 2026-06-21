/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

    public static boolean isInGarden() {
        if (!isOnHypixel()) return false;

        List<String> scoreboard = ScoreboardUtils.getScoreboardLines();
        for (String line : scoreboard) {
            String stripped = line.replaceAll("§.", "").toLowerCase();
            if (stripped.contains("garden")) {
                return true;
            }
        }
        return false;
    }
}
