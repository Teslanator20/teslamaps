// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class PlayerDied implements RunEvent {
    public final int t;
    public final UUID player;
    public final DamageSource source;
    public final UUID attacker;

    public PlayerDied(int t, UUID player, DamageSource source, UUID attacker) {
        this.t = t;
        this.player = player;
        this.source = source;
        this.attacker = attacker;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PlayerDied x && Objects.equals(t, x.t) && Objects.equals(player, x.player) && Objects.equals(source, x.source) && Objects.equals(attacker, x.attacker);
    }
    @Override public int hashCode() { return Objects.hash(t, player, source, attacker); }
    @Override public String toString() { return "PlayerDied(" + "t=" + t + ", " + "player=" + player + ", " + "source=" + source + ", " + "attacker=" + attacker + ")"; }
}
