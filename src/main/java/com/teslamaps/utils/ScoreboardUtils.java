package com.teslamaps.utils;

import com.teslamaps.TeslaMaps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.*;
import net.minecraft.scoreboard.ScoreHolder;

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
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null) return lines;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        if (objective == null) return lines;

        // Get all known score holders and filter to those with scores for sidebar objective
        for (ScoreHolder scoreHolder : scoreboard.getKnownScoreHolders()) {
            // Check if this score holder has scores for the sidebar objective
            var scoresMap = scoreboard.getScoreHolderObjectives(scoreHolder);
            if (!scoresMap.containsKey(objective)) continue;

            // Get team prefix/suffix for display text
            Team team = scoreboard.getScoreHolderTeam(scoreHolder.getNameForScoreboard());
            if (team != null) {
                String prefix = team.getPrefix().getString();
                String suffix = team.getSuffix().getString();
                String line = prefix + suffix;
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }

        // Add objective title and reverse to get correct order
        lines.add(objective.getDisplayName().getString());
        java.util.Collections.reverse(lines);

        return lines;
    }

    public static String cleanLine(String line) {
        // Remove color codes and special characters
        return COLOR_CODE_PATTERN.matcher(line).replaceAll("").trim();
    }

    public static String getScoreboardTitle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "";

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        if (objective == null) return "";

        return objective.getDisplayName().getString();
    }

    /**
     * Get dungeon clear percentage from scoreboard.
     * @return clear percentage (0.0 - 1.0), or 0 if not found
     */
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
