package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class DoorScanner {

    // Flat lookup instead of a keyed map: read for every door on every frame, and a
    // 6x6 grid means the whole cell-pair key space fits in one array with no boxing.
    private static final int GRID = ComponentGrid.GRID_SIZE;
    private static final DoorType[] doors = new DoorType[GRID * GRID * GRID * GRID];
    private static int doorCount = 0;

    public static void reset() {
        java.util.Arrays.fill(doors, null);
        doorCount = 0;
    }

    /** Normalised index for the cell pair, -1 if either cell is off the grid. */
    private static int doorIndex(int x1, int z1, int x2, int z2) {
        if (x1 < 0 || x1 >= GRID || z1 < 0 || z1 >= GRID) return -1;
        if (x2 < 0 || x2 >= GRID || z2 < 0 || z2 >= GRID) return -1;

        int a, b;
        if (x1 < x2 || (x1 == x2 && z1 < z2)) {
            a = x1 * GRID + z1;
            b = x2 * GRID + z2;
        } else {
            a = x2 * GRID + z2;
            b = x1 * GRID + z1;
        }
        return a * (GRID * GRID) + b;
    }

    public static void scanAllDoors() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        TeslaMaps.LOGGER.debug("Scanning doors...");

        for (int gx = 0; gx <= 10; gx++) {
            for (int gz = 0; gz <= 10; gz++) {
                if ((gx % 2 == 0 && gz % 2 == 0) || (gx % 2 == 1 && gz % 2 == 1)) {
                    continue;
                }

                scanDoorPosition(mc.level, gx, gz);
            }
        }

        TeslaMaps.LOGGER.debug("Door scan complete. Found {} doors", doorCount);
    }

    private static void scanDoorPosition(Level world, int gx, int gz) {
        int worldX = ComponentGrid.DUNGEON_MIN_X + ComponentGrid.HALF_ROOM_SIZE + gx * 16;
        int worldZ = ComponentGrid.DUNGEON_MIN_Z + ComponentGrid.HALF_ROOM_SIZE + gz * 16;

        if (!CoreHasher.isPositionLoaded(world, worldX, worldZ)) {
            return;
        }

        int room1X, room1Z, room2X, room2Z;

        if (gx % 2 == 1) {
            room1X = (gx - 1) / 2;
            room2X = (gx + 1) / 2;
            room1Z = gz / 2;
            room2Z = gz / 2;
        } else {
            room1X = gx / 2;
            room2X = gx / 2;
            room1Z = (gz - 1) / 2;
            room2Z = (gz + 1) / 2;
        }

        DungeonRoom room1 = DungeonManager.getGrid().getRoom(room1X, room1Z);
        DungeonRoom room2 = DungeonManager.getGrid().getRoom(room2X, room2Z);

        if (room1 == null || room2 == null) {
            return;
        }

        if (room1 == room2) {
            return;
        }

        int key = doorIndex(room1X, room1Z, room2X, room2Z);
        if (key < 0 || doors[key] != null) {
            return;
        }

        int roofHeight = getHighestBlock(world, worldX, worldZ);

        DoorType doorType = detectDoorType(world, worldX, worldZ, roofHeight);

        if (doorType != DoorType.NONE) {
            doors[key] = doorType;
            doorCount++;
            TeslaMaps.LOGGER.debug("Found {} door between [{},{}] and [{},{}] at world [{},{}] roof={}",
                    doorType, room1X, room1Z, room2X, room2Z, worldX, worldZ, roofHeight);
        }
    }

    private static DoorType detectDoorType(Level world, int x, int z, int roofHeight) {
        if (roofHeight <= 0) {
            return DoorType.NONE;
        }

        int entranceCount = 0;
        int witherCount = 0;
        int bloodCount = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockState(new BlockPos(x + dx, 69, z + dz)).getBlock();
                String blockName = BuiltInRegistries.BLOCK.getKey(block).toString();

                if (blockName.contains("infested")) {
                    entranceCount++;
                }
                else if (blockName.equals("minecraft:coal_block")) {
                    witherCount++;
                }
                else if (blockName.contains("red_terracotta") || blockName.contains("stained_hardened_clay")) {
                    bloodCount++;
                }
            }
        }

        if (entranceCount >= 2) {
            return DoorType.ENTRANCE;
        }

        if (witherCount >= 2) {
            return DoorType.WITHER;
        }

        if (bloodCount >= 4) {
            return DoorType.BLOOD;
        }

        if (roofHeight >= 68 && roofHeight <= 82) {
            // a real doorway is walkable: solid floor with 2 blocks of air above it. Air alone
            // also matches the empty void between unconnected rooms (false "doors" on the map)
            for (int floorY = 69; floorY >= 68; floorY--) {
                if (!isAir(world, x, floorY, z)
                        && isAir(world, x, floorY + 1, z)
                        && isAir(world, x, floorY + 2, z)) {
                    return DoorType.NORMAL;
                }
            }
        }

        return DoorType.NONE;
    }

    private static boolean isAir(Level world, int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    private static int getHighestBlock(Level world, int x, int z) {
        for (int y = 255; y > 0; y--) {
            Block block = world.getBlockState(new BlockPos(x, y, z)).getBlock();
            String blockName = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (blockName.equals("minecraft:air") ||
                blockName.equals("minecraft:cave_air") ||
                blockName.equals("minecraft:gold_block")) {
                continue;
            }
            return y;
        }
        return 0;
    }

    public static boolean hasDoorBetween(int x1, int z1, int x2, int z2) {
        int key = doorIndex(x1, z1, x2, z2);
        return key >= 0 && doors[key] != null;
    }

    public static DoorType getDoorType(int x1, int z1, int x2, int z2) {
        int key = doorIndex(x1, z1, x2, z2);
        if (key < 0 || doors[key] == null) return DoorType.NONE;
        return doors[key];
    }

    /**
     * Detects the door between two adjacent room cells directly from the world,
     * without requiring either room to be scanned. Used by Legit Mode to find
     * doors on the edge of explored rooms leading to undiscovered cells.
     * Returns NONE if the door area isn't loaded or there is no door.
     */
    public static DoorType scanDoorBetweenCells(int rx1, int rz1, int rx2, int rz2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return DoorType.NONE;

        int gx, gz;
        if (rx1 != rx2) { gx = rx1 + rx2; gz = rz1 * 2; }   // horizontal neighbour
        else { gx = rx1 * 2; gz = rz1 + rz2; }              // vertical neighbour

        int worldX = ComponentGrid.DUNGEON_MIN_X + ComponentGrid.HALF_ROOM_SIZE + gx * 16;
        int worldZ = ComponentGrid.DUNGEON_MIN_Z + ComponentGrid.HALF_ROOM_SIZE + gz * 16;

        if (!CoreHasher.isPositionLoaded(mc.level, worldX, worldZ)) return DoorType.NONE;

        int roofHeight = getHighestBlock(mc.level, worldX, worldZ);
        return detectDoorType(mc.level, worldX, worldZ, roofHeight);
    }

    /** Debug only - builds the keyed view on demand. */
    public static Map<String, DoorType> getAllDoors() {
        Map<String, DoorType> out = new HashMap<>();
        for (int i = 0; i < doors.length; i++) {
            if (doors[i] == null) continue;
            int a = i / (GRID * GRID), b = i % (GRID * GRID);
            out.put((a / GRID) + "," + (a % GRID) + "-" + (b / GRID) + "," + (b % GRID), doors[i]);
        }
        return out;
    }
}
