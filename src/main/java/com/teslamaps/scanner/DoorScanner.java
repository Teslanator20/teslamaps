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

    private static final Map<String, DoorType> doors = new HashMap<>();

    public static void reset() {
        doors.clear();
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

        TeslaMaps.LOGGER.debug("Door scan complete. Found {} doors", doors.size());
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

        String key = makeDoorKey(room1X, room1Z, room2X, room2Z);
        if (doors.containsKey(key)) {
            return;
        }

        int roofHeight = getHighestBlock(world, worldX, worldZ);

        DoorType doorType = detectDoorType(world, worldX, worldZ, roofHeight);

        if (doorType != DoorType.NONE) {
            doors.put(key, doorType);
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
            Block walkBlock = world.getBlockState(new BlockPos(x, 70, z)).getBlock();
            String walkBlockName = BuiltInRegistries.BLOCK.getKey(walkBlock).toString();
            if (walkBlockName.contains("air")) {
                return DoorType.NORMAL;
            }
        }

        return DoorType.NONE;
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

    private static String makeDoorKey(int x1, int z1, int x2, int z2) {
        if (x1 < x2 || (x1 == x2 && z1 < z2)) {
            return x1 + "," + z1 + "-" + x2 + "," + z2;
        } else {
            return x2 + "," + z2 + "-" + x1 + "," + z1;
        }
    }

    public static boolean hasDoorBetween(int x1, int z1, int x2, int z2) {
        String key = makeDoorKey(x1, z1, x2, z2);
        return doors.containsKey(key);
    }

    public static DoorType getDoorType(int x1, int z1, int x2, int z2) {
        String key = makeDoorKey(x1, z1, x2, z2);
        return doors.getOrDefault(key, DoorType.NONE);
    }

    public static Map<String, DoorType> getAllDoors() {
        return doors;
    }
}
