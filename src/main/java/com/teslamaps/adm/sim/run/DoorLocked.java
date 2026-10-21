// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class DoorLocked implements RunEvent {
    public final int t;
    public final int door;
    public final UUID player;

    public DoorLocked(int t, int door, UUID player) {
        this.t = t;
        this.door = door;
        this.player = player;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DoorLocked x && Objects.equals(t, x.t) && Objects.equals(door, x.door) && Objects.equals(player, x.player);
    }
    @Override public int hashCode() { return Objects.hash(t, door, player); }
    @Override public String toString() { return "DoorLocked(" + "t=" + t + ", " + "door=" + door + ", " + "player=" + player + ")"; }
}
