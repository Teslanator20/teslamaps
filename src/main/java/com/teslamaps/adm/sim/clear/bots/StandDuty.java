// Ported from ADM's p3sim/clear/bots/BotExcursion.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.List;
import java.util.Map;

/** Who goes to a key stand. Pure. */
public final class StandDuty {
    private StandDuty() {}

    /** The seat in [positions] nearest to [at] within [within] blocks, or null. */
    public static Integer nearest(Map<Integer, Pt> positions, Pt at, double within) {
        Integer best = null;
        double bestD = 0.0;
        for (Map.Entry<Integer, Pt> e : positions.entrySet()) {
            double d = e.getValue().dist(at);
            if (d > within) continue;
            if (best == null || d < bestD) { best = e.getKey(); bestD = d; }
        }
        return best;
    }

    /** Whether somebody (a human) already stands at the stand. */
    public static boolean someoneThere(List<Pt> humans, Pt at, double radius) {
        for (Pt h : humans) if (h.dist(at) <= radius) return true;
        return false;
    }

    public static boolean someoneThere(List<Pt> humans, Pt at) { return someoneThere(humans, at, 2.5); }
}
