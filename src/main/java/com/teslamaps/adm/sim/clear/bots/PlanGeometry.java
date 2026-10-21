// Ported from ADM's p3sim/clear/bots/PlanGeometry.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Where things are in a DungeonPlan, as a walker needs them. Pure. */
public class PlanGeometry {
    public final DungeonPlan plan;

    /** An edge of the door graph: the room on the other side and the door between. */
    public static class Edge {
        public final int to;
        public final int door;

        public Edge(int to, int door) { this.to = to; this.door = door; }
    }

    /** Connected doors only. */
    public final Map<Integer, List<Edge>> adjacency;

    private final Map<Integer, Pt> centres;

    public PlanGeometry(DungeonPlan plan) {
        this.plan = plan;
        LinkedHashMap<Integer, List<Edge>> adj = new LinkedHashMap<>();
        for (PlacedDoor d : plan.doors) if (d.kind.connected() && d.roomA != d.roomB) {
            adj.computeIfAbsent(d.roomA, k -> new ArrayList<>()).add(new Edge(d.roomB, d.index));
            adj.computeIfAbsent(d.roomB, k -> new ArrayList<>()).add(new Edge(d.roomA, d.index));
        }
        this.adjacency = adj;
        LinkedHashMap<Integer, Pt> c = new LinkedHashMap<>();
        for (PlacedRoom r : plan.rooms) c.put(r.index, computeCentre(r));
        this.centres = c;
    }

    /** The middle of a room. */
    public Pt centre(int room) {
        Pt p = centres.get(room);
        return p != null ? p : new Pt(DungeonFrame.tileCentre(3) + 0.5, DungeonFrame.tileCentre(3) + 0.5);
    }

    private Pt computeCentre(PlacedRoom r) {
        List<Pt> tc = new ArrayList<>();
        for (Tile it : r.tiles) tc.add(new Pt(DungeonFrame.tileCentre(it.x) + 0.5, DungeonFrame.tileCentre(it.z) + 0.5));
        if (tc.isEmpty()) return new Pt(0.0, 0.0);
        double sx = 0.0, sz = 0.0;
        for (Pt p : tc) sx += p.x;
        for (Pt p : tc) sz += p.z;
        Pt mean = new Pt(sx / tc.size(), sz / tc.size());
        Tile tile = new Tile(DungeonFrame.tileIndex(mean.x), DungeonFrame.tileIndex(mean.z));
        if (r.tiles.contains(tile)) return mean;
        Pt best = null;
        double bestD = 0.0;
        for (Pt p : tc) {
            double d = p.dist(mean);
            if (best == null || d < bestD) { best = p; bestD = d; }
        }
        return best;
    }

    /** The room whose tiles hold the point, or null. */
    public PlacedRoom roomAt(Pt p) {
        Tile tile = DungeonFrame.tileOf(p.x, p.z);
        if (tile == null) return null;
        for (PlacedRoom r : plan.rooms) if (r.tiles.contains(tile)) return r;
        return null;
    }

    /** The centre of a door's gap column. */
    public Pt doorPt(int door) {
        PlacedDoor d = plan.doors.get(door);
        Pair<Integer, Integer> s = d.spot();
        return new Pt(s.first + 0.5, s.second + 0.5);
    }

    /** The rooms from [a] to [b] along the door graph (inclusive), or just [a] when they are not connected. */
    public List<Integer> roomPath(int a, int b) {
        if (a == b) return List.of(a);
        HashMap<Integer, Integer> prev = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(a);
        prev.put(a, a);
        while (!queue.isEmpty()) {
            int cur = queue.removeFirst();
            if (cur == b) break;
            List<Edge> edges = adjacency.get(cur);
            if (edges != null) for (Edge e : edges) if (!prev.containsKey(e.to)) { prev.put(e.to, cur); queue.add(e.to); }
        }
        if (!prev.containsKey(b)) return List.of(a);
        ArrayList<Integer> out = new ArrayList<>();
        int c = b;
        while (c != a) { out.add(c); c = prev.get(c); }
        out.add(a);
        Collections.reverse(out);
        return out;
    }

    /** The door between two neighbouring rooms in the graph. */
    public Integer doorBetween(int a, int b) {
        List<Edge> edges = adjacency.get(a);
        if (edges == null) return null;
        for (Edge e : edges) if (e.to == b) return e.door;
        return null;
    }

    /** The route from a point in room [fromRoom] to a point in room [toRoom] (default: its middle). */
    public List<RoutePoint> route(Pt from, int fromRoom, int toRoom, Pt to) {
        List<Integer> rooms = roomPath(fromRoom, toRoom);
        ArrayList<RoutePoint> out = new ArrayList<>();
        out.add(new RoutePoint(from));
        for (int i = 1; i < rooms.size(); i++) {
            Integer door = doorBetween(rooms.get(i - 1), rooms.get(i));
            if (door != null) out.add(new RoutePoint(doorPt(door), door));
            if (i < rooms.size() - 1) out.add(new RoutePoint(centre(rooms.get(i))));
        }
        out.add(new RoutePoint(to != null ? to : centre(toRoom)));
        return out;
    }

    public List<RoutePoint> route(Pt from, int fromRoom, int toRoom) { return route(from, fromRoom, toRoom, null); }

    /** Length of a route, blocks. */
    public double length(List<RoutePoint> route) {
        double sum = 0.0;
        for (int i = 1; i < route.size(); i++) sum += route.get(i - 1).pt.dist(route.get(i).pt);
        return sum;
    }

    /** Doors on the way from room [a] to room [b]. */
    public List<Integer> doorsOnRoute(int a, int b) {
        List<Integer> path = roomPath(a, b);
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            Integer d = doorBetween(path.get(i), path.get(i + 1));
            if (d != null) out.add(d);
        }
        return out;
    }

    /** For every room, the run tick from which it can be walked into. Rooms not connected get Integer.MAX_VALUE. */
    public int[] reachTimes(int entrance, Function<Integer, Integer> doorPassableAt) {
        int maxIdx = -1;
        for (PlacedRoom r : plan.rooms) maxIdx = Math.max(maxIdx, r.index);
        int[] reach = new int[(plan.rooms.isEmpty() ? 0 : maxIdx) + 1];
        Arrays.fill(reach, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        reach[entrance] = 0;
        queue.add(entrance);
        while (!queue.isEmpty()) {
            int cur = queue.removeFirst();
            List<Edge> edges = adjacency.get(cur);
            if (edges == null) continue;
            for (Edge e : edges) {
                int t = Math.max(reach[cur], doorPassableAt.apply(e.door));
                if (reach[e.to] == Integer.MAX_VALUE || t < reach[e.to]) { reach[e.to] = t; queue.add(e.to); }
            }
        }
        return reach;
    }

    /** A spot [index] of [count] around [centre] (a small ring). */
    public Pt spread(Pt centre, int index, int count, double radius) {
        if (count <= 1) return centre;
        double a = 2.0 * Math.PI * index / count;
        return new Pt(centre.x + radius * Math.cos(a), centre.z + radius * Math.sin(a));
    }

    public Pt spread(Pt centre, int index, int count) { return spread(centre, index, count, 1.6); }

    /** Blocks per tick needed to cover [length] in [ticks]. */
    public static double speedFor(double length, int ticks) { return ticks <= 0 ? Double.POSITIVE_INFINITY : length / ticks; }

    /** Ticks to cover [length] at [bpt], at least 0. */
    public static int ticksFor(double length, double bpt) { return length <= 0.0 ? 0 : (int) Math.ceil(length / Math.min(bpt, 1e9)); }
}
