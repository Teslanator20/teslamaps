// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import java.util.Objects;
import java.util.UUID;

public final class DoorOpened implements RunEvent {
    public final int t;
    public final int door;
    public final DoorKind kind;
    public final UUID by;

    public DoorOpened(int t, int door, DoorKind kind, UUID by) {
        this.t = t;
        this.door = door;
        this.kind = kind;
        this.by = by;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DoorOpened x && Objects.equals(t, x.t) && Objects.equals(door, x.door) && Objects.equals(kind, x.kind) && Objects.equals(by, x.by);
    }
    @Override public int hashCode() { return Objects.hash(t, door, kind, by); }
    @Override public String toString() { return "DoorOpened(" + "t=" + t + ", " + "door=" + door + ", " + "kind=" + kind + ", " + "by=" + by + ")"; }
}
