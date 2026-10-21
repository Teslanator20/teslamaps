// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class PlayerJoined implements RunEvent {
    public final int t;
    public final UUID player;

    public PlayerJoined(int t, UUID player) {
        this.t = t;
        this.player = player;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PlayerJoined x && Objects.equals(t, x.t) && Objects.equals(player, x.player);
    }
    @Override public int hashCode() { return Objects.hash(t, player); }
    @Override public String toString() { return "PlayerJoined(" + "t=" + t + ", " + "player=" + player + ")"; }
}
