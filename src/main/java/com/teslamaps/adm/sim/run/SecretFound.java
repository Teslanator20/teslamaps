// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class SecretFound implements RunEvent {
    public final int t;
    public final int room;
    public final SecretKind kind;
    public final UUID player;
    public final int x;
    public final int y;
    public final int z;

    public SecretFound(int t, int room, SecretKind kind, UUID player, int x, int y, int z) {
        this.t = t;
        this.room = room;
        this.kind = kind;
        this.player = player;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof SecretFound oo && Objects.equals(t, oo.t) && Objects.equals(room, oo.room) && Objects.equals(kind, oo.kind) && Objects.equals(player, oo.player) && Objects.equals(x, oo.x) && Objects.equals(y, oo.y) && Objects.equals(z, oo.z);
    }
    @Override public int hashCode() { return Objects.hash(t, room, kind, player, x, y, z); }
    @Override public String toString() { return "SecretFound(" + "t=" + t + ", " + "room=" + room + ", " + "kind=" + kind + ", " + "player=" + player + ", " + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ")"; }
}
