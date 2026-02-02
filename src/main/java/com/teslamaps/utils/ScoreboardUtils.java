package com.teslamaps.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class ScoreboardUtils {
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("\u00A7[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);

    public static List<String> getScoreboardLines() {
        List<String> lines = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null) return lines;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        if (objective == null) return lines;

        Collection<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective);

        for (ScoreboardEntry entry : entries) {
            String name = entry.owner();
            // Get display name if available through team
            Team team = scoreboard.getScoreHolderTeam(name);
            if (team != null) {
                String prefix = team.getPrefix().getString();
                String suffix = team.getSuffix().getString();
                name = prefix + name + suffix;
            }
            lines.add(name);
        }

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
}
