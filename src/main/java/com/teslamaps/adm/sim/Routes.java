package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Routes.kt

import com.teslamaps.adm.sim.util.Pair;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import net.minecraft.world.phys.Vec3;

public final class Routes {
    private Routes() {}

    public static int gate(int s) { return s; }

    public static int door(int s) { return s + 4; }

    public static final int CORE_DOOR = 4;

    private static final double JOIN_SPEED = 0.9;
    private static final double CLIP_COST = 2.0;
    private static final double ATTACH = 2.0;
    private static final double ATTACH_FAR = 5.0;
    private static final int CELL = 4;

    private static final class Graph {
        final float[] x, y, z, yaw, pitch;
        final String[] held;
        final int[] flags;
        final int[] off, to;
        final float[] ticks;
        final Map<Long, int[]> cells;

        Graph(float[] x, float[] y, float[] z, float[] yaw, float[] pitch, String[] held, int[] flags, int[] off, int[] to, float[] ticks, Map<Long, int[]> cells) {
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch; this.held = held; this.flags = flags;
            this.off = off; this.to = to; this.ticks = ticks;
            this.cells = cells;
        }

        int n() { return x.length; }

        boolean grounded(int i) { return (flags[i] & 1) != 0; }

        int opening(int i) { return (flags[i] >> 1) & 7; }

        boolean clip(int i) { return (flags[i] & 16) != 0; }
    }

    private static volatile Graph loaded = null;
    private static volatile boolean failed = false;

    private static Graph graph() {
        Graph g = loaded;
        if (g != null) return g;
        if (failed) return null;
        synchronized (Routes.class) {
            if (loaded != null) return loaded;
            Graph r;
            try {
                r = read();
            } catch (Throwable t) {
                failed = true;
                Ec.LOG.warn("[p3sim] routes.bin: {}", t.toString());
                r = null;
            }
            loaded = r;
            return r;
        }
    }

    private static Graph read() throws Exception {
        InputStream stream = Routes.class.getResourceAsStream("/assets/teslamaps/adm/p3sim/routes.bin");
        if (stream == null) throw new IllegalStateException("missing from the jar");
        try (DataInputStream inp = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(stream), 1 << 16)))) {
            byte[] magic = new byte[4];
            inp.readFully(magic);
            if (!new String(magic, StandardCharsets.UTF_8).equals("P3R1")) throw new IllegalArgumentException("bad magic");
            int n = inp.readInt();
            float[] x = new float[n], y = new float[n], z = new float[n];
            float[] yaw = new float[n], pitch = new float[n];
            int[] heldIx = new int[n], flags = new int[n];
            for (int i = 0; i < n; i++) {
                x[i] = inp.readFloat(); y[i] = inp.readFloat(); z[i] = inp.readFloat();
                yaw[i] = inp.readUnsignedByte() * 360f / 256f;
                pitch[i] = inp.readUnsignedByte() - 90f;
                heldIx[i] = inp.readUnsignedByte();
                flags[i] = inp.readUnsignedByte();
            }
            String[] names = new String[inp.readInt()];
            for (int i = 0; i < names.length; i++) {
                byte[] b = new byte[inp.readUnsignedShort()];
                inp.readFully(b);
                names[i] = new String(b, StandardCharsets.UTF_8);
            }
            String[] held = new String[n];
            for (int i = 0; i < n; i++) held[i] = heldIx[i] >= 0 && heldIx[i] < names.length ? names[heldIx[i]] : null;
            int[] off = new int[n + 1];
            for (int i = 0; i < n + 1; i++) off[i] = inp.readInt();
            int m = off[n];
            int[] to = new int[m];
            float[] ticks = new float[m];
            for (int e = 0; e < m; e++) { to[e] = inp.readInt(); ticks[e] = inp.readUnsignedShort() / 64f; }
            HashMap<Long, List<Integer>> cells = new HashMap<>();
            for (int i = 0; i < n; i++) cells.computeIfAbsent(cell(x[i], z[i]), k -> new ArrayList<>()).add(i);
            HashMap<Long, int[]> cellArrays = new HashMap<>();
            for (Map.Entry<Long, List<Integer>> en : cells.entrySet()) {
                List<Integer> l = en.getValue();
                int[] a = new int[l.size()];
                for (int i = 0; i < a.length; i++) a[i] = l.get(i);
                cellArrays.put(en.getKey(), a);
            }
            return new Graph(x, y, z, yaw, pitch, held, flags, off, to, ticks, cellArrays);
        }
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) | ((long) cz & 0xffffffffL); }

    private static long cell(double x, double z) { return key(Math.floorDiv((int) Math.floor(x), CELL), Math.floorDiv((int) Math.floor(z), CELL)); }

    public static void preload() { graph(); }

    public static class Route {
        public final double[] x, y, z, t;
        public final float[] yaw, pitch;
        public final String[] held;

        public Route(double[] x, double[] y, double[] z, double[] t, float[] yaw, float[] pitch, String[] held) {
            this.x = x; this.y = y; this.z = z; this.t = t;
            this.yaw = yaw; this.pitch = pitch; this.held = held;
        }

        public double ticks() { return t[t.length - 1]; }

        public int size() { return t.length; }

        public int frameAt(double time) {
            int lo = 0, hi = t.length - 1;
            while (lo < hi) { int mid = (lo + hi + 1) / 2; if (t[mid] <= time) lo = mid; else hi = mid - 1; }
            return lo;
        }

        public Vec3 pos(double time) {
            int i = frameAt(time);
            if (i >= t.length - 1) return new Vec3(x[i], y[i], z[i]);
            double span = t[i + 1] - t[i];
            double f = span <= 1e-6 ? 1.0 : Math.clamp((time - t[i]) / span, 0.0, 1.0);
            return new Vec3(x[i] + (x[i + 1] - x[i]) * f, y[i] + (y[i + 1] - y[i]) * f, z[i] + (z[i + 1] - z[i]) * f);
        }
    }

    private static final class Near {
        final int node;
        final double d;

        Near(int node, double d) { this.node = node; this.d = d; }
    }

    private static List<Near> near(Graph g, Vec3 p, Predicate<Integer> closed) {
        for (double r : new double[] {ATTACH, ATTACH_FAR}) {
            ArrayList<Near> found = new ArrayList<>();
            int c = (int) (r / CELL) + 1;
            int cx = Math.floorDiv((int) Math.floor(p.x), CELL), cz = Math.floorDiv((int) Math.floor(p.z), CELL);
            for (int dx = -c; dx <= c; dx++) for (int dz = -c; dz <= c; dz++) {
                int[] ids = g.cells.get(key(cx + dx, cz + dz));
                if (ids == null) continue;
                for (int i : ids) {
                    if (!g.grounded(i) || g.clip(i)) continue;
                    int o = g.opening(i);
                    if (o != 0 && closed.test(o)) continue;
                    double ddx = g.x[i] - p.x, ddy = g.y[i] - p.y, ddz = g.z[i] - p.z;
                    double d = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    if (d <= r) found.add(new Near(i, d));
                }
            }
            if (!found.isEmpty()) {
                found.sort(Comparator.comparingDouble(it -> it.d));
                return new ArrayList<>(found.subList(0, Math.min(12, found.size())));
            }
        }
        return Collections.emptyList();
    }

    public static Route find(Vec3 from, Vec3 to, Predicate<Integer> closed) {
        Graph g = graph();
        if (g == null) return null;
        List<Near> starts = near(g, from, closed);
        List<Near> ends = near(g, to, closed);
        if (starts.isEmpty() || ends.isEmpty()) return null;
        HashMap<Integer, Double> endCost = new HashMap<>();
        for (Near e : ends) endCost.put(e.node, e.d / JOIN_SPEED);
        double[] dist = new double[g.n()];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        int[] prev = new int[g.n()];
        Arrays.fill(prev, -1);
        boolean[] done = new boolean[g.n()];
        PriorityQueue<Pair<Double, Integer>> pq = new PriorityQueue<>(Comparator.comparing((Pair<Double, Integer> it) -> it.first));
        for (Near s : starts) {
            double c = s.d / JOIN_SPEED;
            if (c < dist[s.node]) { dist[s.node] = c; pq.add(Pair.of(c, s.node)); }
        }
        double best = Double.POSITIVE_INFINITY;
        int bestNode = -1;
        while (!pq.isEmpty()) {
            Pair<Double, Integer> top = pq.poll();
            double c = top.first;
            int i = top.second;
            if (done[i]) continue;
            if (c > best) break;
            done[i] = true;
            Double ec = endCost.get(i);
            if (ec != null) { if (c + ec < best) { best = c + ec; bestNode = i; } }
            for (int e = g.off[i]; e < g.off[i + 1]; e++) {
                int j = g.to[e];
                if (done[j]) continue;
                int o = g.opening(j);
                if (o != 0 && closed.test(o)) continue;
                double nc = c + g.ticks[e] + (g.clip(j) ? CLIP_COST : 0.0);
                if (nc < dist[j]) { dist[j] = nc; prev[j] = i; pq.add(Pair.of(nc, j)); }
            }
        }
        if (bestNode < 0) return null;
        ArrayList<Integer> path = new ArrayList<>();
        int i = bestNode;
        while (i >= 0) { path.add(i); i = prev[i]; }
        Collections.reverse(path);
        int k = path.size() + 2;
        double[] x = new double[k], y = new double[k], z = new double[k], t = new double[k];
        float[] yaw = new float[k], pitch = new float[k];
        Arrays.fill(yaw, Float.NaN);
        Arrays.fill(pitch, Float.NaN);
        String[] held = new String[k];
        x[0] = from.x; y[0] = from.y; z[0] = from.z;
        for (int f = 0; f < path.size(); f++) {
            int node = path.get(f);
            int q = f + 1;
            x[q] = g.x[node]; y[q] = g.y[node]; z[q] = g.z[node];
            yaw[q] = g.yaw[node]; pitch[q] = g.pitch[node]; held[q] = g.held[node];
            t[q] = f == 0 ? dist3(from, x[q], y[q], z[q]) / JOIN_SPEED : t[q - 1] + edgeTicks(g, path.get(f - 1), node);
        }
        x[k - 1] = to.x; y[k - 1] = to.y; z[k - 1] = to.z;
        t[k - 1] = t[k - 2] + dist3(to, x[k - 2], y[k - 2], z[k - 2]) / JOIN_SPEED;
        return new Route(x, y, z, t, yaw, pitch, held);
    }

    private static double edgeTicks(Graph g, int a, int b) {
        for (int e = g.off[a]; e < g.off[a + 1]; e++) if (g.to[e] == b) return g.ticks[e];
        return 1.0;
    }

    private static double dist3(Vec3 p, double x, double y, double z) {
        return Math.sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y) + (p.z - z) * (p.z - z));
    }
}
