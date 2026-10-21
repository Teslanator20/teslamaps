// Ported from ADM's p3sim/run/DungeonPlan.kt
package com.teslamaps.adm.sim.run;

/** Where a practice run starts: the room's one doorway, on side (N E S W) of world tile (tx, tz). */
public class PracticeStart {
    public final int tx;
    public final int tz;
    public final char side;

    public PracticeStart(int tx, int tz, char side) {
        this.tx = tx;
        this.tz = tz;
        this.side = side;
    }
}
