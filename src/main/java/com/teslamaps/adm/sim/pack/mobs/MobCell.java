// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Objects;

/** One independent-roll spawn cell: "in {@code occ} of the visits, a mob of [arch] stood at (x, y, z)". */
public final class MobCell {
    public final int x;
    public final int y;
    public final int z;
    public final int arch;
    public final boolean star;
    public final int occ;

    public MobCell(int x, int y, int z, int arch, boolean star, int occ) {
        this.x = x; this.y = y; this.z = z; this.arch = arch; this.star = star; this.occ = occ;
    }

    @Override public boolean equals(Object o) {
        return o instanceof MobCell c && x == c.x && y == c.y && z == c.z && arch == c.arch && star == c.star && occ == c.occ;
    }
    @Override public int hashCode() { return Objects.hash(x, y, z, arch, star, occ); }
    @Override public String toString() { return "MobCell(x=" + x + ", y=" + y + ", z=" + z + ", arch=" + arch + ", star=" + star + ", occ=" + occ + ")"; }
}
