// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class BlessingGranted implements RunEvent {
    public final int t;
    public final String blessing;
    public final int level;
    public final UUID player;

    public BlessingGranted(int t, String blessing, int level, UUID player) {
        this.t = t;
        this.blessing = blessing;
        this.level = level;
        this.player = player;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BlessingGranted x && Objects.equals(t, x.t) && Objects.equals(blessing, x.blessing) && Objects.equals(level, x.level) && Objects.equals(player, x.player);
    }
    @Override public int hashCode() { return Objects.hash(t, blessing, level, player); }
    @Override public String toString() { return "BlessingGranted(" + "t=" + t + ", " + "blessing=" + blessing + ", " + "level=" + level + ", " + "player=" + player + ")"; }
}
