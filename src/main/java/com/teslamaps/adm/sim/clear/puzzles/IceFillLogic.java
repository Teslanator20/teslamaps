// Ported from ADM's p3sim/clear/puzzles/IceFillLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ice Fill as a state machine over three floors (feet level 70, 71, 72 in the room's frame). The first step on a floor may be any
 * cell of its patch; every later one must be next to the previous and on an uncovered cell, else the floor being filled collapses.
 * Odin's patterns (see OdinTables) let the sim check that a room is one Odin can solve. Positions are in the room's own frame.
 */
public class IceFillLogic {
    public final List<Set<Cell>> floors;

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

    /** What a step did. */
    public enum Outcome {
        /** Not on the active floor's patch (or no floor left): nothing happens. */
        NOTHING,
        /** The cell is covered now. */
        COVERED,
        /** The cell was already covered: the floor collapses. */
        WRONG_BLOCK,
        /** The cell is not next to the last one: the floor collapses. */
        DIAGONAL,
    }

    /** The result of step: what happened, and for COVERED the cell and whether it finished its floor. */
    public static class Step {
        public final Outcome outcome;
        public final Cell cell;
        public final boolean floorDone;

        public Step(Outcome outcome, Cell cell, boolean floorDone) {
            this.outcome = outcome;
            this.cell = cell;
            this.floorDone = floorDone;
        }

        public Step(Outcome outcome, Cell cell) { this(outcome, cell, false); }

        public Step(Outcome outcome) { this(outcome, null, false); }
    }

    @FunctionalInterface
    public interface CellPredicate {
        boolean test(int x, int y, int z);
    }

    private final List<Set<Cell>> covered;

    /** The cell stepped on last on the active floor, or null at the start of a floor (and after a collapse). */
    public Cell last = null;

    public IceFillLogic(List<Set<Cell>> floors) {
        this.floors = floors;
        List<Set<Cell>> c = new ArrayList<>();
        for (int i = 0; i < floors.size(); i++) c.add(new HashSet<>());
        this.covered = c;
    }

    public int coveredCount(int f) { return covered.get(f).size(); }

    /** A floor with nothing to cover counts as done. */
    public boolean floorDone(int f) { return covered.get(f).size() >= floors.get(f).size(); }

    /** The first floor not done, or -1 when all are. */
    public int activeFloor() {
        for (int i = 0; i < floors.size(); i++) if (!floorDone(i)) return i;
        return -1;
    }

    public boolean solved() {
        boolean any = false;
        for (Set<Cell> f : floors) if (!f.isEmpty()) { any = true; break; }
        return any && activeFloor() == -1;
    }

    /** Someone stands with their feet at (x, y, z), having just moved onto that cell. */
    public Step step(int x, int y, int z) {
        int f = activeFloor();
        if (f < 0) return new Step(Outcome.NOTHING);
        Cell c = new Cell(x, y, z);
        if (!floors.get(f).contains(c)) return new Step(Outcome.NOTHING);
        if (covered.get(f).contains(c)) return new Step(Outcome.WRONG_BLOCK, c);
        Cell l = last;
        if (l != null && Math.abs(l.x - c.x) + Math.abs(l.z - c.z) != 1) return new Step(Outcome.DIAGONAL, c);
        covered.get(f).add(c);
        boolean done = floorDone(f);
        last = done ? null : c;
        return new Step(Outcome.COVERED, c, done);
    }

    /** The active floor falls: its covered cells are forgotten and the next step may be anywhere. Done floors are untouched. */
    public void collapse() {
        int f = activeFloor();
        if (f >= 0) covered.get(f).clear();
        last = null;
    }

    public void reset() {
        for (Set<Cell> s : covered) s.clear();
        last = null;
    }

    /** Feet level of the three floors. */
    public static final List<Integer> FLOOR_Y = List.of(70, 71, 72);

    /** Every column of floor f with ice under it, whatever stands at feet level: the cells a collapse takes away. */
    public static Set<Cell> scanIce(int f, int size, CellPredicate isIce) {
        int y = FLOOR_Y.get(f);
        Set<Cell> out = new LinkedHashSet<>();
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) if (isIce.test(x, y - 1, z)) out.add(new Cell(x, y, z));
        return out;
    }

    public static Set<Cell> scanIce(int f, CellPredicate isIce) { return scanIce(f, 31, isIce); }

    /** The patch of floor f: every column whose ice (feet level minus one) is ice and whose feet-level cell is air. */
    public static Set<Cell> scan(int f, int size, CellPredicate isIce, CellPredicate isAir) {
        int y = FLOOR_Y.get(f);
        Set<Cell> out = new LinkedHashSet<>();
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) if (isIce.test(x, y - 1, z) && isAir.test(x, y, z)) out.add(new Cell(x, y, z));
        return out;
    }

    public static Set<Cell> scan(int f, CellPredicate isIce, CellPredicate isAir) { return scan(f, 31, isIce, isAir); }

    /** Odin's choice per floor: the index of the first pattern whose marker pair reads (air, not air), or -1 when none does. */
    public static List<Integer> patterns(List<List<OdinTables.IcePattern>> table, CellPredicate isAir) {
        List<Integer> out = new ArrayList<>();
        for (List<OdinTables.IcePattern> ps : table) {
            int idx = -1;
            for (int i = 0; i < ps.size(); i++) {
                OdinTables.IcePattern it = ps.get(i);
                if (isAir.test(it.identifierAir[0], it.identifierAir[1], it.identifierAir[2])
                        && !isAir.test(it.identifierSolid[0], it.identifierSolid[1], it.identifierSolid[2])) { idx = i; break; }
            }
            out.add(idx);
        }
        return out;
    }

    public static List<Integer> patterns(CellPredicate isAir) { return patterns(OdinTables.iceFloors(), isAir); }
}
