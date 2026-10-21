// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Objects;

/** One mob of a recorded visit. */
public final class MobSpawn {
    public final int x;
    public final int y;
    public final int z;
    public final int arch;
    public final boolean star;

    public MobSpawn(int x, int y, int z, int arch, boolean star) { this.x = x; this.y = y; this.z = z; this.arch = arch; this.star = star; }

    @Override public boolean equals(Object o) {
        return o instanceof MobSpawn s && x == s.x && y == s.y && z == s.z && arch == s.arch && star == s.star;
    }
    @Override public int hashCode() { return Objects.hash(x, y, z, arch, star); }
    @Override public String toString() { return "MobSpawn(x=" + x + ", y=" + y + ", z=" + z + ", arch=" + arch + ", star=" + star + ")"; }
}
