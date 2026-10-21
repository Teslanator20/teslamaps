// Ported from ADM's p3sim/pack/PackTypes.kt
package com.teslamaps.adm.sim.pack;

import java.util.Objects;

public final class Tile {
    public final int x;
    public final int z;

    public Tile(int x, int z) { this.x = x; this.z = z; }

    @Override public boolean equals(Object o) { return o instanceof Tile t && x == t.x && z == t.z; }
    @Override public int hashCode() { return Objects.hash(x, z); }
    @Override public String toString() { return "Tile(x=" + x + ", z=" + z + ")"; }
}
