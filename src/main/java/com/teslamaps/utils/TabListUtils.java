package com.teslamaps.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for parsing the tab list (player list) in dungeons.
 * Based on Skyblocker's implementation.
 */
public class TabListUtils {
    // Secrets Found: 40.5% (percentage, not count!)
    private static final Pattern SECRETS_PERCENT_PATTERN = Pattern.compile("Secrets Found:\\s*(\\d+\\.?\\d*)%");
    // Secrets Found: X (actual count)
    private static final Pattern SECRETS_COUNT_PATTERN = Pattern.compile("Secrets Found:\\s*(\\d+)(?!%)");
    // Crypts: X
    private static final Pattern CRYPTS_PATTERN = Pattern.compile("Crypts:\\s*(\\d+)");
    // Completed Rooms: X
    private static final Pattern COMPLETED_ROOMS_PATTERN = Pattern.compile("Completed Rooms:\\s*(\\d+)");
    // Deaths: X
    private static final Pattern DEATHS_PATTERN = Pattern.compile("Deaths:\\s*(\\d+)");
    // Puzzles: (X)
    private static final Pattern PUZZLE_COUNT_PATTERN = Pattern.compile("Puzzles:\\s*\\((\\d+)\\)");
    // Puzzle state: [✔] or [✖] or [✦]
    private static final Pattern PUZZLE_STATE_PATTERN = Pattern.compile(".+?(?=:):\\s*\\[([✔✖✦])]");

    private static int debugCounter = 0;

    /**
     * Get all lines from the tab list.
     */
    public static List<String> getTabListLines() {
        List<String> lines = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getNetworkHandler() == null) return lines;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getDisplayName() != null) {
                lines.add(entry.getDisplayName().getString());
            }
        }

        return lines;
    }

    /**
     * Get the secrets found PERCENTAGE from tab list.
     * @return secrets percentage (0-100), or -1 if not found
     */
    public static double getSecretsPercentage() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = SECRETS_PERCENT_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Get the actual number of secrets found from tab list.
     * @return secrets found count, or -1 if not found
     */
    public static int getSecretsFound() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = SECRETS_COUNT_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Get the number of crypts found from tab list.
     * @return crypts found, or -1 if not found
     */
    public static int getCryptsFound() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = CRYPTS_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Get the number of completed rooms from tab list.
     * @return completed rooms, or -1 if not found
     */
    public static int getCompletedRooms() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = COMPLETED_ROOMS_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Get the number of deaths from tab list.
     * @return deaths, or -1 if not found
     */
    public static int getDeaths() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = DEATHS_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Get the puzzle count from tab list.
     * @return puzzle count, or 0 if not found
     */
    public static int getPuzzleCount() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = PUZZLE_COUNT_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Get the number of failed puzzles (✖) from tab list.
     * Note: [✦] (not started) does NOT count as failed.
     * @return failed puzzle count
     */
    public static int getIncompletePuzzles() {
        int failed = 0;
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = PUZZLE_STATE_PATTERN.matcher(clean);
            if (matcher.find()) {
                String state = matcher.group(1);
                // Only count [✖] as failed, not [✦] (not started)
                if (state.equals("✖")) {
                    failed++;
                }
            }
        }
        return failed;
    }
}
