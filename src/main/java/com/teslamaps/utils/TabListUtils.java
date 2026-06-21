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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class TabListUtils {
    private static final Pattern SECRETS_PERCENT_PATTERN = Pattern.compile("Secrets Found:\\s*(\\d+\\.?\\d*)%");
    private static final Pattern SECRETS_COUNT_PATTERN = Pattern.compile("Secrets Found:\\s*(\\d+)(?![\\d.%])");
    private static final Pattern CRYPTS_PATTERN = Pattern.compile("Crypts:\\s*(\\d+)");
    private static final Pattern COMPLETED_ROOMS_PATTERN = Pattern.compile("Completed Rooms:\\s*(\\d+)");
    private static final Pattern DEATHS_PATTERN = Pattern.compile("Deaths:\\s*(\\d+)");
    private static final Pattern PUZZLE_COUNT_PATTERN = Pattern.compile("Puzzles:\\s*\\((\\d+)\\)");
    private static final Pattern PUZZLE_STATE_PATTERN = Pattern.compile(".+?(?=:):\\s*\\[([])]");

    private static int debugCounter = 0;

    public static List<String> getTabListLines() {
        List<String> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        if (mc.getConnection() == null) return lines;

        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            if (entry.getTabListDisplayName() != null) {
                lines.add(entry.getTabListDisplayName().getString());
            }
        }

        return lines;
    }

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

    public static int getIncompletePuzzles() {
        int failed = 0;
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = PUZZLE_STATE_PATTERN.matcher(clean);
            if (matcher.find()) {
                String state = matcher.group(1);
                if (state.equals("")) {
                    failed++;
                }
            }
        }
        return failed;
    }
}
