package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/SpawnFinder.kt

import com.teslamaps.adm.sim.pack.DungeonFrame;
import java.util.ArrayList;
import java.util.List;

public final class SpawnFinder {
    private SpawnFinder() {}

    public static final int SEARCH_TOP = 76;
    public static final int SEARCH_BOTTOM = 62;
    public static final int MAIN_LEVEL = 69;
    public static final int RADIUS = 13;

    public static Integer standY(RoomInstance room, int x, int z) { return standY(room, x, z, false); }

    public static Integer standY(RoomInstance room, int x, int z, boolean onlyMainLevel) {
        List<Integer> levels = new ArrayList<>();
        levels.add(MAIN_LEVEL);
        if (!onlyMainLevel) {
            for (int y = MAIN_LEVEL + 1; y <= SEARCH_TOP; y++) levels.add(y);
            for (int y = MAIN_LEVEL - 1; y >= SEARCH_BOTTOM; y--) levels.add(y);
        }
        for (int y : levels) {
            if (room.isSolid(x, y - 1, z) && !room.isSolid(x, y, z) && !room.isSolid(x, y + 1, z)) return y;
        }
        return null;
    }

    public static StandSpot find(RoomInstance room, int tx, int tz) {
        StandSpot s = findAt(room, tx, tz, true);
        return s != null ? s : findAt(room, tx, tz, false);
    }

    private static final int[][] MARGIN = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static StandSpot findAt(RoomInstance room, int tx, int tz, boolean onlyMainLevel) {
        int cx = DungeonFrame.tileCentre(tx);
        int cz = DungeonFrame.tileCentre(tz);
        for (int r = 0; r <= RADIUS; r++) {
            // the ring at Chebyshev distance r, nearest first by Euclid
            ArrayList<int[]> ring = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (Math.max(Math.abs(dx), Math.abs(dz)) == r) ring.add(new int[]{dx, dz});
            ring.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
            for (int[] d : ring) {
                int dx = d[0];
                int dz = d[1];
                Integer y = standY(room, cx + dx, cz + dz, onlyMainLevel);
                if (y == null) continue;
                // one free block of margin on each side
                boolean blocked = false;
                for (int[] m : MARGIN) if (room.isSolid(cx + dx + m[0], y, cz + dz + m[1])) { blocked = true; break; }
                if (blocked) continue;
                return new StandSpot(cx + dx + 0.5, y.doubleValue(), cz + dz + 0.5);
            }
        }
        return null;
    }
}
