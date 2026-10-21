package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/Frames.kt

import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.util.Pair;

/** Positions between a room's canonical frame and the world, off the block grid. Pure. */
public final class Frames {
    private Frames() {}

    /** The world x, z of canonical position (cx, cz). */
    public static Pair<Double, Double> toWorld(Placement p, double cx, double cz) {
        int bx = (int) Math.floor(cx); int bz = (int) Math.floor(cz);
        WorldPos w = p.toWorld(bx, 0, bz);
        double ox = cx - bx - 0.5; double oz = cz - bz - 0.5;
        double rx, rz;
        switch (((p.quarterTurns() % 4) + 4) % 4) {
            case 1 -> { rx = -oz; rz = ox; }
            case 2 -> { rx = -ox; rz = -oz; }
            case 3 -> { rx = oz; rz = -ox; }
            default -> { rx = ox; rz = oz; }
        }
        return Pair.of(w.x + 0.5 + rx, w.z + 0.5 + rz);
    }

    /** The canonical x, z of world position (wx, wz), or null when outside the placed room. */
    public static Pair<Double, Double> toCanonical(Placement p, double wx, double wz) {
        int bx = (int) Math.floor(wx); int bz = (int) Math.floor(wz);
        Pair<Integer, Integer> c = p.toCanonical(bx, bz);
        if (c == null) return null;
        int cx = c.first, cz = c.second;
        double rx = wx - bx - 0.5; double rz = wz - bz - 0.5;
        double ox, oz;
        switch (((p.quarterTurns() % 4) + 4) % 4) {
            case 1 -> { ox = rz; oz = -rx; }
            case 2 -> { ox = -rx; oz = -rz; }
            case 3 -> { ox = -rz; oz = rx; }
            default -> { ox = rx; oz = rz; }
        }
        return Pair.of(cx + 0.5 + ox, cz + 0.5 + oz);
    }

    /** The world yaw of a canonical yaw. */
    public static float yawToWorld(Placement p, float yaw) { return yaw + 90f * (((p.quarterTurns() % 4) + 4) % 4); }
}
