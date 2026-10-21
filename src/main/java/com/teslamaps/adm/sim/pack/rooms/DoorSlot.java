// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Tile;

import java.util.Objects;

/** One tile side that borders outside the room. {@code open}: all 12 opening cells are air in the base volume. */
public final class DoorSlot {
    public final Tile tile;
    public final char side;
    public final boolean open;
    public final SlotSeal seal;

    public DoorSlot(Tile tile, char side, boolean open, SlotSeal seal) {
        this.tile = tile; this.side = side; this.open = open; this.seal = seal;
    }

    @Override public boolean equals(Object o) {
        return o instanceof DoorSlot s && Objects.equals(tile, s.tile) && side == s.side && open == s.open && Objects.equals(seal, s.seal);
    }
    @Override public int hashCode() { return Objects.hash(tile, side, open, seal); }
    @Override public String toString() { return "DoorSlot(tile=" + tile + ", side=" + side + ", open=" + open + ", seal=" + seal + ")"; }
}
