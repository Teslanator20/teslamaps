// Ported from ADM's p3sim/pack/rooms/Placement.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.Objects;

/** Where a canonical room-frame cell lands in the world. */
public final class WorldPos {
    public final int x;
    public final int y;
    public final int z;

    public WorldPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

    @Override public boolean equals(Object o) { return o instanceof WorldPos p && x == p.x && y == p.y && z == p.z; }
    @Override public int hashCode() { return Objects.hash(x, y, z); }
    @Override public String toString() { return "WorldPos(x=" + x + ", y=" + y + ", z=" + z + ")"; }
}
