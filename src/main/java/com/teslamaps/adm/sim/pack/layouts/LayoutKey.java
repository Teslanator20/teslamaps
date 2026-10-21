// Ported from ADM's p3sim/pack/layouts/LayoutPack.kt
package com.teslamaps.adm.sim.pack.layouts;

import java.util.Objects;

/** A Wither or Blood Key stand seen in the recorded run. {@code room} is the room it came from. */
public final class LayoutKey {
    public final String kind;
    public final Integer tSpawn;
    public final double x;
    public final double z;
    public final Integer room;
    public final Integer tPickup;
    public final Integer tGone;

    public LayoutKey(String kind, Integer tSpawn, double x, double z, Integer room, Integer tPickup, Integer tGone) {
        this.kind = kind;
        this.tSpawn = tSpawn;
        this.x = x;
        this.z = z;
        this.room = room;
        this.tPickup = tPickup;
        this.tGone = tGone;
    }

    @Override public boolean equals(Object o) {
        return o instanceof LayoutKey k && Objects.equals(kind, k.kind) && Objects.equals(tSpawn, k.tSpawn) && Double.compare(x, k.x) == 0
                && Double.compare(z, k.z) == 0 && Objects.equals(room, k.room) && Objects.equals(tPickup, k.tPickup) && Objects.equals(tGone, k.tGone);
    }

    @Override public int hashCode() { return Objects.hash(kind, tSpawn, x, z, room, tPickup, tGone); }

    @Override public String toString() {
        return "LayoutKey(kind=" + kind + ", tSpawn=" + tSpawn + ", x=" + x + ", z=" + z + ", room=" + room + ", tPickup=" + tPickup + ", tGone=" + tGone + ")";
    }
}
