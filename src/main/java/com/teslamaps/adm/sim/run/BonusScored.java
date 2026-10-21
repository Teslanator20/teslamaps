// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class BonusScored implements RunEvent {
    public final int t;
    public final BonusKind kind;
    public final UUID player;

    public BonusScored(int t, BonusKind kind, UUID player) {
        this.t = t;
        this.kind = kind;
        this.player = player;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BonusScored x && Objects.equals(t, x.t) && Objects.equals(kind, x.kind) && Objects.equals(player, x.player);
    }
    @Override public int hashCode() { return Objects.hash(t, kind, player); }
    @Override public String toString() { return "BonusScored(" + "t=" + t + ", " + "kind=" + kind + ", " + "player=" + player + ")"; }
}
