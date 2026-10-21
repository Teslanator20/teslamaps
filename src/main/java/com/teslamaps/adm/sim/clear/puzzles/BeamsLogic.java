// Ported from ADM's p3sim/clear/puzzles/BeamsLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Creeper Beams as a state machine: pairs of sea lanterns; an arrow near either end turns both to prismarine, for good; every pair
 * done = the chest. The pairs are Odin's table (see OdinTables); only pairs whose two ends are lanterns in the room count.
 * Positions are in the room's own frame. Pure: no Minecraft.
 */
public class BeamsLogic {
    public static final class Cell {
        public final int x;
        public final int y;
        public final int z;

        public Cell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override public boolean equals(Object o) { return o instanceof Cell c && x == c.x && y == c.y && z == c.z; }

        @Override public int hashCode() {
            int r = Integer.hashCode(x);
            r = r * 31 + Integer.hashCode(y);
            r = r * 31 + Integer.hashCode(z);
            return r;
        }

        @Override public String toString() { return "Cell(x=" + x + ", y=" + y + ", z=" + z + ")"; }
    }

    /** What a call changed: lanterns to turn to prismarine, lanterns to turn back to sea lanterns (a reset). */
    public static class Change {
        public final List<Cell> toPrismarine;
        public final List<Cell> toLantern;

        public Change(List<Cell> toPrismarine, List<Cell> toLantern) {
            this.toPrismarine = toPrismarine;
            this.toLantern = toLantern;
        }
    }

    private final Map<Cell, Cell> partner = new HashMap<>();
    private final int pairCount;
    private final Set<Cell> done = new HashSet<>();

    public BeamsLogic(List<Pair<Cell, Cell>> pairs) {
        // A lantern belongs to one pair (the table repeats a pair or two): the first one wins.
        for (Pair<Cell, Cell> p : pairs) {
            Cell a = p.first, b = p.second;
            if (partner.containsKey(a) || partner.containsKey(b) || a.equals(b)) continue;
            partner.put(a, b);
            partner.put(b, a);
        }
        pairCount = partner.size() / 2;
    }

    public int total() { return pairCount; }

    public int solvedPairs() { return done.size() / 2; }

    public boolean solved() { return pairCount > 0 && solvedPairs() == pairCount; }

    /** The lanterns in play. */
    public Set<Cell> lanterns() { return partner.keySet(); }

    /** The other end of the pair c belongs to. */
    public Cell partnerOf(Cell c) { return partner.get(c); }

    /** Whether the pair holding c is already done. */
    public boolean isDone(Cell c) { return done.contains(c); }

    /** The closest lantern of a pair still to do within radius blocks of the point, or null. */
    public Cell lanternNear(double x, double y, double z, double radius) {
        Cell best = null;
        double bestD = 0;
        for (Cell c : partner.keySet()) {
            if (done.contains(c)) continue;
            double d = dist2(c, x, y, z);
            if (d > radius * radius) continue;
            if (best == null || d < bestD) { best = c; bestD = d; }
        }
        return best;
    }

    public Cell lanternNear(double x, double y, double z) { return lanternNear(x, y, z, ARROW_RADIUS); }

    private double dist2(Cell c, double x, double y, double z) {
        double dx = c.x + 0.5 - x, dy = c.y + 0.5 - y, dz = c.z + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** The lantern at c was hit: its whole pair turns to prismarine at once. */
    public Change hit(Cell c) {
        Cell p = partner.get(c);
        if (p == null) return NONE;
        if (done.contains(c)) return NONE;
        done.add(c);
        done.add(p);
        return new Change(List.of(c, p), List.of());
    }

    /** Back to the start (the lanterns are all lanterns again). */
    public void reset() { done.clear(); }

    /** An arrow within this many blocks of a lantern counts as a hit on its pair. */
    public static final double ARROW_RADIUS = 4.0;
    private static final Change NONE = new Change(List.of(), List.of());

    /** Odin's pairs that exist in the room: isLantern says whether a cell of the room holds a sea lantern. */
    public static List<Pair<Cell, Cell>> pairsIn(List<int[]> table, Predicate<Cell> isLantern) {
        List<Pair<Cell, Cell>> out = new ArrayList<>();
        Set<Set<Cell>> seen = new HashSet<>();
        for (int[] it : table) {
            Cell a = new Cell(it[0], it[1], it[2]);
            Cell b = new Cell(it[3], it[4], it[5]);
            if (!isLantern.test(a) || !isLantern.test(b)) continue;
            if (!seen.add(new HashSet<>(Arrays.asList(a, b)))) continue;
            out.add(Pair.of(a, b));
        }
        return out;
    }

    public static List<Pair<Cell, Cell>> pairsIn(Predicate<Cell> isLantern) { return pairsIn(OdinTables.beams(), isLantern); }
}
