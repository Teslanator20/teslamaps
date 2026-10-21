// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Pos;

import java.util.Objects;

public final class SealCell {
    public final Pos pos;
    public final String state;

    public SealCell(Pos pos, String state) { this.pos = pos; this.state = state; }

    @Override public boolean equals(Object o) { return o instanceof SealCell s && Objects.equals(pos, s.pos) && Objects.equals(state, s.state); }
    @Override public int hashCode() { return Objects.hash(pos, state); }
    @Override public String toString() { return "SealCell(pos=" + pos + ", state=" + state + ")"; }
}
