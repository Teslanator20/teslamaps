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
import com.teslamaps.database.RoomData;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class RoomScanner {
    private static int tickCounter = 0;
    private static boolean fullScanRequested = false;
    private static final Set<Integer> scannedPositions = new HashSet<>();

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            return;
        }

        tickCounter++;

        if (fullScanRequested) {
            performFullScan();
            fullScanRequested = false;
            return;
        }

        if (TeslaMapsConfig.get().autoScan &&
                tickCounter % TeslaMapsConfig.get().scanTickInterval == 0) {
            scanUnscannedPositions();
        }

        if (tickCounter % 80 == 0) {
            DoorScanner.scanAllDoors();
        }
    }

    public static void triggerFullScan() {
        fullScanRequested = true;
        scannedPositions.clear();
        TeslaMaps.LOGGER.debug("Full dungeon scan requested");
    }

    private static void performFullScan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        TeslaMaps.LOGGER.debug("Performing full dungeon scan...");

        int roomsFound = 0;

        for (int gridX = 0; gridX < ComponentGrid.GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < ComponentGrid.GRID_SIZE; gridZ++) {
                if (scanPosition(gridX, gridZ)) {
                    roomsFound++;
                }
            }
        }

        mergeDisconnectedRooms();

        DoorScanner.scanAllDoors();

        TeslaMaps.LOGGER.debug("Full scan complete. Found {} rooms", roomsFound);
    }

    private static void scanUnscannedPositions() {
        boolean foundNew = false;
        for (int gridX = 0; gridX < ComponentGrid.GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < ComponentGrid.GRID_SIZE; gridZ++) {
                int posKey = gridX * ComponentGrid.GRID_SIZE + gridZ;

                if (scannedPositions.contains(posKey) &&
                        DungeonManager.getGrid().hasRoom(gridX, gridZ)) {
                    continue;
                }

                if (scanPosition(gridX, gridZ)) {
                    foundNew = true;
                }
            }
        }

        if (foundNew) {
            mergeDisconnectedRooms();
            DoorScanner.reset();
            DoorScanner.scanAllDoors();
        }
    }

    private static boolean scanPosition(int gridX, int gridZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        int[] center = ComponentGrid.gridToWorld(gridX, gridZ);
        int centerX = center[0];
        int centerZ = center[1];

        if (!CoreHasher.isPositionLoaded(mc.level, centerX, centerZ)) {
            return false;
        }

        int posKey = gridX * ComponentGrid.GRID_SIZE + gridZ;
        scannedPositions.add(posKey);

        if (DungeonManager.getGrid().hasRoom(gridX, gridZ)) {
            return true;
        }

        int roofHeight = getHighestBlock(mc.level, centerX, centerZ);
        if (roofHeight == 0) {
            return false;
        }

        int coreHash = CoreHasher.scanRoomCore(gridX, gridZ);

        if (coreHash == 0) {
            return false;
        }

        RoomData roomData = RoomDatabase.getInstance().findByCore(coreHash);

        if (roomData != null) {
            TeslaMaps.LOGGER.debug("[ScanDebug] [{},{}] Looking for adjacent room named '{}'",
                    gridX, gridZ, roomData.getName());

            DungeonRoom existingRoom = findAdjacentRoomWithName(gridX, gridZ, roomData.getName());

            if (existingRoom != null) {
                existingRoom.addComponent(gridX, gridZ);
                DungeonManager.getGrid().setRoom(gridX, gridZ, existingRoom);

                detectAndSetRotation(existingRoom);

                TeslaMaps.LOGGER.debug("[ScanDebug] [{},{}] Added to existing room '{}' (now {} components)",
                        gridX, gridZ, roomData.getName(), existingRoom.getComponents().size());
            } else {
                int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                String[] dirs = {"West", "East", "North", "South"};
                for (int i = 0; i < offsets.length; i++) {
                    int adjX = gridX + offsets[i][0];
                    int adjZ = gridZ + offsets[i][1];
                    DungeonRoom adj = DungeonManager.getGrid().getRoom(adjX, adjZ);
                    if (adj != null) {
                        TeslaMaps.LOGGER.debug("[ScanDebug] [{},{}] {} neighbor [{},{}] has room '{}' (expected '{}')",
                                gridX, gridZ, dirs[i], adjX, adjZ, adj.getName(), roomData.getName());
                    }
                }

                DungeonRoom room = new DungeonRoom(gridX, gridZ);
                room.loadFromRoomData(roomData);
                DungeonManager.addRoom(room);

                detectAndSetRotation(room);

                TeslaMaps.LOGGER.debug("[ScanDebug] [{},{}] Created NEW room '{}' core={} rotation={}",
                        gridX, gridZ, roomData.getName(), coreHash, room.getRotation());
            }

            return true;
        } else {
            TeslaMaps.LOGGER.debug("Unknown room at grid [{},{}] core={} (roof={})",
                    gridX, gridZ, coreHash, roofHeight);

            DungeonRoom room = new DungeonRoom(gridX, gridZ);
            room.setName("Unknown");
            room.setType(RoomType.UNKNOWN);

            DungeonManager.addRoom(room);
            return true;
        }
    }

    private static DungeonRoom findAdjacentRoomWithName(int gridX, int gridZ, String roomName) {
        int[][] orthogonalOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        String[] dirNames = {"West", "East", "North", "South"};

        for (int i = 0; i < orthogonalOffsets.length; i++) {
            int[] offset = orthogonalOffsets[i];
            int adjX = gridX + offset[0];
            int adjZ = gridZ + offset[1];

            DungeonRoom adjacent = DungeonManager.getGrid().getRoom(adjX, adjZ);
            TeslaMaps.LOGGER.debug("[FindAdj] [{},{}] Checking {} neighbor [{},{}]: {}",
                    gridX, gridZ, dirNames[i], adjX, adjZ,
                    adjacent == null ? "null" : ("'" + adjacent.getName() + "'"));

            if (adjacent != null && roomName.equals(adjacent.getName())) {
                TeslaMaps.LOGGER.debug("[FindAdj] [{},{}] Found match at [{},{}]!",
                        gridX, gridZ, adjX, adjZ);
                return adjacent;
            }
        }

        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (!roomName.equals(room.getName())) continue;

            for (int[] comp : room.getComponents()) {
                for (int[] offset : orthogonalOffsets) {
                    if (comp[0] + offset[0] == gridX && comp[1] + offset[1] == gridZ) {
                        return room;
                    }
                }
            }
        }

        return null;
    }

    public static void mergeDisconnectedRooms() {
        java.util.Map<String, java.util.List<DungeonRoom>> roomsByName = new java.util.HashMap<>();

        TeslaMaps.LOGGER.debug("[MergeDebug] Starting merge. All rooms:");
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            StringBuilder comps = new StringBuilder();
            for (int[] comp : room.getComponents()) {
                comps.append("[").append(comp[0]).append(",").append(comp[1]).append("] ");
            }
            TeslaMaps.LOGGER.debug("[MergeDebug]   Room '{}' type={} components: {}",
                    room.getName(), room.getType(), comps.toString().trim());
        }

        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            String name = room.getName();
            if (name == null || name.equals("Unknown")) continue;

            roomsByName.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(room);
        }

        for (java.util.Map.Entry<String, java.util.List<DungeonRoom>> entry : roomsByName.entrySet()) {
            if (entry.getValue().size() > 1) {
                TeslaMaps.LOGGER.debug("[MergeDebug] Multiple '{}' rooms found: {} instances",
                        entry.getKey(), entry.getValue().size());
            }
        }

        int[][] orthogonalOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (java.util.List<DungeonRoom> rooms : roomsByName.values()) {
            if (rooms.size() <= 1) continue;

            boolean merged;
            do {
                merged = false;
                outer:
                for (int i = 0; i < rooms.size(); i++) {
                    DungeonRoom roomA = rooms.get(i);
                    for (int j = i + 1; j < rooms.size(); j++) {
                        DungeonRoom roomB = rooms.get(j);

                        boolean adjacent = false;
                        for (int[] compA : roomA.getComponents()) {
                            for (int[] compB : roomB.getComponents()) {
                                for (int[] offset : orthogonalOffsets) {
                                    if (compA[0] + offset[0] == compB[0] && compA[1] + offset[1] == compB[1]) {
                                        adjacent = true;
                                        break;
                                    }
                                }
                                if (adjacent) break;
                            }
                            if (adjacent) break;
                        }

                        if (adjacent) {
                            for (int[] comp : roomB.getComponents()) {
                                roomA.addComponent(comp[0], comp[1]);
                                DungeonManager.getGrid().setRoom(comp[0], comp[1], roomA);
                            }
                            rooms.remove(j);
                            merged = true;
                            TeslaMaps.LOGGER.debug("Merged adjacent room '{}' components", roomA.getName());
                            break outer;
                        }
                    }
                }
            } while (merged);
        }
    }

    private static int getHighestBlock(net.minecraft.world.level.Level world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            net.minecraft.world.level.block.Block block = world.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).getBlock();
            String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
            if (blockName.equals("minecraft:air") ||
                blockName.equals("minecraft:cave_air") ||
                blockName.equals("minecraft:void_air") ||
                blockName.equals("minecraft:gold_block")) {
                continue;
            }
            return y;
        }
        return 0;
    }

    public static void forceScan() {
        TeslaMaps.LOGGER.debug("Force scan triggered");
        scannedPositions.clear();
        performFullScan();
    }

    public static void reset() {
        scannedPositions.clear();
        fullScanRequested = false;
        tickCounter = 0;
        DoorScanner.reset();
    }

    public static int detectRotation(DungeonRoom room) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || room == null) return -1;

        int halfRoomSize = 15; // Half of 31 (room size without door)

        int minGridX = Integer.MAX_VALUE, minGridZ = Integer.MAX_VALUE;
        int maxGridX = Integer.MIN_VALUE, maxGridZ = Integer.MIN_VALUE;

        for (int[] comp : room.getComponents()) {
            minGridX = Math.min(minGridX, comp[0]);
            minGridZ = Math.min(minGridZ, comp[1]);
            maxGridX = Math.max(maxGridX, comp[0]);
            maxGridZ = Math.max(maxGridZ, comp[1]);
        }

        for (int[] comp : room.getComponents()) {
            int[] center = ComponentGrid.gridToWorld(comp[0], comp[1]);
            int centerX = center[0];
            int centerZ = center[1];

            int roofHeight = getHighestBlock(mc.level, centerX, centerZ);
            if (roofHeight == 0) continue;

            int[][] cornerOffsets = {
                {-halfRoomSize, -halfRoomSize},  // rotation 0
                {halfRoomSize, -halfRoomSize},   // rotation 90
                {halfRoomSize, halfRoomSize},    // rotation 180
                {-halfRoomSize, halfRoomSize}    // rotation 270
            };

            for (int i = 0; i < cornerOffsets.length; i++) {
                int checkX = centerX + cornerOffsets[i][0];
                int checkZ = centerZ + cornerOffsets[i][1];

                for (int checkY : new int[]{roofHeight, roofHeight - 1, roofHeight - 2, roofHeight + 1,
                                            68, 69, 70, 71, 72, 11, 12, 66, 67}) {
                    if (checkY <= 0) continue;

                    BlockPos pos = new BlockPos(checkX, checkY, checkZ);
                    Block block = mc.level.getBlockState(pos).getBlock();

                    if (block == Blocks.BLUE_TERRACOTTA) {
                        int rotation = i * 90;
                        TeslaMaps.LOGGER.debug("Detected rotation {} for room '{}' (blue terracotta at corner {}, pos [{},{},{}])",
                                rotation, room.getName(), i, checkX, checkY, checkZ);

                        room.setCorner(checkX, checkZ);

                        int[] minCornerWorld = ComponentGrid.gridToWorld(minGridX, minGridZ);
                        TeslaMaps.LOGGER.debug("  Room bounding box NW corner would be at [{},{}]",
                                minCornerWorld[0] - halfRoomSize, minCornerWorld[1] - halfRoomSize);

                        return rotation;
                    }
                }
            }
        }

        TeslaMaps.LOGGER.debug("Could not detect blue terracotta for room '{}', using bounding box corner", room.getName());

        int[] minCorner = ComponentGrid.gridToWorld(minGridX, minGridZ);
        room.setCorner(minCorner[0] - halfRoomSize, minCorner[1] - halfRoomSize);
        return 0;
    }

    public static void detectAndSetRotation(DungeonRoom room) {
        if (room == null) return;
        if (room.hasRotation()) return; // Already detected

        int rotation = detectRotation(room);
        room.setRotation(rotation);
    }
}
