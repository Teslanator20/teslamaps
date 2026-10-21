package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/WaterSim.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Water as the 1.8 server flows it (BlockDynamicLiquid.updateTick): levels 0 source, 1..7 spread, 8 falling, 8 + n fell from a sideways
 * flow; every update five ticks after the change that asked for it. Cells outside the box are solid. Pure, server thread only.
 */
public class WaterSim {
    public final int x0;
    public final int y0;
    public final int z0;
    public final int w;
    public final int h;
    public final int d;

    private final boolean[] solid;
    private final byte[] level;
    private final boolean[] scheduled;
    private final TreeMap<Integer, ArrayList<Integer>> buckets = new TreeMap<>();

    /** Ticks this simulation has run. */
    public int tickCount = 0;

    /** One change of a water cell: its new level, or -1 when the water is gone. */
    public static class Change {
        public final int x;
        public final int y;
        public final int z;
        public final int level;

        public Change(int x, int y, int z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }

    private final ArrayList<Change> changes = new ArrayList<>();

    public WaterSim(int x0, int y0, int z0, int w, int h, int d) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.w = w;
        this.h = h;
        this.d = d;
        this.solid = new boolean[w * h * d];
        this.level = new byte[w * h * d];
        Arrays.fill(this.level, (byte) -1);
        this.scheduled = new boolean[w * h * d];
    }

    private int idx(int x, int y, int z) { return ((y - y0) * d + (z - z0)) * w + (x - x0); }

    private boolean inside(int x, int y, int z) { return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h && z >= z0 && z < z0 + d; }

    public boolean isSolid(int x, int y, int z) { return !inside(x, y, z) || solid[idx(x, y, z)]; }

    /** The water level of a cell (0 to 15), or -1 when it holds no water. */
    public int water(int x, int y, int z) { return inside(x, y, z) ? level[idx(x, y, z)] : -1; }

    /** Water already there when the board starts: it does not flow until something next to it changes. */
    public void setStaticWater(int x, int y, int z, int lvl) {
        if (!inside(x, y, z)) return;
        int i = idx(x, y, z);
        solid[i] = false;
        level[i] = (byte) lvl;
    }

    /** Sets a cell solid or free; a solid cell drowns its water without a change record, a freed cell wakes the water around it. */
    public void setSolid(int x, int y, int z, boolean isSolid) {
        if (!inside(x, y, z)) return;
        int i = idx(x, y, z);
        solid[i] = isSolid;
        if (isSolid) level[i] = -1;
        notifyAround(x, y, z);
    }

    /** Puts a source block in a free cell (the intake). */
    public void placeSource(int x, int y, int z) {
        if (!inside(x, y, z) || solid[idx(x, y, z)]) return;
        setWater(x, y, z, 0);
    }

    private void setWater(int x, int y, int z, int lvl) {
        int i = idx(x, y, z);
        level[i] = (byte) lvl;
        changes.add(new Change(x, y, z, lvl));
        schedule(x, y, z);
        notifyAround(x, y, z);
    }

    private void clearWater(int x, int y, int z) {
        int i = idx(x, y, z);
        level[i] = -1;
        changes.add(new Change(x, y, z, -1));
        notifyAround(x, y, z);
    }

    private void schedule(int x, int y, int z) {
        if (!inside(x, y, z)) return;
        int i = idx(x, y, z);
        if (scheduled[i]) return;
        scheduled[i] = true;
        buckets.computeIfAbsent(tickCount + TICK_RATE, k -> new ArrayList<>()).add(i);
    }

    private void notifyAround(int x, int y, int z) {
        for (int[] k : NEIGHBOURS) {
            int nx = x + k[0]; int ny = y + k[1]; int nz = z + k[2];
            if (inside(nx, ny, nz) && level[idx(nx, ny, nz)] >= 0) schedule(nx, ny, nz);
        }
    }

    /** Runs one tick and returns the water changes it made. */
    public List<Change> tick() {
        advance();
        return runDue();
    }

    /** Starts the next tick without running its updates. */
    public void advance() { tickCount++; }

    /** Runs the updates due in the current tick and returns the changes since the last call. */
    public List<Change> runDue() {
        ArrayList<Integer> due = buckets.remove(tickCount);
        if (due != null) {
            for (int i : due) scheduled[i] = false;
            for (int i : due) {
                int x = x0 + i % w; int z = z0 + (i / w) % d; int y = y0 + i / (w * d);
                update(x, y, z);
            }
        }
        return takeChanges();
    }

    /** The changes made since the last call, oldest first. */
    public List<Change> takeChanges() {
        if (changes.isEmpty()) return List.of();
        ArrayList<Change> out = new ArrayList<>(changes);
        changes.clear();
        return out;
    }

    private boolean blocked(int x, int y, int z) { return isSolid(x, y, z); }

    private void update(int x, int y, int z) {
        if (!inside(x, y, z)) return;
        int i = level[idx(x, y, z)];
        if (i < 0) return;
        if (i > 0) {
            int low = -100;
            int sources = 0;
            for (int[] k : SIDES) {
                int dd = water(x + k[0], y, z + k[1]);
                if (dd < 0) continue;
                if (dd == 0) sources++;
                if (dd >= 8) dd = 0;
                if (low < 0 || dd < low) low = dd;
            }
            int next = low + 1;
            if (next >= 8 || low < 0) next = -1;
            int up = water(x, y + 1, z);
            if (up >= 0) next = up >= 8 ? up : up + 8;
            if (sources >= 2 && (blocked(x, y - 1, z) || water(x, y - 1, z) == 0)) next = 0;
            if (next != i) {
                i = next;
                if (next < 0) clearWater(x, y, z); else setWater(x, y, z, next);
            }
        }
        if (canFlowInto(x, y - 1, z)) {
            setWater(x, y - 1, z, i >= 8 ? i : i + 8);
        } else if (i >= 0 && (i == 0 || blocked(x, y - 1, z))) {
            int reach = i + 1;
            if (i >= 8) reach = 1;
            if (reach >= 8) return;
            for (int q : flowDirections(x, y, z)) {
                int nx = x + SIDES[q][0]; int nz = z + SIDES[q][1];
                if (canFlowInto(nx, y, nz)) setWater(nx, y, nz, reach);
            }
        }
    }

    /** Free of both solid and water: the only cells new water is put into. */
    private boolean canFlowInto(int x, int y, int z) { return inside(x, y, z) && !solid[idx(x, y, z)] && level[idx(x, y, z)] < 0; }

    /** The sides water spreads to: the shortest ways down within four blocks, or all open sides. */
    private List<Integer> flowDirections(int x, int y, int z) {
        int best = 1000;
        ArrayList<Integer> out = new ArrayList<>(4);
        for (int q = 0; q < 4; q++) {
            int nx = x + SIDES[q][0]; int nz = z + SIDES[q][1];
            if (blocked(nx, y, nz) || water(nx, y, nz) == 0) continue;
            int cost = blocked(nx, y - 1, nz) ? flowCost(nx, y, nz, 1, q ^ 1) : 0;
            if (cost < best) out.clear();
            if (cost <= best) { out.add(q); best = cost; }
        }
        return out;
    }

    private int flowCost(int x, int y, int z, int depth, int from) {
        int best = 1000;
        for (int q = 0; q < 4; q++) {
            if (q == from) continue;
            int nx = x + SIDES[q][0]; int nz = z + SIDES[q][1];
            if (blocked(nx, y, nz) || water(nx, y, nz) == 0) continue;
            if (!blocked(nx, y - 1, nz)) return depth;
            if (depth < 4) {
                int c = flowCost(nx, y, nz, depth + 1, q ^ 1);
                if (c < best) best = c;
            }
        }
        return best;
    }

    /** Every water cell and every pending update: `x,y,z,level` quadruples and `index,ticksLeft` pairs. */
    public Pair<int[], int[]> snapshot() {
        ArrayList<Integer> cells = new ArrayList<>();
        for (int i = 0; i < level.length; i++) if (level[i] >= 0) {
            cells.add(x0 + i % w); cells.add(y0 + i / (w * d)); cells.add(z0 + (i / w) % d); cells.add((int) level[i]);
        }
        ArrayList<Integer> pend = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<Integer>> e : buckets.entrySet()) for (int i : e.getValue()) { pend.add(i); pend.add(e.getKey() - tickCount); }
        return Pair.of(toArray(cells), toArray(pend));
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    /** Restores what snapshot returned over the current solids. */
    public void restore(int[] cells, int[] pending) {
        Arrays.fill(level, (byte) -1);
        Arrays.fill(scheduled, false);
        buckets.clear();
        changes.clear();
        int k = 0;
        while (k + 3 < cells.length) {
            if (inside(cells[k], cells[k + 1], cells[k + 2])) level[idx(cells[k], cells[k + 1], cells[k + 2])] = (byte) cells[k + 3];
            k += 4;
        }
        k = 0;
        while (k + 1 < pending.length) {
            int i = pending[k];
            if (i >= 0 && i < solid.length && !scheduled[i]) {
                scheduled[i] = true;
                buckets.computeIfAbsent(tickCount + Math.max(pending[k + 1], 1), t -> new ArrayList<>()).add(i);
            }
            k += 2;
        }
    }

    /** Ticks between a change and the update it causes (water's tick rate). */
    public static final int TICK_RATE = 5;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
}
