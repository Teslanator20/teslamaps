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

import com.teslamaps.map.DungeonRoom;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ComponentGrid {
    public static final int DUNGEON_MIN_X = -200;
    public static final int DUNGEON_MAX_X = -10;
    public static final int DUNGEON_MIN_Z = -200;
    public static final int DUNGEON_MAX_Z = -10;

    public static final int ROOM_SIZE = 31;      // Room is 31 blocks
    public static final int DOOR_SIZE = 1;       // Door is 1 block
    public static final int TOTAL_SIZE = 32;     // Room + door = 32 blocks
    public static final int HALF_ROOM_SIZE = 15; // Math.floor(31/2)

    public static final int GRID_SIZE = 6;       // 6x6 component grid

    private final Map<Integer, DungeonRoom> rooms = new HashMap<>();

    public void clear() {
        rooms.clear();
    }

    public void setRoom(int gridX, int gridZ, DungeonRoom room) {
        if (isValidGridPos(gridX, gridZ)) {
            rooms.put(gridX * GRID_SIZE + gridZ, room);
        }
    }

    public DungeonRoom getRoom(int gridX, int gridZ) {
        if (!isValidGridPos(gridX, gridZ)) return null;
        return rooms.get(gridX * GRID_SIZE + gridZ);
    }

    public Collection<DungeonRoom> getAllRooms() {
        return new java.util.HashSet<>(rooms.values());
    }

    public boolean hasRoom(int gridX, int gridZ) {
        return getRoom(gridX, gridZ) != null;
    }

    public boolean isValidGridPos(int gridX, int gridZ) {
        return gridX >= 0 && gridX < GRID_SIZE && gridZ >= 0 && gridZ < GRID_SIZE;
    }

    public static int[] worldToGrid(double worldX, double worldZ) {
        if (worldX < DUNGEON_MIN_X || worldX > DUNGEON_MAX_X ||
                worldZ < DUNGEON_MIN_Z || worldZ > DUNGEON_MAX_Z) {
            return null;
        }

        int gridX = (int) ((worldX - DUNGEON_MIN_X) / TOTAL_SIZE);
        int gridZ = (int) ((worldZ - DUNGEON_MIN_Z) / TOTAL_SIZE);

        gridX = Math.max(0, Math.min(GRID_SIZE - 1, gridX));
        gridZ = Math.max(0, Math.min(GRID_SIZE - 1, gridZ));

        return new int[]{gridX, gridZ};
    }

    public static int[] gridToWorld(int gridX, int gridZ) {
        int worldX = DUNGEON_MIN_X + HALF_ROOM_SIZE + (gridX * TOTAL_SIZE);
        int worldZ = DUNGEON_MIN_Z + HALF_ROOM_SIZE + (gridZ * TOTAL_SIZE);
        return new int[]{worldX, worldZ};
    }

    public static int[] gridToWorldCorner(int gridX, int gridZ) {
        int worldX = DUNGEON_MIN_X + (gridX * TOTAL_SIZE);
        int worldZ = DUNGEON_MIN_Z + (gridZ * TOTAL_SIZE);
        return new int[]{worldX, worldZ};
    }

    public static int[][] getAllGridPositions() {
        int[][] positions = new int[GRID_SIZE * GRID_SIZE][2];
        int index = 0;
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int z = 0; z < GRID_SIZE; z++) {
                positions[index++] = gridToWorld(x, z);
            }
        }
        return positions;
    }

    public static net.minecraft.core.BlockPos relativeToActual(int gridX, int gridZ, net.minecraft.core.BlockPos relative) {
        int[] corner = gridToWorldCorner(gridX, gridZ);
        return new net.minecraft.core.BlockPos(
                corner[0] + relative.getX(),
                relative.getY(),
                corner[1] + relative.getZ()
        );
    }

    public static net.minecraft.core.BlockPos actualToRelative(int gridX, int gridZ, net.minecraft.core.BlockPos actual) {
        int[] corner = gridToWorldCorner(gridX, gridZ);
        return new net.minecraft.core.BlockPos(
                actual.getX() - corner[0],
                actual.getY(),
                actual.getZ() - corner[1]
        );
    }
}
