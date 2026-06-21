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
package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapScanner {
    private static int tickCounter = 0;

    private static final byte COLOR_GREEN_CHECKMARK = 30;  // MapColor.PLANT.getPackedId(MapColor.Brightness.HIGH)
    private static final byte COLOR_WHITE_CHECKMARK = 34;  // MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH)
    private static final byte COLOR_FAILED = 18;           // MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH)
    private static final byte COLOR_UNEXPLORED = 0;        // Black
    private static final byte COLOR_UNEXPLORED_ALT = 85;   // Unknown room color

    private static final Map<Integer, String> playerIndexToName = new HashMap<>();

    private static final List<int[]> mapPlayerPositions = new ArrayList<>();

    private static final byte COLOR_WITHER_DOOR = 119;  // Black door
    private static final byte COLOR_BLOOD_DOOR = 18;    // Red door (same as failed checkmark)

    private static final List<double[]> witherDoorBoxes = new ArrayList<>();  // [minX, minY, minZ, maxX, maxY, maxZ]
    private static final List<double[]> bloodDoorBoxes = new ArrayList<>();

    private static final Map<String, Integer> roomExplorationScanCount = new HashMap<>();

    public static int mapCornerX = -1;  // Will be detected from map
    public static int mapCornerY = -1;
    public static int mapRoomSize = 16;
    public static int mapGapSize = 4;
    private static boolean mapParamsDetected = false;

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            return;
        }

        tickCounter++;

        if (tickCounter % 10 != 0) {
            return;
        }

        scanDungeonMap();
    }

    private static void scanDungeonMap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        MapItemSavedData mapState = findDungeonMapState(mc);
        if (mapState == null) {
            if (debugLogCounter++ % 100 == 0) {
                TeslaMaps.LOGGER.debug("[MapScanner] No map found in hotbar");
            }
            return;
        }

        extractPlayerPositions(mapState);

        scanDoorsFromMap(mapState);

        byte[] colors = mapState.colors;
        if (colors == null || colors.length < 16384) {
            return;
        }

        if (!mapParamsDetected) {
            detectMapParameters(colors);
        }

        if (mapCornerX < 0 || mapCornerY < 0) {
            return;
        }

        boolean shouldLog = (debugLogCounter++ % 500 == 0);

        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getComponents().isEmpty()) continue;

            CheckmarkState bestState = CheckmarkState.UNEXPLORED;
            boolean mapShowsExplored = false;
            int exploredPixelCount = 0;

            int greenCount = 0, whiteCount = 0, failedCount = 0;
            java.util.Map<Integer, Integer> colorCounts = new java.util.HashMap<>();
            java.util.Map<Integer, Integer> allColorCounts = new java.util.HashMap<>();

            for (int[] comp : room.getComponents()) {
                int gridX = comp[0];
                int gridZ = comp[1];

                int topLeftX = mapCornerX + (mapRoomSize + mapGapSize) * gridX;
                int topLeftY = mapCornerY + (mapRoomSize + mapGapSize) * gridZ;

                int halfRoom = mapRoomSize / 2;

                for (int dy = 0; dy < mapRoomSize; dy++) {
                    for (int dx = 0; dx < mapRoomSize; dx++) {
                        int checkX = topLeftX + dx;
                        int checkY = topLeftY + dy;
                        if (checkX < 0 || checkX >= 128 || checkY < 0 || checkY >= 128) continue;

                        int index = checkX + checkY * 128;
                        if (index < 0 || index >= colors.length) continue;

                        byte color = colors[index];
                        int c = color & 0xFF;

                        if (shouldLog && c != 0) {
                            allColorCounts.merge(c, 1, Integer::sum);
                        }

                        if (dx >= 4 && dx <= 12 && dy >= 4 && dy <= 12) {
                            if (isExploredColor(color)) {
                                exploredPixelCount++;
                            }
                        }
                    }
                }

                for (int dy = 0; dy < mapRoomSize; dy++) {
                    for (int dx = 0; dx < mapRoomSize; dx++) {
                        int checkX = topLeftX + dx;
                        int checkY = topLeftY + dy;
                        if (checkX < 0 || checkX >= 128 || checkY < 0 || checkY >= 128) continue;

                        int idx = checkX + checkY * 128;
                        if (idx < 0 || idx >= colors.length) continue;

                        int c = colors[idx] & 0xFF;
                        if (c == 30) greenCount++;       // GREEN (MapColor.PLANT)
                        else if (c == 34) whiteCount++;  // WHITE (MapColor.SNOW)
                        else if (c == 18) failedCount++; // RED (MapColor.FIRE)
                    }
                }
            }

            if (room.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                bestState = CheckmarkState.GREEN;
            } else {
                if (whiteCount >= 3) {
                    bestState = CheckmarkState.WHITE;
                    if (shouldLog) {
                        TeslaMaps.LOGGER.debug("[MapScanner] Room '{}' has {} white checkmark pixels -> WHITE",
                                room.getName(), whiteCount);
                    }
                } else if (failedCount >= 3) {
                    bestState = CheckmarkState.FAILED;
                    if (shouldLog) {
                        TeslaMaps.LOGGER.debug("[MapScanner] Room '{}' has {} failed checkmark pixels -> FAILED",
                                room.getName(), failedCount);
                    }
                } else if (greenCount >= (isAdjacentToEntrance(room) ? 29 : 25)) {
                    bestState = CheckmarkState.GREEN;
                    if (shouldLog) {
                        TeslaMaps.LOGGER.debug("[MapScanner] Room '{}' has {} green checkmark pixels -> GREEN",
                                room.getName(), greenCount);
                    }
                } else if (shouldLog) {
                    int[] comp = room.getPrimaryComponent();
                    int topX = mapCornerX + (mapRoomSize + mapGapSize) * comp[0];
                    int topY = mapCornerY + (mapRoomSize + mapGapSize) * comp[1];
                    String status = room.isExplored() ? "explored" : "unexplored(" + exploredPixelCount + "px)";
                    TeslaMaps.LOGGER.debug("[MapScanner] Room '{}' [{}] {} g={} w={} f={} | all: {}",
                            room.getName(), comp[0] + "," + comp[1], status,
                            greenCount, whiteCount, failedCount, allColorCounts);
                }
            }

            int componentsCount = room.getComponents().size();
            int threshold = componentsCount * 20;
            mapShowsExplored = exploredPixelCount >= threshold;

            boolean wasAlreadyExplored = room.isExplored();
            String roomKey = room.getName() + "_" + room.getPrimaryComponent()[0] + "_" + room.getPrimaryComponent()[1];

            if (!roomExplorationScanCount.containsKey(roomKey)) {
                roomExplorationScanCount.put(roomKey, wasAlreadyExplored ? 10 : 0);
            }

            if (mapShowsExplored && !room.isExplored()) {
                room.setExplored(true);
                if (room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                    room.setCheckmarkState(CheckmarkState.NONE);
                }
                if (room.getFoundSecrets() < 0 && room.getSecrets() > 0) {
                    room.setFoundSecrets(0);
                }
                roomExplorationScanCount.put(roomKey, 0);
                TeslaMaps.LOGGER.debug("[MapScanner] Room '{}' [{}] marked as explored by map (pixels={})",
                        room.getName(), room.getPrimaryComponent()[0] + "," + room.getPrimaryComponent()[1], exploredPixelCount);
            }

            if (room.isExplored()) {
                int scanCount = roomExplorationScanCount.getOrDefault(roomKey, 0);
                roomExplorationScanCount.put(roomKey, scanCount + 1);
            }

            if (!mapShowsExplored && room.isExplored() && room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                room.setExplored(false);
                roomExplorationScanCount.put(roomKey, 0);
            }

            int scansSinceExplored = roomExplorationScanCount.getOrDefault(roomKey, 0);
            if (bestState != CheckmarkState.UNEXPLORED && room.getCheckmarkState() != bestState) {
                if (wasAlreadyExplored && scansSinceExplored >= 2) {
                    CheckmarkState oldState = room.getCheckmarkState();
                    room.setCheckmarkState(bestState);
                    TeslaMaps.LOGGER.debug("[MapScanner] Room '{}' [{}] checkmark updated: {} -> {} (after {} scans)",
                            room.getName(), room.getPrimaryComponent()[0] + "," + room.getPrimaryComponent()[1],
                            oldState, bestState, scansSinceExplored);

                    if (bestState == CheckmarkState.GREEN) {
                        SecretTracker.onRoomCompleted(room);
                    }
                } else if (shouldLog && bestState != CheckmarkState.UNEXPLORED) {
                    TeslaMaps.LOGGER.debug("[MapScanner] Room '{}' detected state {} but waiting (explored={}, scans={}/2)",
                            room.getName(), bestState, wasAlreadyExplored, scansSinceExplored);
                }
            }
        }
    }

    private static boolean isExploredColor(byte color) {
        int c = color & 0xFF;
        if (c == 0) return false;

        int brightness = c % 4;

        return brightness >= 2;
    }

    private static int debugLogCounter = 0;

    private static boolean matchesCheckmarkColor(byte color) {
        int c = color & 0xFF;  // Convert to unsigned
        return c == 30 || c == 34 || c == 18;  // GREEN, WHITE, FAILED
    }

    private static CheckmarkState getCheckmarkStateFromColor(byte color) {
        int c = color & 0xFF;  // Convert to unsigned
        if (c == 30) return CheckmarkState.GREEN;   // GREEN
        if (c == 34) return CheckmarkState.WHITE;   // WHITE
        if (c == 18) return CheckmarkState.FAILED;  // FAILED/RED
        return CheckmarkState.NONE;
    }

    private static MapItemSavedData findDungeonMapState(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof MapItem) {
                MapId mapId = stack.get(DataComponents.MAP_ID);
                if (mapId != null) {
                    MapItemSavedData state = MapItem.getSavedData(mapId, mc.level);
                    if (state != null) {
                        return state;
                    }
                }
            }
        }

        ItemStack offhand = mc.player.getOffhandItem();
        if (offhand.getItem() instanceof MapItem) {
            MapId mapId = offhand.get(DataComponents.MAP_ID);
            if (mapId != null) {
                MapItemSavedData state = MapItem.getSavedData(mapId, mc.level);
                if (state != null) {
                    return state;
                }
            }
        }

        return null;
    }

    private static void extractPlayerPositions(MapItemSavedData mapState) {
        mapPlayerPositions.clear();

        try {
            Iterable<MapDecoration> decorations = mapState.getDecorations();
            if (decorations == null) {
                if (debugLogCounter % 100 == 0) {
                    TeslaMaps.LOGGER.debug("[MapScanner] No decorations on map");
                }
                return;
            }

            int decorationCount = 0;
            for (MapDecoration decoration : decorations) {
                decorationCount++;
                String typeId = decoration.type().getRegisteredName();

                if (debugLogCounter % 100 == 0) {
                    TeslaMaps.LOGGER.debug("[MapScanner] Decoration: type={} x={} z={} rot={}",
                            typeId, decoration.x(), decoration.y(), decoration.rot());
                }

                if (typeId.contains("player") || typeId.contains("blue_marker") || typeId.contains("frame")) {
                    int mapX = (decoration.x() + 128) / 2;  // Convert from -128..128 to 0..128
                    int mapZ = (decoration.y() + 128) / 2;

                    int isLocal = typeId.equals("minecraft:frame") ? 1 : 0;

                    mapPlayerPositions.add(new int[]{mapX, mapZ, (int)decoration.rot(), isLocal});

                    if (debugLogCounter % 100 == 0) {
                        TeslaMaps.LOGGER.debug("[MapScanner] Player at map[{},{}] type={} isLocal={}", mapX, mapZ, typeId, isLocal);
                    }
                }
            }

            if (debugLogCounter % 100 == 0) {
                TeslaMaps.LOGGER.debug("[MapScanner] Total decorations: {}, player positions: {}",
                        decorationCount, mapPlayerPositions.size());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[MapScanner] Error extracting player positions from map", e);
        }
    }

    private static void scanDoorsFromMap(MapItemSavedData mapState) {
        witherDoorBoxes.clear();
        bloodDoorBoxes.clear();

        if (!mapParamsDetected || mapCornerX < 0 || mapCornerY < 0) {
            return;
        }

        byte[] colors = mapState.colors;
        if (colors == null || colors.length < 16384) {
            return;
        }

        int cellSize = mapRoomSize + mapGapSize;  // Usually 16 + 4 = 20

        boolean shouldDebug = debugLogCounter % 100 == 0;
        if (shouldDebug) {
            Map<Integer, Integer> doorColorCounts = new HashMap<>();
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int c = colors[x + y * 128] & 0xFF;
                    if (c == 119 || c == 18 || c == 116 || c == 117 || c == 118 || c == 44 || c == 45 || c == 46 || c == 47) {
                        doorColorCounts.merge(c, 1, Integer::sum);
                    }
                }
            }
            if (!doorColorCounts.isEmpty()) {
                TeslaMaps.LOGGER.debug("[MapScanner] Door color scan - potential door colors on map: {}", doorColorCounts);
            }
        }

        for (int mapX = mapCornerX + mapRoomSize; mapX < 128; mapX += cellSize) {
            for (int mapY = mapCornerY + mapRoomSize / 2; mapY < 128; mapY += cellSize) {
                if (mapX < 0 || mapX >= 128 || mapY < 0 || mapY >= 128) continue;
                int idx = mapX + mapY * 128;
                if (idx < 0 || idx >= colors.length) continue;

                int color = colors[idx] & 0xFF;
                if (shouldDebug && color != 0) {
                    TeslaMaps.LOGGER.debug("[MapScanner] Vertical gap at ({},{}) color={}", mapX, mapY, color);
                }
                if (color == COLOR_WITHER_DOOR || color == COLOR_BLOOD_DOOR) {
                    double[] worldPos = mapToWorldPosition(mapX, mapY, true);
                    if (worldPos != null) {
                        double[] box = new double[]{
                            worldPos[0] - 1.5, 69, worldPos[1] - 1.5,
                            worldPos[0] + 1.5, 73, worldPos[1] + 1.5
                        };
                        if (color == COLOR_WITHER_DOOR) {
                            witherDoorBoxes.add(box);
                        } else {
                            bloodDoorBoxes.add(box);
                        }
                        TeslaMaps.LOGGER.debug("[MapScanner] Found {} door at map({},{}) -> world({},{})",
                            color == COLOR_WITHER_DOOR ? "WITHER" : "BLOOD",
                            mapX, mapY, worldPos[0], worldPos[1]);
                    }
                }
            }
        }

        for (int mapX = mapCornerX + mapRoomSize / 2; mapX < 128; mapX += cellSize) {
            for (int mapY = mapCornerY + mapRoomSize; mapY < 128; mapY += cellSize) {
                if (mapX < 0 || mapX >= 128 || mapY < 0 || mapY >= 128) continue;
                int idx = mapX + mapY * 128;
                if (idx < 0 || idx >= colors.length) continue;

                int color = colors[idx] & 0xFF;
                if (shouldDebug && color != 0) {
                    TeslaMaps.LOGGER.debug("[MapScanner] Horizontal gap at ({},{}) color={}", mapX, mapY, color);
                }
                if (color == COLOR_WITHER_DOOR || color == COLOR_BLOOD_DOOR) {
                    double[] worldPos = mapToWorldPosition(mapX, mapY, false);
                    if (worldPos != null) {
                        double[] box = new double[]{
                            worldPos[0] - 1.5, 69, worldPos[1] - 1.5,
                            worldPos[0] + 1.5, 73, worldPos[1] + 1.5
                        };
                        if (color == COLOR_WITHER_DOOR) {
                            witherDoorBoxes.add(box);
                        } else {
                            bloodDoorBoxes.add(box);
                        }
                        TeslaMaps.LOGGER.debug("[MapScanner] Found {} door at map({},{}) -> world({},{})",
                            color == COLOR_WITHER_DOOR ? "WITHER" : "BLOOD",
                            mapX, mapY, worldPos[0], worldPos[1]);
                    }
                }
            }
        }
    }

    public static double[] mapToWorldPosition(int mapX, int mapY) {
        return mapToWorldPosition(mapX, mapY, false);
    }

    private static double[] mapToWorldPosition(int mapX, int mapY, boolean isVerticalDoor) {

        DungeonRoom entranceRoom = null;
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                entranceRoom = room;
                break;
            }
        }

        if (entranceRoom == null) {
            return null;
        }

        int[] entranceGrid = entranceRoom.getPrimaryComponent();
        int[] entranceWorld = ComponentGrid.gridToWorld(entranceGrid[0], entranceGrid[1]);

        int entranceMapX = mapCornerX + entranceGrid[0] * (mapRoomSize + mapGapSize) + mapRoomSize / 2;
        int entranceMapY = mapCornerY + entranceGrid[1] * (mapRoomSize + mapGapSize) + mapRoomSize / 2;

        int mapOffsetX = mapX - entranceMapX;
        int mapOffsetY = mapY - entranceMapY;

        double blocksPerMapPixel = 32.0 / mapRoomSize;
        double worldX = entranceWorld[0] + mapOffsetX * blocksPerMapPixel;
        double worldZ = entranceWorld[1] + mapOffsetY * blocksPerMapPixel;

        return new double[]{worldX, worldZ};
    }

    public static List<double[]> getWitherDoorBoxes() {
        return witherDoorBoxes;
    }

    public static List<double[]> getBloodDoorBoxes() {
        return bloodDoorBoxes;
    }

    public static List<int[]> getMapPlayerPositions() {
        return mapPlayerPositions; // Return directly - no copy needed, list is rebuilt on scan
    }

    public static int getPlayerCount() {
        return mapPlayerPositions.size();
    }

    public static int[] mapToGrid(int mapX, int mapZ) {
        int gridX = (mapX - mapCornerX) / (mapRoomSize + mapGapSize);
        int gridZ = (mapZ - mapCornerY) / (mapRoomSize + mapGapSize);

        gridX = Math.max(0, Math.min(5, gridX));
        gridZ = Math.max(0, Math.min(5, gridZ));

        return new int[]{gridX, gridZ};
    }

    public static double[] mapToRenderPosition(int mapX, int mapZ, int roomSize, int doorSize) {
        double cellSize = roomSize + doorSize;
        double x = (mapX - mapCornerX) / (double)(mapRoomSize + mapGapSize) * cellSize;
        double z = (mapZ - mapCornerY) / (double)(mapRoomSize + mapGapSize) * cellSize;

        return new double[]{x, z};
    }

    private static void detectMapParameters(byte[] colors) {
        int entranceX = -1, entranceY = -1;

        for (int y = 5; y < 123; y++) {
            for (int x = 5; x < 123; x++) {
                int idx = x + y * 128;
                if ((colors[idx] & 0xFF) == 30) {
                    entranceX = x;
                    entranceY = y;
                    break;
                }
            }
            if (entranceX >= 0) break;
        }

        if (entranceX < 0) {
            return;
        }

        while (entranceX > 0 && (colors[(entranceX - 1) + entranceY * 128] & 0xFF) == 30) {
            entranceX--;
        }
        while (entranceY > 0 && (colors[entranceX + (entranceY - 1) * 128] & 0xFF) == 30) {
            entranceY--;
        }

        int roomSize = 0;
        while (entranceX + roomSize < 128 && (colors[(entranceX + roomSize) + entranceY * 128] & 0xFF) == 30) {
            roomSize++;
        }

        if (roomSize < 10 || roomSize > 20) {
            mapCornerX = 5;
            mapCornerY = 5;
            mapRoomSize = 16;
        } else {
            mapRoomSize = roomSize;

            DungeonRoom entranceRoom = null;
            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                if (room.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                    entranceRoom = room;
                    break;
                }
            }

            if (entranceRoom != null) {
                int[] entranceGrid = entranceRoom.getPrimaryComponent();
                int cellSize = mapRoomSize + mapGapSize;
                mapCornerX = entranceX - entranceGrid[0] * cellSize;
                mapCornerY = entranceY - entranceGrid[1] * cellSize;
                TeslaMaps.LOGGER.debug("[MapScanner] Detected map params: corner=({},{}), roomSize={}, entranceAt=({},{}), grid=[{},{}]",
                        mapCornerX, mapCornerY, mapRoomSize, entranceX, entranceY, entranceGrid[0], entranceGrid[1]);
            } else {
                mapCornerX = entranceX;
                mapCornerY = entranceY;
                TeslaMaps.LOGGER.debug("[MapScanner] Detected entrance at ({},{}) roomSize={}, waiting for grid",
                        entranceX, entranceY, mapRoomSize);
                return; // Don't mark as detected yet
            }
        }

        mapParamsDetected = true;
    }

    private static boolean isAdjacentToEntrance(DungeonRoom room) {
        DungeonRoom entrance = null;
        for (DungeonRoom r : DungeonManager.getGrid().getAllRooms()) {
            if (r.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                entrance = r;
                break;
            }
        }
        if (entrance == null) return false;

        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] comp : room.getComponents()) {
            for (int[] entranceComp : entrance.getComponents()) {
                for (int[] offset : offsets) {
                    if (comp[0] + offset[0] == entranceComp[0] && comp[1] + offset[1] == entranceComp[1]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void reset() {
        tickCounter = 0;
        mapPlayerPositions.clear();
        mapParamsDetected = false;
        mapCornerX = -1;
        mapCornerY = -1;
        roomExplorationScanCount.clear();
        witherDoorBoxes.clear();
        bloodDoorBoxes.clear();
    }
}
