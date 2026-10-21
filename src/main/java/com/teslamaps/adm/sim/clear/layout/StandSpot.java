package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/SpawnFinder.kt

import java.util.Objects;

public final class StandSpot {
    public final double x;
    public final double y;
    public final double z;

    public StandSpot(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StandSpot s && Double.compare(x, s.x) == 0 && Double.compare(y, s.y) == 0 && Double.compare(z, s.z) == 0;
    }

    @Override
    public int hashCode() { return Objects.hash(x, y, z); }

    @Override
    public String toString() { return "StandSpot(x=" + x + ", y=" + y + ", z=" + z + ")"; }
}
