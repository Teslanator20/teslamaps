package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/IcePathModel.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * The Ice Path as a state machine. A silverfish stands in the middle of a cell on ice; a hit sends it sliding away along the nearer axis
 * at SPEED blocks a tick until the next cell is blocked. Into the exit corridor = solved, into a trap = failed. Cells are canonical (x, z). Pure.
 */
public class IcePathModel {
    @FunctionalInterface
    public interface Solid { boolean test(int x, int z); }

    public enum Phase { IDLE, MOVING, SOLVED, TRAPPED }

    public enum Hit { STARTED, WHILE_MOVING, BLOCKED, IGNORED }

    private final Solid solid;
    public final int startX;
    public final int startZ;
    /** The corridor columns (inclusive) the fish must be sliding south in, and the row where the goal counts. */
    private final int goalXMin;
    private final int goalXMax;
    private final int goalZ;
    private final Set<Long> traps;

    public Phase phase = Phase.IDLE;
    public int cellX;
    public int cellZ;

    private int dx = 0;
    private int dz = 0;
    private double progress = 0.0;

    public IcePathModel(Solid solid, int startX, int startZ, int goalXMin, int goalXMax, int goalZ, Set<Long> traps) {
        this.solid = solid;
        this.startX = startX;
        this.startZ = startZ;
        this.goalXMin = goalXMin;
        this.goalXMax = goalXMax;
        this.goalZ = goalZ;
        this.traps = traps;
        this.cellX = startX;
        this.cellZ = startZ;
    }

    public IcePathModel(Solid solid, int startX, int startZ, int goalXMin, int goalXMax, int goalZ) {
        this(solid, startX, startZ, goalXMin, goalXMax, goalZ, Collections.emptySet());
    }

    /** The fish's position, block coordinates. */
    public double x() { return cellX + 0.5 + dx * progress; }

    public double z() { return cellZ + 0.5 + dz * progress; }

    /** Heading in x and z while moving (0, 0 when still). */
    public int headingX() { return phase == Phase.MOVING ? dx : 0; }

    public int headingZ() { return phase == Phase.MOVING ? dz : 0; }

    private boolean inGoalX(int x) { return x >= goalXMin && x <= goalXMax; }

    /** Puts the fish back on its start cell, standing. */
    public void reset() { phase = Phase.IDLE; cellX = startX; cellZ = startZ; dx = 0; dz = 0; progress = 0.0; }

    /** A hit by someone standing at (fromX, fromZ): the fish slides away along the nearer axis. */
    public Hit hit(double fromX, double fromZ) {
        if (phase == Phase.MOVING) return Hit.WHILE_MOVING;
        if (phase != Phase.IDLE) return Hit.IGNORED;
        double ax = x() - fromX; double az = z() - fromZ;
        int sx; int sz;
        if (Math.abs(ax) >= Math.abs(az)) { sx = ax >= 0 ? 1 : -1; sz = 0; } else { sx = 0; sz = az >= 0 ? 1 : -1; }
        if (solid.test(cellX + sx, cellZ + sz)) return Hit.BLOCKED;
        dx = sx; dz = sz; progress = 0.0; phase = Phase.MOVING;
        return Hit.STARTED;
    }

    /** One tick of sliding; returns the phase afterwards (SOLVED and TRAPPED are final until reset). */
    public Phase tick() {
        if (phase != Phase.MOVING) return phase;
        progress += SPEED;
        while (progress >= 1.0 && phase == Phase.MOVING) {
            progress -= 1.0;
            cellX += dx; cellZ += dz;
            if (dz > 0 && inGoalX(cellX) && cellZ >= goalZ) { phase = Phase.SOLVED; settle(); return phase; }
            if (traps.contains(key(cellX, cellZ))) { phase = Phase.TRAPPED; settle(); return phase; }
            if (solid.test(cellX + dx, cellZ + dz)) { phase = Phase.IDLE; settle(); }
        }
        return phase;
    }

    private void settle() { dx = 0; dz = 0; progress = 0.0; }

    /** The slides that solve it from the start, shortest first, as the cells it stops on, or null if there is none. */
    public List<Pair<Integer, Integer>> solution() {
        Pair<Integer, Integer> start = Pair.of(startX, startZ);
        HashMap<Pair<Integer, Integer>, Pair<Integer, Integer>> prev = new HashMap<>();
        prev.put(start, null);
        ArrayDeque<Pair<Integer, Integer>> queue = new ArrayDeque<>();
        queue.add(start);
        Pair<Integer, Integer> goal = null;
        while (!queue.isEmpty() && goal == null) {
            Pair<Integer, Integer> p = queue.removeFirst();
            for (int[] d : DIRS) {
                int sx = d[0], sz = d[1];
                int x = p.first; int z = p.second; int n = 0; boolean reached = false;
                while (!solid.test(x + sx, z + sz)) {
                    x += sx; z += sz; n++;
                    if (traps.contains(key(x, z))) { n = 0; break; }
                    if (sz > 0 && inGoalX(x) && z >= goalZ) { reached = true; break; }
                    if (n > 64) break;
                }
                if (n == 0) continue;
                Pair<Integer, Integer> q = Pair.of(x, z);
                if (prev.containsKey(q)) continue;
                prev.put(q, p);
                if (reached) { goal = q; break; }
                queue.add(q);
            }
        }
        if (goal == null) return null;
        ArrayList<Pair<Integer, Integer>> out = new ArrayList<>();
        Pair<Integer, Integer> c = goal;
        while (c != null) { out.add(c); c = prev.get(c); }
        Collections.reverse(out);
        return new ArrayList<>(out.subList(1, out.size()));
    }

    /** Blocks a tick (steady in every recording). */
    public static final double SPEED = 0.4;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static long key(int x, int z) { return ((long) x << 32) ^ ((long) z & 0xffffffffL); }

    public static final String MSG_MOVING = "You cannot hit the silverfish while it's moving!";
    public static final String MSG_BLOCKED = "You cannot move the silverfish in that direction!";
    public static final String MSG_FAIL = "pushed the silverfish into a Trap! That's a whoopsie!";
}
