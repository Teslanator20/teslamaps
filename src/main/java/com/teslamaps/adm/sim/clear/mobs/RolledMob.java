// Ported from ADM's p3sim/clear/mobs/MobRolls.kt
package com.teslamaps.adm.sim.clear.mobs;

import java.util.Objects;

public final class RolledMob {
    public final int arch;
    public final boolean star;
    public final int x;
    public final int y;
    public final int z;

    public RolledMob(int arch, boolean star, int x, int y, int z) { this.arch = arch; this.star = star; this.x = x; this.y = y; this.z = z; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolledMob r)) return false;
        return arch == r.arch && star == r.star && x == r.x && y == r.y && z == r.z;
    }

    @Override public int hashCode() { return Objects.hash(arch, star, x, y, z); }

    @Override public String toString() { return "RolledMob(arch=" + arch + ", star=" + star + ", x=" + x + ", y=" + y + ", z=" + z + ")"; }
}
