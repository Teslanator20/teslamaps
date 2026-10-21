// Ported from ADM's p3sim/clear/bots/PlanGeometry.kt
package com.teslamaps.adm.sim.clear.bots;

/** A point on the dungeon floor, world x and z. */
public final class Pt {
    public final double x;
    public final double z;

    public Pt(double x, double z) { this.x = x; this.z = z; }

    public double dist(Pt o) { return Math.hypot(x - o.x, z - o.z); }

    public Pt lerp(Pt o, double f) { return new Pt(x + (o.x - x) * f, z + (o.z - z) * f); }

    @Override public boolean equals(Object o) {
        return o instanceof Pt p && Double.compare(x, p.x) == 0 && Double.compare(z, p.z) == 0;
    }

    @Override public int hashCode() { return 31 * Double.hashCode(x) + Double.hashCode(z); }

    @Override public String toString() { return "Pt(x=" + x + ", z=" + z + ")"; }
}
