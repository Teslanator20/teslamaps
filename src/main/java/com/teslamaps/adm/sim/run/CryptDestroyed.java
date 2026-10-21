// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class CryptDestroyed implements RunEvent {
    public final int t;
    public final int room;
    public final UUID player;

    public CryptDestroyed(int t, int room, UUID player) {
        this.t = t;
        this.room = room;
        this.player = player;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof CryptDestroyed x && Objects.equals(t, x.t) && Objects.equals(room, x.room) && Objects.equals(player, x.player);
    }
    @Override public int hashCode() { return Objects.hash(t, room, player); }
    @Override public String toString() { return "CryptDestroyed(" + "t=" + t + ", " + "room=" + room + ", " + "player=" + player + ")"; }
}
