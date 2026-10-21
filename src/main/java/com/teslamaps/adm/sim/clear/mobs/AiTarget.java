// Ported from ADM's p3sim/clear/mobs/MobAi.kt
package com.teslamaps.adm.sim.clear.mobs;

public class AiTarget {
    public final double x;
    public final double z;
    public final double dist;
    public final boolean seen;

    public AiTarget(double x, double z, double dist, boolean seen) { this.x = x; this.z = z; this.dist = dist; this.seen = seen; }

    public AiTarget(double x, double z, double dist) { this(x, z, dist, true); }
}
