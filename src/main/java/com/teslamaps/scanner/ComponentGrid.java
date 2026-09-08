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

    // Multi-component rooms sit under several grid keys, so values() has duplicates.
    // Deduped once per change instead of per call - this is read every frame.
    private final java.util.Set<DungeonRoom> uniqueRooms = new java.util.LinkedHashSet<>();
    private boolean uniqueDirty = true;

    public void clear() {
        rooms.clear();
        uniqueDirty = true;
    }

    public void setRoom(int gridX, int gridZ, DungeonRoom room) {
        if (isValidGridPos(gridX, gridZ)) {
            rooms.put(gridX * GRID_SIZE + gridZ, room);
            uniqueDirty = true;
        }
    }

    public DungeonRoom getRoom(int gridX, int gridZ) {
        if (!isValidGridPos(gridX, gridZ)) return null;
        return rooms.get(gridX * GRID_SIZE + gridZ);
    }

    /** Unique rooms, no duplicates. Read-only - the returned set is reused. */
    public Collection<DungeonRoom> getAllRooms() {
        if (uniqueDirty) {
            uniqueRooms.clear();
            uniqueRooms.addAll(rooms.values());
            uniqueDirty = false;
        }
        return uniqueRooms;
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
