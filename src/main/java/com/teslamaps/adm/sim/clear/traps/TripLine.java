package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TripLines.kt

import java.util.ArrayList;
import java.util.List;

/** A tripwire between two hooks: axis 'x' runs east-west (z = fixed), 'z' north-south (x = fixed); from/to are the hooks along it. */
public class TripLine {
    public final char axis;
    public final int y;
    public final int fixed;
    public final int from;
    public final int to;

    public TripLine(char axis, int y, int fixed, int from, int to) {
        this.axis = axis;
        this.y = y;
        this.fixed = fixed;
        this.from = from;
        this.to = to;
    }

    /** Whether a player standing at feet (px, py, pz) touches the string's flat box. */
    public boolean touches(double px, double py, double pz) {
        if (py + PLAYER_HEIGHT <= y || py >= y + STRING_HEIGHT) return false;
        double along = axis == 'x' ? px : pz;
        double across = axis == 'x' ? pz : px;
        return along + PLAYER_HALF > from + 1 && along - PLAYER_HALF < to && across + PLAYER_HALF > fixed && across - PLAYER_HALF < fixed + 1;
    }

    /** Whether the cell (x, y, z) is one of this line's two hooks. */
    public boolean hasHook(int x, int y, int z) {
        return y == this.y && (axis == 'x' ? z == fixed && (x == from || x == to) : x == fixed && (z == from || z == to));
    }

    public static final double STRING_HEIGHT = 0.15625;
    public static final double PLAYER_HEIGHT = 1.8;
    public static final double PLAYER_HALF = 0.3;

    /** Pairs hooks: one facing east or south joins the nearest hook ahead on the same line facing back. */
    public static List<TripLine> pair(List<Hook> hooks) {
        List<TripLine> out = new ArrayList<>();
        for (Hook h : hooks) {
            switch (h.facing) {
                case "east" -> {
                    Hook best = null;
                    for (Hook o : hooks) if (o.facing.equals("west") && o.y == h.y && o.z == h.z && o.x > h.x && (best == null || o.x < best.x)) best = o;
                    if (best != null) out.add(new TripLine('x', h.y, h.z, h.x, best.x));
                }
                case "south" -> {
                    Hook best = null;
                    for (Hook o : hooks) if (o.facing.equals("north") && o.y == h.y && o.x == h.x && o.z > h.z && (best == null || o.z < best.z)) best = o;
                    if (best != null) out.add(new TripLine('z', h.y, h.x, h.z, best.z));
                }
                default -> {}
            }
        }
        return out;
    }
}
