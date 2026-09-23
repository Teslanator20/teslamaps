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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public class DungeonManager {
    private static DungeonState currentState = DungeonState.NOT_IN_DUNGEON;
    private static DungeonFloor currentFloor = DungeonFloor.UNKNOWN;
    private static final ComponentGrid grid = new ComponentGrid();
    private static long dungeonStartTime = 0;
    private static int tickCounter = 0;

    private static ClientLevel lastWorld = null;

    private static final Pattern FLOOR_PATTERN = Pattern.compile("([FM])(\\d+)");
    private static final Pattern CATACOMBS_PATTERN = Pattern.compile("The Catacombs \\(([FM]\\d+)\\)");

    public static void tick() {
        tickCounter++;

        if (tickCounter % 20 == 0) {
            updateDungeonState();
        }
    }

    private static void updateDungeonState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastWorld) {
            if (lastWorld != null && isInDungeon()) {
                TeslaMaps.debug("World instance changed while in dungeon, forcing map reset");
                onDungeonExit();
                currentState = DungeonState.NOT_IN_DUNGEON;
            }
            lastWorld = mc.level;
        }

        DungeonFloor previousFloor = currentFloor;
        DungeonState newState = detectCurrentState();

        if (isInDungeon() && previousFloor != DungeonFloor.UNKNOWN
                && currentFloor != previousFloor && currentFloor != DungeonFloor.UNKNOWN) {
            TeslaMaps.debug("Floor changed while in dungeon ({} -> {}), forcing map reset",
                    previousFloor, currentFloor);
            onDungeonExit();
            currentState = DungeonState.NOT_IN_DUNGEON;
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

        boolean hasCatacombs = false;
        boolean hasCleared = false;
        boolean hasStarting = false;
        boolean hasBoss = false;

        for (String line : scoreboard) {
            String clean = ScoreboardUtils.cleanLine(line);
            String cleanLower = clean.toLowerCase();

            // "the catacombs", not "catacombs": the hub has a zone called Catacombs Entrance and
            // its sidebar line would otherwise put us in a dungeon that never ends
            if (cleanLower.contains("the catacombs")) {
                hasCatacombs = true;

                Matcher floorMatcher = CATACOMBS_PATTERN.matcher(clean);
                if (floorMatcher.find()) {
                    currentFloor = DungeonFloor.fromString(floorMatcher.group(1));
                }
            }

            if (cleanLower.contains("cleared:")) {
                hasCleared = true;
            }

            if (cleanLower.contains("starting in")) {
                hasStarting = true;
            }

            if (clean.contains("Boss") && clean.contains("\u2764")) { // Heart emoji
                hasBoss = true;
            }
        }

        if (!hasCatacombs) {
            // M7 P5 swaps the sidebar and the Catacombs line vanishes mid-fight; leaving a
            // dungeon always changes worlds, so stay in until the world-change path exits us
            // Escape hatch so a bad read can never strand us: the tab list keeps saying
            // "Dungeon: The Catacombs" the whole run. Empty means unknown, not outside.
            String tabArea = SkyblockUtils.getArea().toLowerCase();
            if (!tabArea.isEmpty() && !tabArea.contains("catacombs")) {
                return DungeonState.NOT_IN_DUNGEON;
            }
            return isInDungeon() ? currentState : DungeonState.NOT_IN_DUNGEON;
        }

        if (hasBoss) {
            return DungeonState.BOSS_FIGHT;
        }
        if (hasCleared) {
            return DungeonState.IN_DUNGEON;
        }
        if (hasStarting) {
            return DungeonState.STARTING;
        }

        return DungeonState.IN_DUNGEON;
    }

    private static void onStateChange(DungeonState oldState, DungeonState newState) {
        TeslaMaps.debug("Dungeon state changed: {} -> {}", oldState, newState);

        if (oldState == DungeonState.NOT_IN_DUNGEON &&
            (newState == DungeonState.IN_DUNGEON || newState == DungeonState.STARTING)) {
            onDungeonEnter();
        } else if (newState == DungeonState.NOT_IN_DUNGEON) {
            onDungeonExit();
        }
    }

    private static void onDungeonEnter() {
        dungeonStartTime = System.currentTimeMillis();
        grid.clear();

        TeslaMaps.debug("Entered dungeon: {}", currentFloor);

        DungeonScore.onDungeonStart();
        RareRoomAlert.reset();

        RoomScanner.triggerFullScan();
    }

    private static void onDungeonExit() {
        currentFloor = DungeonFloor.UNKNOWN;
        grid.clear();
        PlayerTracker.reset();
        MapScanner.reset();
        SecretTracker.reset();
        com.teslamaps.features.SecretChime.reset();
        MimicDetector.reset();
        PlayerHeadRenderer.clearCache();
        com.teslamaps.esp.StarredMobESP.reset();
        com.teslamaps.features.SecretWaypoints.reset();
        DungeonWaypoints.resetClicked();
        Splits.reset();
        BloodCamp.reset();
        DragonCheck.dragonHealthMap.clear();
        WitherDragons.reset();

        TeslaMaps.debug("Exited dungeon");
    }

    public static boolean isInDungeon() {
        return currentState == DungeonState.IN_DUNGEON ||
                currentState == DungeonState.BOSS_FIGHT ||
                currentState == DungeonState.STARTING;
    }

    public static boolean isInBoss() {
        return currentState == DungeonState.BOSS_FIGHT;
    }

    /**
     * Reliable boss check. The scoreboard boss-health line only says "Boss" on some
     * floors (F4 Thorn never matches), so also treat standing outside the room grid
     * as the boss area - every boss arena lies outside it.
     */
    public static boolean isInBossArea() {
        if (currentState == DungeonState.BOSS_FIGHT) return true;
        if (!isInDungeon()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ()) == null;
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
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
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

    public static void addRoom(DungeonRoom room) {
        for (int[] component : room.getComponents()) {
            grid.setRoom(component[0], component[1], room);
        }
    }

    public static DungeonRoom getRoomAt(int gridX, int gridZ) {
        return grid.getRoom(gridX, gridZ);
    }

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
