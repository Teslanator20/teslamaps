// Ported from ADM's p3sim/clear/blood/camp/WallPlan.kt
package com.teslamaps.adm.sim.clear.blood.camp;

/** One mob the Watcher is about to put into the room. */
public class Landing {
    public final WallStation station;
    public final boolean initial;
    public final int throwTick;
    public final int spawnTick;
    public final int landTick;

    public Landing(WallStation station, boolean initial, int throwTick, int spawnTick, int landTick) {
        this.station = station;
        this.initial = initial;
        this.throwTick = throwTick;
        this.spawnTick = spawnTick;
        this.landTick = landTick;
    }
}
