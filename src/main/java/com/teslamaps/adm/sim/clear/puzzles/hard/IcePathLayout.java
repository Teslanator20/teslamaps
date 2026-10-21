package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/IcePathModel.kt

import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.util.Pair;
import java.util.HashSet;
import java.util.function.Supplier;

/** Where the Ice Path's cells are, read from the room's blocks (canonical frame). */
public class IcePathLayout {
    public final Supplier<IcePathModel> model;
    public final int floorY;
    public final Pair<Integer, Integer> start;

    public IcePathLayout(Supplier<IcePathModel> model, int floorY, Pair<Integer, Integer> start) {
        this.model = model;
        this.floorY = floorY;
        this.start = start;
    }

    public static final int ARENA_MIN_X = 7; public static final int ARENA_MAX_X = 23;
    public static final int ARENA_MIN_Z = 8; public static final int ARENA_MAX_Z = 24;
    /** The exit corridor's columns, inclusive. */
    public static final int CORRIDOR_X_MIN = 14; public static final int CORRIDOR_X_MAX = 16;
    public static final int CORRIDOR_MAX_Z = 28;
    public static final int START_X = 8; public static final int START_Z = 9;

    /** The fish's feet stand on top of the ice at this y (66 is the ice sheet). */
    public static final int DEFAULT_FLOOR_Y = 67;

    private static boolean inArena(int x, int z) { return x >= ARENA_MIN_X && x <= ARENA_MAX_X && z >= ARENA_MIN_Z && z <= ARENA_MAX_Z; }

    private static boolean inCorridor(int x, int z) { return x >= CORRIDOR_X_MIN && x <= CORRIDOR_X_MAX && z >= ARENA_MAX_Z + 1 && z <= CORRIDOR_MAX_Z; }

    /**
     * Reads the room: a cell is blocked when the block at the fish's height is not air, or outside the arena and the corridor. Traps: a cell
     * beside every hopper with fire above it (not the corridor's), at the nearest arena cell (a guess).
     */
    public static IcePathLayout read(RoomVolume volume) {
        int floorY = DEFAULT_FLOOR_Y;
        IcePathModel.Solid blocked = (x, z) -> !(inArena(x, z) || inCorridor(x, z)) || !volume.isAir(x, floorY, z);
        HashSet<Long> traps = new HashSet<>();
        for (int y = volume.y0; y < volume.y0 + volume.h; y++) for (int z = 0; z < volume.d; z++) for (int x = 0; x < volume.w; x++) {
            if (!volume.stateAt(x, y, z).startsWith("minecraft:hopper")) continue;
            if (!volume.stateAt(x, y + 1, z).startsWith("minecraft:fire")) continue;
            if (inCorridor(x, Math.clamp(z, ARENA_MIN_Z, CORRIDOR_MAX_Z))) continue;
            traps.add(IcePathModel.key(Math.clamp(x, ARENA_MIN_X, ARENA_MAX_X), Math.clamp(z, ARENA_MIN_Z, ARENA_MAX_Z)));
        }
        return new IcePathLayout(() -> new IcePathModel(blocked, START_X, START_Z, CORRIDOR_X_MIN, CORRIDOR_X_MAX, ARENA_MAX_Z, traps), floorY, Pair.of(START_X, START_Z));
    }
}
