// Ported from ADM's p3sim/clear/bots/Countdown.kt
package com.teslamaps.adm.sim.clear.bots;

/** A bot dropping onto the floor after it appears in the air: vanilla gravity. Pure. */
public class Fall {
    public double y;
    private double vy = 0.0;

    /** True once the bot has reached the floor. */
    public boolean landed = false;

    public Fall(double y) { this.y = y; }

    /** One tick of falling; the bot lands on [floor]. Returns the new height. */
    public double step(double floor) {
        if (landed) return y;
        vy = (vy - 0.08) * 0.98;
        y += vy;
        if (y <= floor) { y = floor; vy = 0.0; landed = true; }
        return y;
    }
}
