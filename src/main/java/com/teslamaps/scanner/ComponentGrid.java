package com.teslamaps.scanner;

import com.teslamaps.map.DungeonRoom;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the 6x6 grid of dungeon room components.
 * Each grid cell can contain a room or be empty.
 */
public class ComponentGrid {
    // Dungeon constants
    public static final int DUNGEON_MIN_X = -200;
    public static final int DUNGEON_MAX_X = -10;
    public static final int DUNGEON_MIN_Z = -200;
    public static final int DUNGEON_MAX_Z = -10;

    public static final int ROOM_SIZE = 31;      // Room is 31 blocks
    public static final int DOOR_SIZE = 1;       // Door is 1 block
    public static final int TOTAL_SIZE = 32;     // Room + door = 32 blocks
    public static final int HALF_ROOM_SIZE = 15; // Math.floor(31/2)

    public static final int GRID_SIZE = 6;       // 6x6 component grid

    // Grid storage: gridX * 6 + gridZ -> Room
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
        // Return unique rooms only (multi-component rooms are stored at multiple positions)
        return new java.util.HashSet<>(rooms.values());
    }

    public boolean hasRoom(int gridX, int gridZ) {
        return getRoom(gridX, gridZ) != null;
    }

    public boolean isValidGridPos(int gridX, int gridZ) {
        return gridX >= 0 && gridX < GRID_SIZE && gridZ >= 0 && gridZ < GRID_SIZE;
    }

    /**
     * Convert world coordinates to grid position.
     * Returns [gridX, gridZ] or null if outside dungeon bounds.
     */
    public static int[] worldToGrid(double worldX, double worldZ) {
        if (worldX < DUNGEON_MIN_X || worldX > DUNGEON_MAX_X ||
                worldZ < DUNGEON_MIN_Z || worldZ > DUNGEON_MAX_Z) {
            return null;
        }

        int gridX = (int) ((worldX - DUNGEON_MIN_X) / TOTAL_SIZE);
        int gridZ = (int) ((worldZ - DUNGEON_MIN_Z) / TOTAL_SIZE);

        // Clamp to valid range
        gridX = Math.max(0, Math.min(GRID_SIZE - 1, gridX));
        gridZ = Math.max(0, Math.min(GRID_SIZE - 1, gridZ));

        return new int[]{gridX, gridZ};
    }

    /**
     * Convert grid position to world coordinates (room center).
     */
    public static int[] gridToWorld(int gridX, int gridZ) {
        int worldX = DUNGEON_MIN_X + HALF_ROOM_SIZE + (gridX * TOTAL_SIZE);
        int worldZ = DUNGEON_MIN_Z + HALF_ROOM_SIZE + (gridZ * TOTAL_SIZE);
        return new int[]{worldX, worldZ};
    }

    /**
     * Convert grid position to room corner coordinates.
     */
    public static int[] gridToWorldCorner(int gridX, int gridZ) {
        int worldX = DUNGEON_MIN_X + (gridX * TOTAL_SIZE);
        int worldZ = DUNGEON_MIN_Z + (gridZ * TOTAL_SIZE);
        return new int[]{worldX, worldZ};
    }

    /**
     * Get all 36 grid positions as world coordinates (room centers).
     */
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
}
