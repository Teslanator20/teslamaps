// Ported from ADM's p3sim/clear/bots/RoomGrid.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The floor of one placed room, as a bot can walk it: for every block column, whether somebody can stand there. World columns, row-major from
 * ([x0], [z0]). Pure. A walker is 0.6 wide.
 */
public class RoomGrid {
    public final int x0;
    public final int z0;
    public final int w;
    public final int d;
    private final boolean[] open;

    public RoomGrid(int x0, int z0, int w, int d, boolean[] open) {
        if (open.length != w * d) throw new IllegalArgumentException("a " + w + "x" + d + " grid needs " + (w * d) + " cells, not " + open.length);
        this.x0 = x0;
        this.z0 = z0;
        this.w = w;
        this.d = d;
        this.open = open;
    }

    private int idx(int x, int z) { return (z - z0) * w + (x - x0); }

    public boolean walkable(int x, int z) { return x >= x0 && z >= z0 && x < x0 + w && z < z0 + d && open[idx(x, z)]; }

    public boolean walkable(Pt p) { return walkable((int) Math.floor(p.x), (int) Math.floor(p.z)); }

    private Integer openCount;
    private int[] cells;
    private int[] label;
    private Integer mainSize;
    private Integer mainLabel;

    /** How many columns can be walked. */
    public synchronized int openCount() {
        if (openCount == null) {
            int n = 0;
            for (boolean b : open) if (b) n++;
            openCount = n;
        }
        return openCount;
    }

    private synchronized int[] cells() {
        if (cells == null) {
            int[] out = new int[openCount()];
            int k = 0;
            for (int i = 0; i < open.length; i++) if (open[i]) out[k++] = i;
            cells = out;
        }
        return cells;
    }

    /** Connected piece of the floor (8-way, no cutting corners) each column belongs to, 0 for a column that cannot be walked. */
    private synchronized int[] label() {
        if (label == null) {
            int[] lab = new int[open.length];
            int next = 0;
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            for (int start = 0; start < open.length; start++) {
                if (!open[start] || lab[start] != 0) continue;
                next++;
                lab[start] = next;
                stack.add(start);
                while (!stack.isEmpty()) {
                    int c = stack.removeLast();
                    for (Pair<Integer, Double> n : neighbours(c)) if (lab[n.first] == 0) { lab[n.first] = next; stack.add(n.first); }
                }
            }
            label = lab;
        }
        return label;
    }

    private Map<Integer, Integer> labelCounts() {
        LinkedHashMap<Integer, Integer> counts = new LinkedHashMap<>();
        for (int l : label()) if (l > 0) counts.merge(l, 1, Integer::sum);
        return counts;
    }

    /** How many columns the biggest connected piece of floor has. */
    public synchronized int mainSize() {
        if (mainSize == null) {
            int best = 0;
            boolean any = false;
            for (int v : labelCounts().values()) { if (!any || v > best) best = v; any = true; }
            mainSize = any ? best : 0;
        }
        return mainSize;
    }

    /** The label of the biggest connected piece of floor (0 when there is no floor). */
    private synchronized int mainLabel() {
        if (mainLabel == null) {
            int bestKey = 0, bestVal = 0;
            boolean any = false;
            for (Map.Entry<Integer, Integer> e : labelCounts().entrySet()) {
                if (!any || e.getValue() > bestVal) { bestVal = e.getValue(); bestKey = e.getKey(); }
                any = true;
            }
            mainLabel = any ? bestKey : 0;
        }
        return mainLabel;
    }

    /** Whether the column [p] is on the biggest connected piece of floor. */
    public boolean onMain(Pt p) { return walkable(p) && label()[idx((int) Math.floor(p.x), (int) Math.floor(p.z))] == mainLabel(); }

    /** The open neighbours of cell [c] with the cost of the step; a diagonal needs both columns beside it open. */
    private List<Pair<Integer, Double>> neighbours(int c) {
        int cx = c % w, cz = c / w;
        ArrayList<Pair<Integer, Double>> out = new ArrayList<>(8);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            int nx = cx + dx, nz = cz + dz;
            if (nx < 0 || nz < 0 || nx >= w || nz >= d) continue;
            int n = nz * w + nx;
            if (!open[n]) continue;
            if (dx != 0 && dz != 0 && (!open[cz * w + nx] || !open[nz * w + cx])) continue;
            out.add(Pair.of(n, dx != 0 && dz != 0 ? SQRT2 : 1.0));
        }
        return out;
    }

    /** The middle of the walkable column [p] stands in, or of the nearest walkable column within [radius] blocks; null when there is none. */
    public Pt nearest(Pt p, int radius) {
        int bx = (int) Math.floor(p.x), bz = (int) Math.floor(p.z);
        int[] lab = label();
        int main = mainLabel();
        Pt best = null;
        double bestD = Double.MAX_VALUE;
        Pt bestMain = null;
        double bestMainD = Double.MAX_VALUE;
        for (int dz = -radius; dz <= radius; dz++) for (int dx = -radius; dx <= radius; dx++) {
            if (!walkable(bx + dx, bz + dz)) continue;
            Pt c = new Pt(bx + dx + 0.5, bz + dz + 0.5);
            double dist = c.dist(p);
            if (dist < bestD) { bestD = dist; best = c; }
            if (dist < bestMainD && lab[idx(bx + dx, bz + dz)] == main) { bestMainD = dist; bestMain = c; }
        }
        return bestMain != null && (best == null || lab[idx((int) Math.floor(best.x), (int) Math.floor(best.z))] != main) && bestMainD <= radius ? bestMain : best;
    }

    public Pt nearest(Pt p) { return nearest(p, 10); }

    /** The middle of the column of piece [lab] of the floor that is nearest to [p], or null when the piece has none. */
    private Pt nearestIn(Pt p, int lab) {
        int[] labels = label();
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int c : cells()) {
            if (labels[c] != lab) continue;
            double dx = x0 + c % w + 0.5 - p.x, dz = z0 + c / w + 0.5 - p.z;
            double dist = dx * dx + dz * dz;
            if (dist < bestD) { bestD = dist; best = c; }
        }
        return best < 0 ? null : new Pt(x0 + best % w + 0.5, z0 + best / w + 0.5);
    }

    /** Whether a walker can go straight from [a] to [b]. */
    public boolean lineClear(Pt a, Pt b) {
        double len = a.dist(b);
        int steps = Math.max(1, (int) Math.ceil(len / 0.25));
        for (int i = 0; i <= steps; i++) {
            double f = (double) i / steps;
            double x = a.x + (b.x - a.x) * f, z = a.z + (b.z - a.z) * f;
            if (!walkable((int) Math.floor(x - CLEAR), (int) Math.floor(z - CLEAR)) || !walkable((int) Math.floor(x + CLEAR), (int) Math.floor(z - CLEAR))
                    || !walkable((int) Math.floor(x - CLEAR), (int) Math.floor(z + CLEAR)) || !walkable((int) Math.floor(x + CLEAR), (int) Math.floor(z + CLEAR))) return false;
        }
        return true;
    }

    /** A random walkable place that [from] can get to, between [minDist] and [maxDist] blocks from it when one is found in a few tries, else any. */
    public Pt randomPoint(Pt from, KRandom random, double minDist, double maxDist) {
        Pt start = nearest(from);
        if (start == null) return null;
        int[] labels = label();
        int[] cs = cells();
        int lab = labels[idx((int) Math.floor(start.x), (int) Math.floor(start.z))];
        Pt any = null;
        for (int r = 0; r < 24; r++) {
            int c = cs[random.nextInt(cs.length)];
            if (labels[c] != lab) continue;
            Pt p = new Pt(x0 + c % w + 0.5, z0 + c / w + 0.5);
            any = p;
            double dist = p.dist(from);
            if (dist >= minDist && dist <= maxDist) return p;
        }
        return any;
    }

    public Pt randomPoint(Pt from, KRandom random, double minDist) { return randomPoint(from, random, minDist, Double.MAX_VALUE); }

    public Pt randomPoint(Pt from, KRandom random) { return randomPoint(from, random, 0.0, Double.MAX_VALUE); }

    /** The way from [a] to [b] round whatever is in between: the points to pass after [a], the last one being [b] (or the nearest reachable place). */
    public List<Pt> path(Pt a, Pt b) {
        Pt from = nearest(a);
        if (from == null) return null;
        int[] labels = label();
        int lab = labels[idx((int) Math.floor(from.x), (int) Math.floor(from.z))];
        Pt to = walkable(b) && labels[idx((int) Math.floor(b.x), (int) Math.floor(b.z))] == lab ? b : nearestIn(b, lab);
        if (to == null) return null;
        if (lineClear(from, to)) return new ArrayList<>(List.of(to));
        int s = idx((int) Math.floor(from.x), (int) Math.floor(from.z));
        int g = idx((int) Math.floor(to.x), (int) Math.floor(to.z));
        double[] cost = new double[open.length];
        Arrays.fill(cost, Double.MAX_VALUE);
        int[] prev = new int[open.length];
        Arrays.fill(prev, -1);
        int gx = g % w, gz = g / w;
        PriorityQueue<Pair<Double, Integer>> queue = new PriorityQueue<>((p1, p2) -> Double.compare(p1.first, p2.first));
        cost[s] = 0.0;
        queue.add(Pair.of(h(s, gx, gz), s));
        while (!queue.isEmpty()) {
            Pair<Double, Integer> top = queue.poll();
            double f = top.first;
            int c = top.second;
            if (c == g) break;
            if (f > cost[c] + h(c, gx, gz) + 1e-9) continue;
            for (Pair<Integer, Double> ns : neighbours(c)) {
                int n = ns.first;
                double nc = cost[c] + ns.second;
                if (nc < cost[n]) { cost[n] = nc; prev[n] = c; queue.add(Pair.of(nc + h(n, gx, gz), n)); }
            }
        }
        if (prev[g] < 0 && s != g) return null;
        ArrayList<Pt> cellsOnPath = new ArrayList<>();
        int c = g;
        while (c != s && c >= 0) { cellsOnPath.add(new Pt(x0 + c % w + 0.5, z0 + c / w + 0.5)); c = prev[c]; }
        cellsOnPath.add(from);
        Collections.reverse(cellsOnPath);
        ArrayList<Pt> out = new ArrayList<>();
        int i = 0;
        while (i < cellsOnPath.size() - 1) {
            int j = cellsOnPath.size() - 1;
            while (j > i + 1 && !lineClear(cellsOnPath.get(i), cellsOnPath.get(j))) j--;
            out.add(cellsOnPath.get(j));
            i = j;
        }
        if (out.isEmpty() || out.get(out.size() - 1).dist(to) > 1e-6) out.add(to);
        else out.set(out.size() - 1, to);
        return out;
    }

    private double h(int c, int gx, int gz) {
        int dx = Math.abs(c % w - gx), dz = Math.abs(c / w - gz);
        return (dx + dz) + (SQRT2 - 2) * Math.min(dx, dz);
    }

    private static final double SQRT2 = Math.sqrt(2.0);

    /** Half the width of a walker. */
    public static final double CLEAR = 0.3;
}
