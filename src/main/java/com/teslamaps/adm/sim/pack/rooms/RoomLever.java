// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Pos;

import java.util.List;
import java.util.Objects;

/** A lever and the cells that vanish when it is flipped. */
public final class RoomLever {
    public final Pos pos;
    public final List<Pos> opens;

    public RoomLever(Pos pos, List<Pos> opens) { this.pos = pos; this.opens = opens; }

    @Override public boolean equals(Object o) { return o instanceof RoomLever l && Objects.equals(pos, l.pos) && Objects.equals(opens, l.opens); }
    @Override public int hashCode() { return Objects.hash(pos, opens); }
    @Override public String toString() { return "RoomLever(pos=" + pos + ", opens=" + opens + ")"; }
}
