// Ported from ADM's p3sim/clear/bots/CampKills.kt
package com.teslamaps.adm.sim.clear.bots;

/** How fast a teammate camping blood kills the Watcher's mobs (measured spread). */
public final class CampKills {
    private CampKills() {}

    /** The Watcher's own mob kind, never killed by a camper. */
    public static final String WATCHER = "watcher";

    /** Ticks from a camp mob first standing in the room to its death, the measured spread drawn piecewise. */
    public static int life(double u) {
        return (int) Math.round(PlanMath.curve(PlanMath.pts(0.0, 16.0, 0.1, 21.0, 0.25, 22.0, 0.5, 24.0, 0.75, 35.0, 0.9, 118.0, 1.0, 260.0), u));
    }
}
