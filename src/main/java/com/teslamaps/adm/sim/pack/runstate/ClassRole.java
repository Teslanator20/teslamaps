// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

/** Per-class roles for the bots' ClearPlans. */
public class ClassRole {
    public final double roomsOwnedMean;
    public final double puzzlesOwnedMean;
    public final double doorOpenerFrac;
    public final Dist distanceBlocks;
    public final Double movingSpeedBpt;

    public ClassRole(double roomsOwnedMean, double puzzlesOwnedMean, double doorOpenerFrac, Dist distanceBlocks, Double movingSpeedBpt) {
        this.roomsOwnedMean = roomsOwnedMean;
        this.puzzlesOwnedMean = puzzlesOwnedMean;
        this.doorOpenerFrac = doorOpenerFrac;
        this.distanceBlocks = distanceBlocks;
        this.movingSpeedBpt = movingSpeedBpt;
    }
}
