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

import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScoreboardUtils {
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00A7[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEARED_PATTERN = Pattern.compile("Cleared:\\s*(\\d+)%");

    public static List<String> getScoreboardLines() {
        List<String> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) return lines;

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        if (objective == null) return lines;

        for (ScoreHolder scoreHolder : scoreboard.getTrackedPlayers()) {
            var scoresMap = scoreboard.listPlayerScores(scoreHolder);
            if (!scoresMap.containsKey(objective)) continue;

            PlayerTeam team = scoreboard.getPlayersTeam(scoreHolder.getScoreboardName());
            if (team != null) {
                String prefix = team.getPlayerPrefix().getString();
                String suffix = team.getPlayerSuffix().getString();
                String line = prefix + suffix;
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }

        lines.add(objective.getDisplayName().getString());
        java.util.Collections.reverse(lines);

        return lines;
    }

    public static String cleanLine(String line) {
        return COLOR_CODE_PATTERN.matcher(line).replaceAll("").trim();
    }

    public static String getScoreboardTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

        if (objective == null) return "";

        return objective.getDisplayName().getString();
    }

    public static double getClearPercentage() {
        for (String line : getScoreboardLines()) {
            String clean = cleanLine(line);
            Matcher matcher = CLEARED_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1)) / 100.0;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
