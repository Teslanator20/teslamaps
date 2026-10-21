// Ported from ADM's p3sim/clear/blood/camp/CampMech.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import java.util.Objects;

/** A position in world coordinates. */
public final class Vec3 {
    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 plus(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }

    public Vec3 minus(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }

    public Vec3 times(double s) { return new Vec3(x * s, y * s, z * s); }

    public Vec3 div(double s) { return new Vec3(x / s, y / s, z / s); }

    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    public double length() { return Math.sqrt(dot(this)); }

    public double dist(Vec3 o) { return minus(o).length(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Vec3 v && Double.compare(x, v.x) == 0 && Double.compare(y, v.y) == 0 && Double.compare(z, v.z) == 0;
    }
    @Override public int hashCode() { return Objects.hash(x, y, z); }
    @Override public String toString() { return "Vec3(x=" + x + ", y=" + y + ", z=" + z + ")"; }
}
