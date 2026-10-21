package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/GhostRun.kt

public final class P {
    public final double x;
    public final double y;
    public final double z;

    public P(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public P plus(P o) { return new P(x + o.x, y + o.y, z + o.z); }

    public P minus(P o) { return new P(x - o.x, y - o.y, z - o.z); }

    public P times(double k) { return new P(x * k, y * k, z * k); }

    public double dist(P o) { return Math.sqrt((x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z)); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof P p)) return false;
        return Double.compare(x, p.x) == 0 && Double.compare(y, p.y) == 0 && Double.compare(z, p.z) == 0;
    }

    @Override public int hashCode() { return java.util.Objects.hash(x, y, z); }

    @Override public String toString() { return "P(x=" + x + ", y=" + y + ", z=" + z + ")"; }
}
