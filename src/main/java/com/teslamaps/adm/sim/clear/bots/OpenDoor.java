// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.Objects;

/** Opens paid door [door] with the party's key; retried until it opens. [spot]: the door's gap, the seat must be near it. */
public final class OpenDoor implements Act {
    private final int seat;
    private final int t;
    public final int door;
    public final Pt spot;

    public OpenDoor(int seat, int t, int door, Pt spot) {
        this.seat = seat;
        this.t = t;
        this.door = door;
        this.spot = spot;
    }

    public OpenDoor(int seat, int t, int door) { this(seat, t, door, null); }

    @Override public int seat() { return seat; }

    @Override public int t() { return t; }

    @Override public int until() { return t; }

    @Override public boolean equals(Object o) {
        return o instanceof OpenDoor x && seat == x.seat && t == x.t && door == x.door && Objects.equals(spot, x.spot);
    }

    @Override public int hashCode() { return Objects.hash(seat, t, door, spot); }

    @Override public String toString() { return "OpenDoor(seat=" + seat + ", t=" + t + ", door=" + door + ", spot=" + spot + ")"; }
}
