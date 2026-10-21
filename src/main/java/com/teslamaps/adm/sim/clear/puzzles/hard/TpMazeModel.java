package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/TpMazeModel.kt

import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Teleport Maze as a state machine: 30 end-portal-frame pads, 28 in seven groups of four plus two in the middle. Each pad sends you
 * to a landing on another pad's patch, looking at the group's way on; the last group's way on leads to pad 28. Pad 29 is the start.
 */
public class TpMazeModel {
    public final List<Pad> pads;
    public final int frameY;
    private final int[] dest;
    public final int[] correct;
    public final List<Integer> route;

    /** Whether somebody has reached pad 28. */
    public boolean solved = false;

    public TpMazeModel(List<Pad> pads, int frameY, int[] dest, int[] correct, List<Integer> route) {
        this.pads = pads;
        this.frameY = frameY;
        this.dest = dest;
        this.correct = correct;
        this.route = route;
    }

    /** The pad a frame cell belongs to, or null. */
    public Pad padAtFrame(int x, int z) {
        for (Pad p : pads) if (p.frameX == x && p.frameZ == z) return p;
        return null;
    }

    /** The pad a stepped-on pad sends you to, or null when this pad (28) sends you nowhere. */
    public Integer destinationOf(int pad) {
        int d = dest[pad];
        return d >= 0 ? d : null;
    }

    /** The way-on pad of the group of pad, or null for the middle pads. */
    public Integer wayOnFrom(int pad) {
        int g = pads.get(pad).group();
        return g >= 0 ? correct[g] : null;
    }

    /** The landing for stepping on pad, or null when the pad does nothing. Reaching pad 28 marks the maze solved. */
    public Landing step(int pad) {
        Integer to = destinationOf(pad);
        if (to == null) return null;
        if (to == EXIT) solved = true;
        return landing(to);
    }

    /** The landing on pad to's patch, looking at the way on of its group (towards the chest for pad 28). */
    public Landing landing(int to) {
        Pad p = pads.get(to);
        double x = p.landX + 0.5; double z = p.landZ + 0.5;
        Integer w = wayOnFrom(to);
        Pad aim = w != null ? pads.get(w) : null;
        float yaw = aim != null ? yawTo(aim.centreX() - x, aim.centreZ() - z) : yawTo(CHEST_X - x, CHEST_Z - z);
        return new Landing(to, x, frameY + 0.5, z, yaw, 0f);
    }

    /** The pads to step on, in order, from the start pad to the exit. */
    public List<Integer> solution() {
        List<Integer> out = new ArrayList<>();
        int p = START;
        out.add(p);
        int guard = 0;
        while (p != EXIT && guard++ < 64) {
            Integer land = destinationOf(p);
            if (land == null) break;
            if (land == EXIT) break;
            Integer next = wayOnFrom(land);
            if (next == null) break;
            p = next;
            out.add(p);
        }
        return out;
    }

    public static final int EXIT = 28;
    private static final double CHEST_X = 15.5;
    private static final double CHEST_Z = 20.5;
    public static final int START = 29;
    public static final int GROUPS = 7;

    /** The 30 frame cells in Odin's order (groups of four, then the two middle pads). */
    private static final int[][] FRAMES = {
            {4, 12}, {4, 6}, {10, 12}, {10, 6}, {4, 20}, {4, 14}, {10, 20}, {10, 14}, {4, 28}, {4, 22}, {10, 28}, {10, 22}, {12, 28}, {12, 22}, {18, 28}, {18, 22},
            {20, 28}, {20, 22}, {26, 28}, {26, 22}, {26, 20}, {26, 14}, {20, 20}, {20, 14}, {26, 12}, {26, 6}, {20, 12}, {20, 6}, {15, 14}, {15, 12},
    };

    /** The pads of the room: a landing is the far corner of the 2x2 slab patch a frame stands in. */
    public static List<Pad> standardPads() {
        List<Pad> out = new ArrayList<>();
        for (int i = 0; i < FRAMES.length; i++) {
            int x = FRAMES[i][0], z = FRAMES[i][1];
            int dx = i == EXIT ? 1 : (x % 8 == 4 ? 1 : -1);
            int dz = z % 8 == 6 ? 1 : -1;
            out.add(new Pad(i, x, z, x + dx, z + dz));
        }
        return out;
    }

    /** The frame height of a room volume (the first end-portal frame found), or 69. */
    public static int frameYOf(RoomVolume volume) {
        for (int[] f : FRAMES) for (int y = volume.y0; y < volume.y0 + volume.h; y++) {
            if (volume.stateAt(f[0], y, f[1]).startsWith("minecraft:end_portal_frame")) return y;
        }
        return 69;
    }

    /** MC yaw for a direction in x and z (looking along +z is yaw 0, along -x is yaw 90). */
    public static float yawTo(double dx, double dz) { return (float) Math.toDegrees(Math.atan2(-dx, dz)); }

    /** Draws a maze from seed; the route visits routeGroups groups (2 to 7) in a random order. */
    public static TpMazeModel generate(List<Pad> pads, int frameY, long seed, int routeGroups) {
        if (pads.size() != 30) throw new IllegalArgumentException("Failed requirement.");
        KRandom rnd = new KRandom(seed);
        int n = Math.clamp(routeGroups, 2, GROUPS);
        List<Integer> order = new ArrayList<>();
        for (int g = 0; g < GROUPS; g++) order.add(g);
        KRandom.shuffle(order, rnd);
        List<Integer> route = new ArrayList<>(order.subList(0, n));
        int[] correct = new int[GROUPS];
        for (int g = 0; g < GROUPS; g++) correct[g] = pickWayOn(pads, g, rnd);
        int[] dest = new int[30];
        Arrays.fill(dest, -1);
        List<Integer> notWayOn = new ArrayList<>();
        for (int i = 0; i < 28; i++) if (i != correct[pads.get(i).group()]) notWayOn.add(i);
        for (int g = 0; g < GROUPS; g++) {
            int idx = route.indexOf(g);
            Integer next;
            if (idx >= 0) next = idx + 1 < route.size() ? route.get(idx + 1) : null;
            else next = route.get(rnd.nextInt(route.size()));
            dest[correct[g]] = next == null ? EXIT : landIn(correct, next, rnd);
            for (int p = g * 4; p < g * 4 + 4; p++) if (p != correct[g]) dest[p] = notWayOn.get(rnd.nextInt(notWayOn.size()));
        }
        dest[START] = landIn(correct, route.get(0), rnd);
        dest[EXIT] = -1;
        return new TpMazeModel(pads, frameY, dest, correct, route);
    }

    public static TpMazeModel generate(List<Pad> pads, int frameY, long seed) { return generate(pads, frameY, seed, DEFAULT_ROUTE_GROUPS); }

    private static int landIn(int[] correct, int group, KRandom rnd) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < 4; i++) { int p = group * 4 + i; if (p != correct[group]) l.add(p); }
        return l.get(rnd.nextInt(l.size()));
    }

    /** Groups the route visits unless asked otherwise. Unmeasured. */
    public static final int DEFAULT_ROUTE_GROUPS = 7;

    /** The pad of group g to make the way on: one that keeps every landing's line of sight to it clear of the group's other pads. */
    private static int pickWayOn(List<Pad> pads, int g, KRandom rnd) {
        List<Integer> members = new ArrayList<>();
        for (int i = g * 4; i < g * 4 + 4; i++) members.add(i);
        List<Integer> order = new ArrayList<>(members);
        KRandom.shuffle(order, rnd);
        for (int c : order) {
            boolean all = true;
            for (int lander : members) {
                if (lander == c) continue;
                if (!clearLine(pads, lander, c, members)) { all = false; break; }
            }
            if (all) return c;
        }
        return order.get(0);
    }

    /** From lander's landing cell to target's centre, no other pad of members lies within 1.6 blocks of the line. */
    private static boolean clearLine(List<Pad> pads, int lander, int target, List<Integer> members) {
        double lx = pads.get(lander).landX + 0.5; double lz = pads.get(lander).landZ + 0.5;
        double tx = pads.get(target).centreX(); double tz = pads.get(target).centreZ();
        double len = Math.hypot(tx - lx, tz - lz);
        if (len < 1e-6) return false;
        for (int m : members) {
            if (m == lander || m == target) continue;
            double mx = pads.get(m).centreX() - lx; double mz = pads.get(m).centreZ() - lz;
            double along = (mx * (tx - lx) + mz * (tz - lz)) / len;
            if (along < 0) continue;
            double across = Math.abs(mx * (tz - lz) - mz * (tx - lx)) / len;
            if (across < 1.6) return false;
        }
        return true;
    }
}
