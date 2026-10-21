// Ported from ADM's p3sim/pack/PackTypes.kt
package com.teslamaps.adm.sim.pack;

import java.util.Objects;

public final class Pos {
    public final int x;
    public final int y;
    public final int z;

    public Pos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

    @Override public boolean equals(Object o) { return o instanceof Pos p && x == p.x && y == p.y && z == p.z; }
    @Override public int hashCode() { return Objects.hash(x, y, z); }
    @Override public String toString() { return "Pos(x=" + x + ", y=" + y + ", z=" + z + ")"; }
}
