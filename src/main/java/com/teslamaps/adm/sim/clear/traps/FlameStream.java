package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapSites.kt

/** How a flame behaves everywhere: beat, stream size, live ticks, wake range. */
public class FlameStream {
    public final int period;
    public final double reach;
    public final double halfWidth;
    public final double height;
    public final int liveTicks;
    public final double range;

    public FlameStream(int period, double reach, double halfWidth, double height, int liveTicks, double range) {
        this.period = period;
        this.reach = reach;
        this.halfWidth = halfWidth;
        this.height = height;
        this.liveTicks = liveTicks;
        this.range = range;
    }

    public FlameStream() { this(8, 6.0, 1.0, 1.4, 5, 16.0); }
}
