// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class PlayerRevived implements RunEvent {
    public final int t;
    public final UUID player;
    public final UUID by;

    public PlayerRevived(int t, UUID player, UUID by) {
        this.t = t;
        this.player = player;
        this.by = by;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PlayerRevived x && Objects.equals(t, x.t) && Objects.equals(player, x.player) && Objects.equals(by, x.by);
    }
    @Override public int hashCode() { return Objects.hash(t, player, by); }
    @Override public String toString() { return "PlayerRevived(" + "t=" + t + ", " + "player=" + player + ", " + "by=" + by + ")"; }
}
