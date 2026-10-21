// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

/** One lag preset tier. */
public class LagTier {
    public final double good;
    public final double typical;
    public final double bad;
    public final Double mean;
    public final Double slotOver200;
    public final Double slotMultiplier;

    public LagTier(double good, double typical, double bad, Double mean, Double slotOver200, Double slotMultiplier) {
        this.good = good;
        this.typical = typical;
        this.bad = bad;
        this.mean = mean;
        this.slotOver200 = slotOver200;
        this.slotMultiplier = slotMultiplier;
    }
}
