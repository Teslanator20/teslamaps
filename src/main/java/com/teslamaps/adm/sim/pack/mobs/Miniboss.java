// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import com.teslamaps.adm.sim.pack.Pos;

import java.util.List;
import java.util.Objects;

/** A room cleared by killing a miniboss, with its spawn cell and closed patrol loop (feet cells). */
public final class Miniboss {
    public final String room;
    public final int arch;
    public final Pos cell;
    public final String src;
    public final Double hpFactor;
    public final List<Pos> loop;

    public Miniboss(String room, int arch, Pos cell, String src, Double hpFactor, List<Pos> loop) {
        this.room = room; this.arch = arch; this.cell = cell; this.src = src; this.hpFactor = hpFactor; this.loop = loop;
    }

    @Override public boolean equals(Object o) {
        return o instanceof Miniboss m && Objects.equals(room, m.room) && arch == m.arch && Objects.equals(cell, m.cell) && Objects.equals(src, m.src)
                && Objects.equals(hpFactor, m.hpFactor) && Objects.equals(loop, m.loop);
    }
    @Override public int hashCode() { return Objects.hash(room, arch, cell, src, hpFactor, loop); }
    @Override public String toString() {
        return "Miniboss(room=" + room + ", arch=" + arch + ", cell=" + cell + ", src=" + src + ", hpFactor=" + hpFactor + ", loop=" + loop + ")";
    }
}
