// Ported from ADM's p3sim/clear/bots/Traversals.kt
package com.teslamaps.adm.sim.clear.bots;

/** One recorded walk through a room design in the canonical room frame. */
public class Traversal {
    public final int ticks;
    public final int[] dt;
    public final double[] x;
    public final double[] z;

    public Traversal(int ticks, int[] dt, double[] x, double[] z) {
        if (!(dt.length == x.length && x.length == z.length && x.length > 0)) throw new IllegalArgumentException("a traversal needs at least one point");
        this.ticks = ticks;
        this.dt = dt;
        this.x = x;
        this.z = z;
    }
}
