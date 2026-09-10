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
    private static final Pattern PUZZLE_STATE_PATTERN = Pattern.compile(".+?:\\s*\\[([✔✖✦])]");
    private static final Pattern STRIP_PATTERN = Pattern.compile("§.");

    private static String strip(String line) {
        return STRIP_PATTERN.matcher(line).replaceAll("");
    }

    private static int debugCounter = 0;

    private static List<String> cached = new ArrayList<>();
    private static long cachedTime = -1;

    // getString() over ~80 tab entries is expensive; render paths call this several times a frame
    public static List<String> getTabListLines() {
        long now = System.currentTimeMillis();
        if (now - cachedTime < 50) return cached;
        cachedTime = now;

        List<String> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        if (mc.getConnection() == null) return cached = lines;

        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            if (entry.getTabListDisplayName() != null) {
                lines.add(entry.getTabListDisplayName().getString());
            }
        }

        return cached = lines;
    }

    // getOnlinePlayers() is unordered; Hypixel lays its tab out in the order vanilla renders,
    // so sections only stay contiguous once the vanilla comparator has been applied.
    private static final java.util.Comparator<PlayerInfo> TAB_ORDER =
            java.util.Comparator.comparingInt((PlayerInfo p) -> -p.getTabListOrder())
                    .thenComparingInt(p -> p.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR ? 1 : 0)
                    .thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName())
                    .thenComparing(p -> p.getProfile().name(), String::compareToIgnoreCase);

    private static List<String> cachedOrdered = new ArrayList<>();
    private static long cachedOrderedTime = -1;

    /** Tab lines in the order they are drawn, so a section header still owns the lines below it. */
    public static List<String> getOrderedLines() {
        long now = System.currentTimeMillis();
        if (now - cachedOrderedTime < 250) return cachedOrdered;
        cachedOrderedTime = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return cachedOrdered = new ArrayList<>();

        List<PlayerInfo> entries = new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
        entries.sort(TAB_ORDER);
        List<String> lines = new ArrayList<>(entries.size());
        for (PlayerInfo entry : entries) {
            lines.add(entry.getTabListDisplayName() == null ? "" : entry.getTabListDisplayName().getString());
        }
        return cachedOrdered = lines;
    }

    public static double getSecretsPercentage() {
        return getSecretsPercentage(getTabListLines());
    }

    public static double getSecretsPercentage(List<String> tabLines) {
        for (String line : tabLines) {
            String clean = strip(line);
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
        return getSecretsFound(getTabListLines());
    }

    public static int getSecretsFound(List<String> tabLines) {
        for (String line : tabLines) {
            String clean = strip(line);
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
        return getCryptsFound(getTabListLines());
    }

    public static int getCryptsFound(List<String> tabLines) {
        for (String line : tabLines) {
            String clean = strip(line);
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
            String clean = strip(line);
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
            String clean = strip(line);
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
            String clean = strip(line);
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
            String clean = strip(line);
            Matcher matcher = PUZZLE_STATE_PATTERN.matcher(clean);
            if (matcher.find()) {
                String state = matcher.group(1);
                if (state.equals("✖") || state.equals("✦")) {
                    failed++;
                }
            }
        }
        return failed;
    }
}
