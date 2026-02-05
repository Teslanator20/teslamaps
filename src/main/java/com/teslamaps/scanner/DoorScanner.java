package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Scans for doors between dungeon rooms.
 * Doors exist at positions between room components on an 11x11 grid.
 * Room components are at even coordinates (0,2,4,6,8,10).
 * Doors are at positions where one coordinate is odd.
 */
public class DoorScanner {

    // Stores door connections: "gx1,gz1-gx2,gz2" -> DoorType
    private static final Map<String, DoorType> doors = new HashMap<>();

    public static void reset() {
        doors.clear();
    }

    /**
     * Scan all door positions in the dungeon.
     */
    public static void scanAllDoors() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        TeslaMaps.LOGGER.debug("Scanning doors...");

        // Scan all 60 possible door positions (11x11 grid minus corners and room positions)
        // Door positions: one coordinate is odd (1,3,5,7,9), other is even (0,2,4,6,8,10)
        for (int gx = 0; gx <= 10; gx++) {
            for (int gz = 0; gz <= 10; gz++) {
                // Skip room positions (both even) and invalid positions (both odd)
                if ((gx % 2 == 0 && gz % 2 == 0) || (gx % 2 == 1 && gz % 2 == 1)) {
                    continue;
                }

                scanDoorPosition(mc.world, gx, gz);
            }
        }

        TeslaMaps.LOGGER.debug("Door scan complete. Found {} doors", doors.size());
    }

    /**
     * Scan a single door position on the 11x11 grid.
     */
    private static void scanDoorPosition(World world, int gx, int gz) {
        // Convert 11x11 grid position to world coordinates
        // Each cell is 16 blocks (halfCombinedSize = 16)
        int worldX = ComponentGrid.DUNGEON_MIN_X + ComponentGrid.HALF_ROOM_SIZE + gx * 16;
        int worldZ = ComponentGrid.DUNGEON_MIN_Z + ComponentGrid.HALF_ROOM_SIZE + gz * 16;

        if (!CoreHasher.isPositionLoaded(world, worldX, worldZ)) {
            return;
        }

        // Determine which rooms this door connects
        int room1X, room1Z, room2X, room2Z;

        if (gx % 2 == 1) {
            // Horizontal door (connects rooms left and right)
            room1X = (gx - 1) / 2;
            room2X = (gx + 1) / 2;
            room1Z = gz / 2;
            room2Z = gz / 2;
        } else {
            // Vertical door (connects rooms above and below)
            room1X = gx / 2;
            room2X = gx / 2;
            room1Z = (gz - 1) / 2;
            room2Z = (gz + 1) / 2;
        }

        // Check if both rooms exist
        DungeonRoom room1 = DungeonManager.getGrid().getRoom(room1X, room1Z);
        DungeonRoom room2 = DungeonManager.getGrid().getRoom(room2X, room2Z);

        if (room1 == null || room2 == null) {
            return;
        }

        // If same room (multi-component), skip - internal connection, not a door
        if (room1 == room2) {
            return;
        }

        // Check if already scanned
        String key = makeDoorKey(room1X, room1Z, room2X, room2Z);
        if (doors.containsKey(key)) {
            return;
        }

        // Check roof height at door position
        int roofHeight = getHighestBlock(world, worldX, worldZ);

        // Check if there's actually a door here (roof height < 85 means passable gap)
        // Or check for special door blocks at y=69
        DoorType doorType = detectDoorType(world, worldX, worldZ, roofHeight);

        if (doorType != DoorType.NONE) {
            doors.put(key, doorType);
            TeslaMaps.LOGGER.debug("Found {} door between [{},{}] and [{},{}] at world [{},{}] roof={}",
                    doorType, room1X, room1Z, room2X, room2Z, worldX, worldZ, roofHeight);
        }
    }

    /**
     * Detect the type of door at a position.
     * Based on IllegalMap's detection: check block at y=69 for special doors,
     * and roof height < 85 for normal passable doors.
     *
     * Improved: check multiple blocks around the door position to confirm.
     */
    private static DoorType detectDoorType(World world, int x, int z, int roofHeight) {
        // Must have valid roof height (position is loaded and has blocks)
        if (roofHeight <= 0) {
            return DoorType.NONE;
        }

        // Check a 3x3 area around the door position for special door blocks
        int entranceCount = 0;
        int witherCount = 0;
        int bloodCount = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockState(new BlockPos(x + dx, 69, z + dz)).getBlock();
                String blockName = Registries.BLOCK.getId(block).toString();

                // Entrance door: monster egg (infested stone)
                if (blockName.contains("infested")) {
                    entranceCount++;
                }
                // Wither door: coal block
                else if (blockName.equals("minecraft:coal_block")) {
                    witherCount++;
                }
                // Blood door: red terracotta/stained clay
                else if (blockName.contains("red_terracotta") || blockName.contains("stained_hardened_clay")) {
                    bloodCount++;
                }
            }
        }

        // Entrance doors: 2+ infested blocks
        if (entranceCount >= 2) {
            return DoorType.ENTRANCE;
        }

        // Wither doors: 2+ coal blocks
        if (witherCount >= 2) {
            return DoorType.WITHER;
        }

        // Blood doors: require 4+ red terracotta blocks (more strict to avoid false positives)
        // Blood doors are larger/more prominent than decorative red terracotta
        if (bloodCount >= 4) {
            return DoorType.BLOOD;
        }

        // Normal door: roof height between 68 and 82 means there's a passable gap
        // More strict range to avoid false positives
        // Dungeon floor is around y=68, ceiling varies but doors have lower ceiling
        if (roofHeight >= 68 && roofHeight <= 82) {
            // Additional check: make sure there's air at walking height
            Block walkBlock = world.getBlockState(new BlockPos(x, 70, z)).getBlock();
            String walkBlockName = Registries.BLOCK.getId(walkBlock).toString();
            if (walkBlockName.contains("air")) {
                return DoorType.NORMAL;
            }
        }

        // No door here (solid wall with full height or no passable gap)
        return DoorType.NONE;
    }

    /**
     * Get the highest non-air block at a position.
     */
    private static int getHighestBlock(World world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
            String blockName = Registries.BLOCK.getId(block).toString();
            if (blockName.equals("minecraft:air") ||
                blockName.equals("minecraft:cave_air") ||
                blockName.equals("minecraft:gold_block")) {
                continue;
            }
            return y;
        }
        return 0;
    }

    /**
     * Create a consistent key for a door connection (smaller coords first).
     */
    private static String makeDoorKey(int x1, int z1, int x2, int z2) {
        if (x1 < x2 || (x1 == x2 && z1 < z2)) {
            return x1 + "," + z1 + "-" + x2 + "," + z2;
        } else {
            return x2 + "," + z2 + "-" + x1 + "," + z1;
        }
    }

    /**
     * Check if there's a door between two room grid positions.
     */
    public static boolean hasDoorBetween(int x1, int z1, int x2, int z2) {
        String key = makeDoorKey(x1, z1, x2, z2);
        return doors.containsKey(key);
    }

    /**
     * Get the door type between two room grid positions.
     */
    public static DoorType getDoorType(int x1, int z1, int x2, int z2) {
        String key = makeDoorKey(x1, z1, x2, z2);
        return doors.getOrDefault(key, DoorType.NONE);
    }

    /**
     * Get all doors as a map.
     */
    public static Map<String, DoorType> getAllDoors() {
        return doors;
    }
}
