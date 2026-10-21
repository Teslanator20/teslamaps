package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapSites.kt

/** How a dispenser arrow flies; a bank fires only while a player is within range; an arrow goes after maxTicks. */
public class ArrowFlight {
    public final float speed;
    public final float inaccuracy;
    public final double range;
    public final int maxTicks;

    public ArrowFlight(float speed, float inaccuracy, double range, int maxTicks) {
        this.speed = speed;
        this.inaccuracy = inaccuracy;
        this.range = range;
        this.maxTicks = maxTicks;
    }

    public ArrowFlight() { this(1.5f, 6.0f, 48.0, 60); }
}
