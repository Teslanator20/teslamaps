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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonManager {
    private static DungeonState currentState = DungeonState.NOT_IN_DUNGEON;
    private static DungeonFloor currentFloor = DungeonFloor.UNKNOWN;
    private static final ComponentGrid grid = new ComponentGrid();
    private static long dungeonStartTime = 0;
    private static int tickCounter = 0;

    // Track world instance to detect dungeon-to-dungeon transitions
    private static ClientWorld lastWorld = null;

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
        // Detect world instance change while in dungeon (handles dungeon-to-dungeon transitions)
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != lastWorld) {
            if (lastWorld != null && isInDungeon()) {
                TeslaMaps.LOGGER.info("World instance changed while in dungeon, forcing map reset");
                onDungeonExit();
                currentState = DungeonState.NOT_IN_DUNGEON;
            }
            lastWorld = mc.world;
        }

        DungeonFloor previousFloor = currentFloor;
        DungeonState newState = detectCurrentState();

        // Detect floor change while already in dungeon (e.g., F2 -> F3 without world change)
        if (isInDungeon() && previousFloor != DungeonFloor.UNKNOWN
                && currentFloor != previousFloor && currentFloor != DungeonFloor.UNKNOWN) {
            TeslaMaps.LOGGER.info("Floor changed while in dungeon ({} -> {}), forcing map reset",
                    previousFloor, currentFloor);
            onDungeonExit();
            currentState = DungeonState.NOT_IN_DUNGEON;
            // Re-detect since we just reset
            newState = detectCurrentState();
        }

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

        // Strictly check for "Catacombs" in scoreboard lines
        boolean hasCatacombs = false;
        boolean hasCleared = false;
        boolean hasStarting = false;
        boolean hasBoss = false;

        for (String line : scoreboard) {
            String clean = ScoreboardUtils.cleanLine(line);
            String cleanLower = clean.toLowerCase();

            // Check for "Catacombs" - the definitive dungeon indicator
            // Can appear as "The Catacombs (F2)" or just "Catacombs"
            if (cleanLower.contains("catacombs")) {
                hasCatacombs = true;

                // Try to detect floor from this line
                Matcher floorMatcher = CATACOMBS_PATTERN.matcher(clean);
                if (floorMatcher.find()) {
                    currentFloor = DungeonFloor.fromString(floorMatcher.group(1));
                }
            }

            // Check for "Cleared:" which indicates active dungeon
            if (cleanLower.contains("cleared:")) {
                hasCleared = true;
            }

            // Check for "Starting in" which indicates lobby/starting
            if (cleanLower.contains("starting in")) {
                hasStarting = true;
            }

            // Check for boss fight indicators
            if (clean.contains("Boss") && clean.contains("\u2764")) { // Heart emoji
                hasBoss = true;
            }
        }

        // Only consider in dungeon if "The Catacombs" is on the scoreboard
        if (!hasCatacombs) {
            return DungeonState.NOT_IN_DUNGEON;
        }

        // Determine specific state
        if (hasBoss) {
            return DungeonState.BOSS_FIGHT;
        }
        if (hasCleared) {
            return DungeonState.IN_DUNGEON;
        }
        if (hasStarting) {
            return DungeonState.STARTING;
        }

        // Has "The Catacombs" but no specific state - assume in dungeon
        return DungeonState.IN_DUNGEON;
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
        com.teslamaps.features.SecretWaypoints.reset();

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

    public static String getFloorName() {
        return currentFloor != null ? currentFloor.name() : "";
    }

    public static DungeonRoom getCurrentRoom() {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || grid == null) return null;
        int[] gridPos = ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ());
        if (gridPos == null) return null;
        return grid.getRoom(gridPos[0], gridPos[1]);
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
