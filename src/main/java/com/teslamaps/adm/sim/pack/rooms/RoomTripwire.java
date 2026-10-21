// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Pos;

import java.util.Objects;

/** A tripwire hook in the volume (derived by scanning), {@code facing} canonical. */
public final class RoomTripwire {
    public final Pos pos;
    public final String facing;

    public RoomTripwire(Pos pos, String facing) { this.pos = pos; this.facing = facing; }

    @Override public boolean equals(Object o) { return o instanceof RoomTripwire t && Objects.equals(pos, t.pos) && Objects.equals(facing, t.facing); }
    @Override public int hashCode() { return Objects.hash(pos, facing); }
    @Override public String toString() { return "RoomTripwire(pos=" + pos + ", facing=" + facing + ")"; }
}
