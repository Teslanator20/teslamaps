package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/DoorGeometry.kt

import java.util.Objects;

public final class Slot {
    public final char axis;
    public final int tx;
    public final int tz;

    public Slot(char axis, int tx, int tz) {
        this.axis = axis;
        this.tx = tx;
        this.tz = tz;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Slot s && axis == s.axis && tx == s.tx && tz == s.tz;
    }

    @Override
    public int hashCode() { return Objects.hash(axis, tx, tz); }

    @Override
    public String toString() { return "Slot(axis=" + axis + ", tx=" + tx + ", tz=" + tz + ")"; }
}
