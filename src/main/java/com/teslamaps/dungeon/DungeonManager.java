package com.teslamaps.dungeon;

import com.teslamaps.TeslaMaps;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.PlayerHeadRenderer;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.scanner.RoomScanner;
import com.teslamaps.scanner.SecretTracker;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonManager {
    private static DungeonState currentState = DungeonState.NOT_IN_DUNGEON;
    private static DungeonFloor currentFloor = DungeonFloor.UNKNOWN;
    private static final ComponentGrid grid = new ComponentGrid();
    private static long dungeonStartTime = 0;
    private static int tickCounter = 0;

    private static final Pattern FLOOR_PATTERN = Pattern.compile("([FM])(\\d+)");
    private static final Pattern CATACOMBS_PATTERN = Pattern.compile("The Catacombs \\(([FM]\\d+)\\)");

    public static void tick() {
        tickCounter++;

        // Check state every 20 ticks (1 second)
        if (tickCounter % 20 == 0) {
            updateDungeonState();
        }
    }

    private static void updateDungeonState() {
        DungeonState newState = detectCurrentState();

        if (newState != currentState) {
            DungeonState oldState = currentState;
            currentState = newState;
            onStateChange(oldState, newState);
        }
    }

    private static DungeonState detectCurrentState() {
        if (!SkyblockUtils.isOnHypixel()) {
            return DungeonState.NOT_IN_DUNGEON;
        }

        List<String> scoreboard = ScoreboardUtils.getScoreboardLines();
        String title = ScoreboardUtils.getScoreboardTitle();

        // Check scoreboard title for dungeon indicators
        String titleUpper = title.toUpperCase();
        boolean isDungeonTitle = titleUpper.contains("DUNGEON") ||
                titleUpper.contains("CATACOMBS") ||
                titleUpper.contains("THE CATAC");  // Sometimes shows as "The Catac..."

        // Also check scoreboard lines for dungeon indicators
        boolean hasCleared = false;
        for (String line : scoreboard) {
            String clean = ScoreboardUtils.cleanLine(line).toLowerCase();
            if (clean.contains("cleared:") || clean.contains("the catacombs")) {
                hasCleared = true;
                break;
            }
        }

        // If in dungeon area AND (has dungeon title OR has cleared line), we're in dungeon
        boolean inDungeonArea = SkyblockUtils.isInDungeonArea();
        if (!isDungeonTitle && !hasCleared && !inDungeonArea) {
            return DungeonState.NOT_IN_DUNGEON;
        }

        // If we're in dungeon area with cleared line, definitely in dungeon
        if (inDungeonArea && hasCleared) {
            // Continue to parse for more details
        } else if (!isDungeonTitle && !hasCleared && !inDungeonArea) {
            // Only return NOT_IN_DUNGEON if we're also not in dungeon coordinate area
            return DungeonState.NOT_IN_DUNGEON;
        }

        // Parse scoreboard for more details
        for (String line : scoreboard) {
            String clean = ScoreboardUtils.cleanLine(line);

            // Detect floor from scoreboard
            Matcher floorMatcher = CATACOMBS_PATTERN.matcher(clean);
            if (floorMatcher.find()) {
                currentFloor = DungeonFloor.fromString(floorMatcher.group(1));
            }

            // Check for boss fight indicators
            if (clean.contains("Boss") && clean.contains("\u2764")) { // Heart emoji
                return DungeonState.BOSS_FIGHT;
            }

            // Check for "Cleared:" which indicates active dungeon
            if (clean.contains("Cleared:")) {
                return DungeonState.IN_DUNGEON;
            }

            // Check for "Starting in" which indicates lobby
            if (clean.contains("Starting in")) {
                return DungeonState.STARTING;
            }
        }

        // If we have a dungeon title but no specific state, assume we're in dungeon
        if (isDungeonTitle) {
            return DungeonState.IN_DUNGEON;
        }

        // If we're in dungeon coordinate area but no "Cleared:" line yet, we're in starting room
        if (inDungeonArea) {
            return DungeonState.STARTING;
        }

        return DungeonState.NOT_IN_DUNGEON;
    }

    private static void onStateChange(DungeonState oldState, DungeonState newState) {
        TeslaMaps.LOGGER.info("Dungeon state changed: {} -> {}", oldState, newState);

        // Entered dungeon (either STARTING or IN_DUNGEON from NOT_IN_DUNGEON)
        if (oldState == DungeonState.NOT_IN_DUNGEON &&
            (newState == DungeonState.IN_DUNGEON || newState == DungeonState.STARTING)) {
            onDungeonEnter();
        } else if (newState == DungeonState.NOT_IN_DUNGEON) {
            // Left dungeon
            onDungeonExit();
        }
    }

    private static void onDungeonEnter() {
        dungeonStartTime = System.currentTimeMillis();
        grid.clear();

        TeslaMaps.LOGGER.info("Entered dungeon: {}", currentFloor);

        // Reset score tracker
        DungeonScore.onDungeonStart();

        // Trigger initial scan of entire dungeon area
        RoomScanner.triggerFullScan();
    }

    private static void onDungeonExit() {
        currentFloor = DungeonFloor.UNKNOWN;
        grid.clear();
        PlayerTracker.reset();
        MapScanner.reset();
        SecretTracker.reset();
        MimicDetector.reset();
        PlayerHeadRenderer.clearCache();
        com.teslamaps.esp.StarredMobESP.reset();

        TeslaMaps.LOGGER.info("Exited dungeon");
    }

    // Public getters
    public static boolean isInDungeon() {
        return currentState == DungeonState.IN_DUNGEON ||
                currentState == DungeonState.BOSS_FIGHT ||
                currentState == DungeonState.STARTING;
    }

    public static boolean isInBoss() {
        return currentState == DungeonState.BOSS_FIGHT;
    }

    public static DungeonState getCurrentState() {
        return currentState;
    }

    public static DungeonFloor getCurrentFloor() {
        return currentFloor;
    }

    public static ComponentGrid getGrid() {
        return grid;
    }

    public static long getDungeonStartTime() {
        return dungeonStartTime;
    }

    // Room management
    public static void addRoom(DungeonRoom room) {
        for (int[] component : room.getComponents()) {
            grid.setRoom(component[0], component[1], room);
        }
    }

    public static DungeonRoom getRoomAt(int gridX, int gridZ) {
        return grid.getRoom(gridX, gridZ);
    }

    /**
     * Get total crypts in the dungeon based on identified rooms.
     */
    public static int getTotalCrypts() {
        int total = 0;
        java.util.Set<DungeonRoom> counted = new java.util.HashSet<>();
        for (DungeonRoom room : grid.getAllRooms()) {
            if (!counted.contains(room) && room.getCrypts() > 0) {
                total += room.getCrypts();
                counted.add(room);
            }
        }
        return total;
    }
}
