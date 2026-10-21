// Ported from ADM's p3sim/clear/puzzles/BlazeLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

/** Where the ten blazes of Higher Or Lower hover, in the room's own frame: a spiral of cells around the centre pillar. Pure. */
public final class BlazeLayout {
    private BlazeLayout() {}

    /** The centre pillar's column. */
    public static final int CENTRE = 15;

    /** Blaze k (0 to 9, the lowest first) hangs 4 blocks above blaze k - 1. */
    public static final int SPACING = 4;

    /** Height of the lowest blaze: Lower Blaze 27, Higher Blaze 77. */
    public static int firstY(boolean higher) { return higher ? 77 : 27; }

    /** The column offsets (dx, dz) from the pillar by k mod 4, a quarter turn at a time, 2 blocks out. */
    private static final List<Pair<Integer, Integer>> RING = List.of(Pair.of(0, -2), Pair.of(-2, 0), Pair.of(0, 2), Pair.of(2, 0));

    public static class Cell {
        public final int x;
        public final int y;
        public final int z;

        public Cell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** The cell of blaze k (0 to 9): the blaze stands in this cell's corner, its name tag 2 blocks above. */
    public static Cell cell(int k, boolean higher) {
        Pair<Integer, Integer> d = RING.get(k % 4);
        return new Cell(CENTRE + d.first, firstY(higher) + SPACING * k, CENTRE + d.second);
    }

    /** How far above a blaze its name tag stands. */
    public static final double TAG_HEIGHT = 2.0;
}
