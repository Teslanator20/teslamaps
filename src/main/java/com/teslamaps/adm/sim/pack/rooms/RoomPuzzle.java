// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Pos;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** {@code kind} and the named anchor cells. */
public final class RoomPuzzle {
    public final String kind;
    public final Map<String, List<Pos>> anchors;

    public RoomPuzzle(String kind, Map<String, List<Pos>> anchors) { this.kind = kind; this.anchors = anchors; }

    @Override public boolean equals(Object o) { return o instanceof RoomPuzzle p && Objects.equals(kind, p.kind) && Objects.equals(anchors, p.anchors); }
    @Override public int hashCode() { return Objects.hash(kind, anchors); }
    @Override public String toString() { return "RoomPuzzle(kind=" + kind + ", anchors=" + anchors + ")"; }
}
