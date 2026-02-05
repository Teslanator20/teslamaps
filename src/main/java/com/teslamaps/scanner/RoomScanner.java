package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomData;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Scans dungeon rooms and identifies them using core hashes.
 * Performs aggressive pre-scanning to reveal all rooms before visiting.
 */
public class RoomScanner {
    private static int tickCounter = 0;
    private static boolean fullScanRequested = false;
    private static final Set<Integer> scannedPositions = new HashSet<>();

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            return;
        }

        tickCounter++;

        // Handle full scan request (on dungeon entry)
        if (fullScanRequested) {
            performFullScan();
            fullScanRequested = false;
            return;
        }

        // Periodic re-scan for chunks that may have loaded
        if (TeslaMapsConfig.get().autoScan &&
                tickCounter % TeslaMapsConfig.get().scanTickInterval == 0) {
            scanUnscannedPositions();
        }

        // Periodic door re-scan every 4 seconds (80 ticks) for newly loaded chunks
        if (tickCounter % 80 == 0) {
            DoorScanner.scanAllDoors();
        }
    }

    /**
     * Request a full scan of the entire dungeon area.
     * Called when entering a dungeon.
     */
    public static void triggerFullScan() {
        fullScanRequested = true;
        scannedPositions.clear();
        TeslaMaps.LOGGER.debug("Full dungeon scan requested");
    }

    /**
     * Scan the entire dungeon grid for rooms.
     */
    private static void performFullScan() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        TeslaMaps.LOGGER.debug("Performing full dungeon scan...");

        int roomsFound = 0;

        // Scan all 36 grid positions (6x6)
        for (int gridX = 0; gridX < ComponentGrid.GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < ComponentGrid.GRID_SIZE; gridZ++) {
                if (scanPosition(gridX, gridZ)) {
                    roomsFound++;
                }
            }
        }

        // Merge any rooms with the same name that weren't properly connected
        mergeDisconnectedRooms();

        // After scanning rooms, scan for doors
        DoorScanner.scanAllDoors();

        TeslaMaps.LOGGER.debug("Full scan complete. Found {} rooms", roomsFound);
    }

    /**
     * Scan positions that haven't been scanned yet.
     */
    private static void scanUnscannedPositions() {
        boolean foundNew = false;
        for (int gridX = 0; gridX < ComponentGrid.GRID_SIZE; gridX++) {
            for (int gridZ = 0; gridZ < ComponentGrid.GRID_SIZE; gridZ++) {
                int posKey = gridX * ComponentGrid.GRID_SIZE + gridZ;

                // Skip if already scanned and found a room
                if (scannedPositions.contains(posKey) &&
                        DungeonManager.getGrid().hasRoom(gridX, gridZ)) {
                    continue;
                }

                // Try to scan this position
                if (scanPosition(gridX, gridZ)) {
                    foundNew = true;
                }
            }
        }

        // If we found new rooms, try to merge disconnected ones and rescan doors
        if (foundNew) {
            mergeDisconnectedRooms();
            // Rescan doors after merging to avoid internal room connections
            DoorScanner.reset();
            DoorScanner.scanAllDoors();
        }
    }

    /**
     * Scan a single grid position and identify the room.
     *
     * @return true if a room was found and identified
     */
    private static boolean scanPosition(int gridX, int gridZ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;

        int[] center = ComponentGrid.gridToWorld(gridX, gridZ);
        int centerX = center[0];
        int centerZ = center[1];

        // Check if chunk is loaded
        if (!CoreHasher.isPositionLoaded(mc.world, centerX, centerZ)) {
            return false;
        }

        int posKey = gridX * ComponentGrid.GRID_SIZE + gridZ;
        scannedPositions.add(posKey);

        // Skip if room already exists here
        if (DungeonManager.getGrid().hasRoom(gridX, gridZ)) {
            return true;
        }

        // First check if there's actually a room here (check roof height)
        int roofHeight = getHighestBlock(mc.world, centerX, centerZ);
        if (roofHeight == 0) {
            // No blocks here - not a room
            return false;
        }

        // Calculate core hash
        int coreHash = CoreHasher.scanRoomCore(gridX, gridZ);

        if (coreHash == 0) {
            return false;
        }

        // Look up room in database
        RoomData roomData = RoomDatabase.getInstance().findByCore(coreHash);

        if (roomData != null) {
            // Check if an adjacent cell already has a room with the same name
            // This handles multi-component rooms (2x2, 1x4, L-shapes, etc.)
            TeslaMaps.LOGGER.debug("[ScanDebug] [{},{}] Looking for adjacent room named '{}'",
                    gridX, gridZ, roomData.getName());

            DungeonRoom existingRoom = findAdjacentRoomWithName(gridX, gridZ, roomData.getName());

            if (existingRoom != null) {
                // Add this position as a component of the existing room
                existingRoom.addComponent(gridX, gridZ);
                DungeonManager.getGrid().setRoom(gridX, gridZ, existingRoom);

                // Try to detect rotation if not already detected
                detectAndSetRotation(existingRoom);

                TeslaMaps.LOGGER.debug("[ScanDebug] [{},{}] Added to existing room '{}' (now {} components)",
                        gridX, gridZ, roomData.getName(), existingRoom.getComponents().size());
            } else {
                // Debug: log what's in adjacent cells
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

                // Create new room
                DungeonRoom room = new DungeonRoom(gridX, gridZ);
                room.loadFromRoomData(roomData);
                DungeonManager.addRoom(room);

                // Detect rotation for the new room
                detectAndSetRotation(room);

                TeslaMaps.LOGGER.debug("[ScanDebug] [{},{}] Created NEW room '{}' core={} rotation={}",
                        gridX, gridZ, roomData.getName(), coreHash, room.getRotation());
            }

            return true;
        } else {
            // Unknown room - create placeholder
            TeslaMaps.LOGGER.debug("Unknown room at grid [{},{}] core={} (roof={})",
                    gridX, gridZ, coreHash, roofHeight);

            // Still create a placeholder room so we show something on the map
            DungeonRoom room = new DungeonRoom(gridX, gridZ);
            room.setName("Unknown");
            room.setType(RoomType.UNKNOWN);

            DungeonManager.addRoom(room);
            return true;
        }
    }

    /**
     * Find a room with the given name that has a component adjacent to the given position.
     * Only uses orthogonal adjacency to avoid merging separate room instances.
     */
    private static DungeonRoom findAdjacentRoomWithName(int gridX, int gridZ, String roomName) {
        int[][] orthogonalOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        String[] dirNames = {"West", "East", "North", "South"};

        // Check direct grid neighbors (orthogonal only)
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

        // Search all existing rooms with the same name
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (!roomName.equals(room.getName())) continue;

            // Check if any component of this room is orthogonally adjacent to our position
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

    /**
     * Second pass: merge rooms with the same name that are actually connected.
     * Only merges if rooms share an orthogonal edge (not just diagonal).
     */
    public static void mergeDisconnectedRooms() {
        java.util.Map<String, java.util.List<DungeonRoom>> roomsByName = new java.util.HashMap<>();

        // Debug: Log all rooms before merging
        TeslaMaps.LOGGER.debug("[MergeDebug] Starting merge. All rooms:");
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            StringBuilder comps = new StringBuilder();
            for (int[] comp : room.getComponents()) {
                comps.append("[").append(comp[0]).append(",").append(comp[1]).append("] ");
            }
            TeslaMaps.LOGGER.debug("[MergeDebug]   Room '{}' type={} components: {}",
                    room.getName(), room.getType(), comps.toString().trim());
        }

        // Group rooms by name
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            String name = room.getName();
            if (name == null || name.equals("Unknown")) continue;

            roomsByName.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(room);
        }

        // Debug: Log rooms grouped by name
        for (java.util.Map.Entry<String, java.util.List<DungeonRoom>> entry : roomsByName.entrySet()) {
            if (entry.getValue().size() > 1) {
                TeslaMaps.LOGGER.debug("[MergeDebug] Multiple '{}' rooms found: {} instances",
                        entry.getKey(), entry.getValue().size());
            }
        }

        int[][] orthogonalOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        // Merge rooms with the same name that are orthogonally adjacent
        for (java.util.List<DungeonRoom> rooms : roomsByName.values()) {
            if (rooms.size() <= 1) continue;

            // Check each pair of rooms for adjacency
            boolean merged;
            do {
                merged = false;
                outer:
                for (int i = 0; i < rooms.size(); i++) {
                    DungeonRoom roomA = rooms.get(i);
                    for (int j = i + 1; j < rooms.size(); j++) {
                        DungeonRoom roomB = rooms.get(j);

                        // Check if any component of A is orthogonally adjacent to any component of B
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
                            // Merge B into A
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

    /**
     * Get the highest non-air, non-gold block at a position (matches IllegalMap's getHighestBlock)
     */
    private static int getHighestBlock(net.minecraft.world.World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            net.minecraft.block.Block block = world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z)).getBlock();
            String blockName = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
            // Ignore air variants and gold blocks (gold room has random gold on roof)
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

    /**
     * Force trigger a scan (for debug purposes)
     */
    public static void forceScan() {
        TeslaMaps.LOGGER.debug("Force scan triggered");
        scannedPositions.clear();
        performFullScan();
    }

    /**
     * Reset scanner state.
     */
    public static void reset() {
        scannedPositions.clear();
        fullScanRequested = false;
        tickCounter = 0;
        DoorScanner.reset();
    }

    /**
     * Detect room rotation by finding the blue terracotta corner block.
     * Devonian uses this approach - the terracotta is at specific corner positions.
     *
     * @param room The room to detect rotation for
     * @return The rotation in degrees (0, 90, 180, 270) or -1 if not detected
     */
    public static int detectRotation(DungeonRoom room) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || room == null) return -1;

        int halfRoomSize = 15; // Half of 31 (room size without door)

        // For multi-component rooms, find the bounding box
        int minGridX = Integer.MAX_VALUE, minGridZ = Integer.MAX_VALUE;
        int maxGridX = Integer.MIN_VALUE, maxGridZ = Integer.MIN_VALUE;

        for (int[] comp : room.getComponents()) {
            minGridX = Math.min(minGridX, comp[0]);
            minGridZ = Math.min(minGridZ, comp[1]);
            maxGridX = Math.max(maxGridX, comp[0]);
            maxGridZ = Math.max(maxGridZ, comp[1]);
        }

        // Check all components for the blue terracotta corner marker
        for (int[] comp : room.getComponents()) {
            int[] center = ComponentGrid.gridToWorld(comp[0], comp[1]);
            int centerX = center[0];
            int centerZ = center[1];

            // Get roof height for this component
            int roofHeight = getHighestBlock(mc.world, centerX, centerZ);
            if (roofHeight == 0) continue;

            // Corner offsets relative to room center (matching Devonian's roomOffset)
            int[][] cornerOffsets = {
                {-halfRoomSize, -halfRoomSize},  // rotation 0
                {halfRoomSize, -halfRoomSize},   // rotation 90
                {halfRoomSize, halfRoomSize},    // rotation 180
                {-halfRoomSize, halfRoomSize}    // rotation 270
            };

            // Check each corner for blue terracotta at multiple Y levels
            for (int i = 0; i < cornerOffsets.length; i++) {
                int checkX = centerX + cornerOffsets[i][0];
                int checkZ = centerZ + cornerOffsets[i][1];

                // Check at roof height and common floor heights
                for (int checkY : new int[]{roofHeight, roofHeight - 1, roofHeight - 2, roofHeight + 1,
                                            68, 69, 70, 71, 72, 11, 12, 66, 67}) {
                    if (checkY <= 0) continue;

                    BlockPos pos = new BlockPos(checkX, checkY, checkZ);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (block == Blocks.BLUE_TERRACOTTA) {
                        // Corner index: 0=NW, 1=NE, 2=SE, 3=SW relative to component center
                        // rotation = index * 90, matching Devonian's approach
                        int rotation = i * 90;
                        TeslaMaps.LOGGER.debug("Detected rotation {} for room '{}' (blue terracotta at corner {}, pos [{},{},{}])",
                                rotation, room.getName(), i, checkX, checkY, checkZ);

                        // Store the blue terracotta position as corner (this is Devonian's approach)
                        room.setCorner(checkX, checkZ);

                        // Debug: also log what corner position would be for bounding box
                        int[] minCornerWorld = ComponentGrid.gridToWorld(minGridX, minGridZ);
                        TeslaMaps.LOGGER.debug("  Room bounding box NW corner would be at [{},{}]",
                                minCornerWorld[0] - halfRoomSize, minCornerWorld[1] - halfRoomSize);

                        return rotation;
                    }
                }
            }
        }

        // Fallback: compute corner from bounding box minimum
        TeslaMaps.LOGGER.debug("Could not detect blue terracotta for room '{}', using bounding box corner", room.getName());

        int[] minCorner = ComponentGrid.gridToWorld(minGridX, minGridZ);
        room.setCorner(minCorner[0] - halfRoomSize, minCorner[1] - halfRoomSize);
        return 0;
    }

    /**
     * Detect and set rotation for a room.
     */
    public static void detectAndSetRotation(DungeonRoom room) {
        if (room == null) return;
        if (room.hasRotation()) return; // Already detected

        int rotation = detectRotation(room);
        room.setRotation(rotation);
    }
}
