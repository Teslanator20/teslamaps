// Ported from ADM's p3sim/run/ChunkWindow.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;

public final class ChunkWindow {
    private ChunkWindow() {}

    public static boolean tracked(int dx, int dz, int distance) {
        long x = Math.max(0, Math.abs(dx) - 2); long z = Math.max(0, Math.abs(dz) - 2);
        return x * x + z * z < (long) distance * distance;
    }

    public static int count(int distance) {
        int r = distance + 3;
        int n = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (tracked(dx, dz, distance)) n++;
        return n;
    }

    public static int width(int distance) {
        for (int i = distance + 3; i >= 0; i--) if (tracked(i, 0, distance)) return 2 * i + 1;
        throw new NoSuchElementException("Collection contains no element matching the predicate.");
    }

    public static List<Pair<Integer, Integer>> leftBehind(int loginDistance, BiPredicate<Integer, Integer> kept) {
        int r = loginDistance + 3;
        ArrayList<Pair<Integer, Integer>> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (tracked(dx, dz, loginDistance) && !kept.test(dx, dz)) out.add(Pair.of(dx, dz));
        return out;
    }
}
