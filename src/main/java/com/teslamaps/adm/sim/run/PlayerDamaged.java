// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class PlayerDamaged implements RunEvent {
    public final int t;
    public final UUID player;
    public final double percent;
    public final double hpAfter;
    public final DamageSource source;
    public final UUID attacker;
    public final boolean trueDamage;

    public PlayerDamaged(int t, UUID player, double percent, double hpAfter, DamageSource source, UUID attacker, boolean trueDamage) {
        this.t = t;
        this.player = player;
        this.percent = percent;
        this.hpAfter = hpAfter;
        this.source = source;
        this.attacker = attacker;
        this.trueDamage = trueDamage;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PlayerDamaged x && Objects.equals(t, x.t) && Objects.equals(player, x.player) && Objects.equals(percent, x.percent) && Objects.equals(hpAfter, x.hpAfter) && Objects.equals(source, x.source) && Objects.equals(attacker, x.attacker) && Objects.equals(trueDamage, x.trueDamage);
    }
    @Override public int hashCode() { return Objects.hash(t, player, percent, hpAfter, source, attacker, trueDamage); }
    @Override public String toString() { return "PlayerDamaged(" + "t=" + t + ", " + "player=" + player + ", " + "percent=" + percent + ", " + "hpAfter=" + hpAfter + ", " + "source=" + source + ", " + "attacker=" + attacker + ", " + "trueDamage=" + trueDamage + ")"; }
}
