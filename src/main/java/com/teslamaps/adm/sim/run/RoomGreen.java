// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class RoomGreen implements RunEvent {
    public final int t;
    public final int room;

    public RoomGreen(int t, int room) {
        this.t = t;
        this.room = room;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof RoomGreen x && Objects.equals(t, x.t) && Objects.equals(room, x.room);
    }
    @Override public int hashCode() { return Objects.hash(t, room); }
    @Override public String toString() { return "RoomGreen(" + "t=" + t + ", " + "room=" + room + ")"; }
}
