// Ported from ADM's p3sim/run/plugin/BloodCamp.kt
package com.teslamaps.adm.sim.run.plugin;

import java.util.Objects;

public final class HeadSlot {
    public final int index;
    public final int x;
    public final int y;
    public final int z;
    public final String facing;

    public HeadSlot(int index, int x, int y, int z, String facing) {
        this.index = index;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof HeadSlot h && Objects.equals(index, h.index) && Objects.equals(x, h.x) && Objects.equals(y, h.y) && Objects.equals(z, h.z) && Objects.equals(facing, h.facing);
    }
    @Override public int hashCode() { return Objects.hash(index, x, y, z, facing); }
    @Override public String toString() { return "HeadSlot(" + "index=" + index + ", " + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ", " + "facing=" + facing + ")"; }
}
