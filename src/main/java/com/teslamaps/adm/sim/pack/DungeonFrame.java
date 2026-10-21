// Ported from ADM's p3sim/pack/DungeonFrame.kt
package com.teslamaps.adm.sim.pack;

import com.teslamaps.adm.sim.util.Pair;

public final class DungeonFrame {
    private DungeonFrame() {}

    public static final int ORIGIN = -200;
    public static final int PITCH = 32;
    public static final int TILE_SIZE = 31;
    public static final int TILES = 6;
    public static final int TILE_COUNT = TILES * TILES;

    /** The last block of the dungeon box (tile 5's last block). */
    public static final int DUNGEON_MAX = ORIGIN + PITCH * (TILES - 1) + TILE_SIZE - 1;   // -10

    public static final int ARENA_MIN = -8;

    public static final int DOOR_Y_MIN = 69;
    public static final int DOOR_Y_MAX = 72;

    public static int tileMin(int t) { return ORIGIN + PITCH * t; }

    /** Centre column of tile index [t]: {@code -185 + 32 t}. */
    public static int tileCentre(int t) { return tileMin(t) + 15; }

    public static int tileIndex(double v) {
        return Math.clamp(Math.floorDiv((int) Math.floor(v) - ORIGIN, PITCH), 0, TILES - 1);
    }

    public static Integer roomTileIndexOrNull(double v) {
        int b = (int) Math.floor(v);
        int t = Math.floorDiv(b - ORIGIN, PITCH);
        if (t < 0 || t >= TILES) return null;
        return b - tileMin(t) <= TILE_SIZE - 1 ? t : null;
    }

    public static Tile tileOf(double x, double z) {
        Integer tx = roomTileIndexOrNull(x);
        if (tx == null) return null;
        Integer tz = roomTileIndexOrNull(z);
        if (tz == null) return null;
        return new Tile(tx, tz);
    }

    public static boolean inDungeonBox(int x, int z) {
        return x >= ORIGIN && x <= DUNGEON_MAX && z >= ORIGIN && z <= DUNGEON_MAX;
    }

    /** World (x, z) of the centre of a door slot. */
    public static Pair<Integer, Integer> doorSpot(char axis, int tx, int tz) {
        return switch (axis) {
            case 'E' -> Pair.of(ORIGIN + 31 + PITCH * tx, tileCentre(tz));
            case 'S' -> Pair.of(tileCentre(tx), ORIGIN + 31 + PITCH * tz);
            default -> throw new IllegalArgumentException("door axis must be E or S, not '" + axis + "'");
        };
    }

    public static Pair<Tile, Tile> slotTiles(char axis, int tx, int tz) {
        return switch (axis) {
            case 'E' -> Pair.of(new Tile(tx, tz), new Tile(tx + 1, tz));
            case 'S' -> Pair.of(new Tile(tx, tz), new Tile(tx, tz + 1));
            default -> throw new IllegalArgumentException("door axis must be E or S, not '" + axis + "'");
        };
    }

    public static boolean isSlot(char axis, int tx, int tz) {
        return switch (axis) {
            case 'E' -> tx >= 0 && tx <= TILES - 2 && tz >= 0 && tz < TILES;
            case 'S' -> tx >= 0 && tx < TILES && tz >= 0 && tz <= TILES - 2;
            default -> false;
        };
    }
}
