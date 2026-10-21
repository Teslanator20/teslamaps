// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.List;
import java.util.Objects;

/** The blocks that fill a slot when it is a wall. */
public final class SlotSeal {
    public final String src;
    public final boolean incomplete;
    public final List<SealCell> cells;

    public SlotSeal(String src, boolean incomplete, List<SealCell> cells) {
        this.src = src; this.incomplete = incomplete; this.cells = cells;
    }

    @Override public boolean equals(Object o) {
        return o instanceof SlotSeal s && Objects.equals(src, s.src) && incomplete == s.incomplete && Objects.equals(cells, s.cells);
    }
    @Override public int hashCode() { return Objects.hash(src, incomplete, cells); }
    @Override public String toString() { return "SlotSeal(src=" + src + ", incomplete=" + incomplete + ", cells=" + cells + ")"; }
}
