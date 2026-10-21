// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

/** One straight piece of a seat's path: from [a] at [t0] to [b] at [t1]. [hop]: played as blink hops. */
public class Leg {
    public final int t0;
    public final int t1;
    public final Pt a;
    public final Pt b;
    public final boolean hop;

    public Leg(int t0, int t1, Pt a, Pt b, boolean hop) {
        if (t1 < t0) throw new IllegalArgumentException("a leg cannot end before it starts");
        this.t0 = t0;
        this.t1 = t1;
        this.a = a;
        this.b = b;
        this.hop = hop;
    }

    public Leg(int t0, int t1, Pt a, Pt b) { this(t0, t1, a, b, false); }

    /** Where the seat is at tick [t] (clamped to the leg). */
    public Pt at(int t) {
        if (t <= t0 || a.equals(b)) return t >= t1 ? b : a;
        if (t >= t1) return b;
        double f = (double) (t - t0) / (t1 - t0);
        if (!hop) return a.lerp(b, f);
        double len = a.dist(b);
        double done = Math.floor(len * f / BotTuning.HOP_BLOCKS) * BotTuning.HOP_BLOCKS;
        return a.lerp(b, Math.clamp(done / len, 0.0, 1.0));
    }
}
