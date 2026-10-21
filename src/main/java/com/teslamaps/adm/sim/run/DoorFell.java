// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class DoorFell implements RunEvent {
    public final int t;
    public final int door;

    public DoorFell(int t, int door) {
        this.t = t;
        this.door = door;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DoorFell x && Objects.equals(t, x.t) && Objects.equals(door, x.door);
    }
    @Override public int hashCode() { return Objects.hash(t, door); }
    @Override public String toString() { return "DoorFell(" + "t=" + t + ", " + "door=" + door + ")"; }
}
