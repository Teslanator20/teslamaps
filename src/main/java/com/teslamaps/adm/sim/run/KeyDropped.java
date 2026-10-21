// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class KeyDropped implements RunEvent {
    public final int t;
    public final KeyKind kind;
    public final int room;
    public final double x;
    public final double y;
    public final double z;

    public KeyDropped(int t, KeyKind kind, int room, double x, double y, double z) {
        this.t = t;
        this.kind = kind;
        this.room = room;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof KeyDropped oo && Objects.equals(t, oo.t) && Objects.equals(kind, oo.kind) && Objects.equals(room, oo.room) && Objects.equals(x, oo.x) && Objects.equals(y, oo.y) && Objects.equals(z, oo.z);
    }
    @Override public int hashCode() { return Objects.hash(t, kind, room, x, y, z); }
    @Override public String toString() { return "KeyDropped(" + "t=" + t + ", " + "kind=" + kind + ", " + "room=" + room + ", " + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ")"; }
}
