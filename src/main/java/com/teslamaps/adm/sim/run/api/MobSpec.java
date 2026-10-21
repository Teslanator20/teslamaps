// Ported from ADM's p3sim/run/api/MobApi.kt
package com.teslamaps.adm.sim.run.api;

import java.util.Map;
import java.util.Objects;

public final class MobSpec {
    public final String kind;
    public final double x;
    public final double y;
    public final double z;
    public final Integer roomIndex;
    public final boolean starred;
    public final boolean required;
    public final Double hp;
    public final Map<String, String> extras;

    public MobSpec(String kind, double x, double y, double z, Integer roomIndex, boolean starred, boolean required, Double hp, Map<String, String> extras) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.roomIndex = roomIndex;
        this.starred = starred;
        this.required = required;
        this.hp = hp;
        this.extras = extras;
    }

    public MobSpec(String kind, double x, double y, double z, Integer roomIndex, boolean starred, boolean required, Double hp) { this(kind, x, y, z, roomIndex, starred, required, hp, java.util.Collections.emptyMap()); }

    public MobSpec(String kind, double x, double y, double z, Integer roomIndex, boolean starred, boolean required) { this(kind, x, y, z, roomIndex, starred, required, null, java.util.Collections.emptyMap()); }

    public MobSpec(String kind, double x, double y, double z, Integer roomIndex, boolean starred) { this(kind, x, y, z, roomIndex, starred, false, null, java.util.Collections.emptyMap()); }

    public MobSpec(String kind, double x, double y, double z, Integer roomIndex) { this(kind, x, y, z, roomIndex, false, false, null, java.util.Collections.emptyMap()); }

    public MobSpec(String kind, double x, double y, double z) { this(kind, x, y, z, null, false, false, null, java.util.Collections.emptyMap()); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MobSpec m && Objects.equals(kind, m.kind) && Objects.equals(x, m.x) && Objects.equals(y, m.y) && Objects.equals(z, m.z) && Objects.equals(roomIndex, m.roomIndex) && Objects.equals(starred, m.starred) && Objects.equals(required, m.required) && Objects.equals(hp, m.hp) && Objects.equals(extras, m.extras);
    }
    @Override public int hashCode() { return Objects.hash(kind, x, y, z, roomIndex, starred, required, hp, extras); }
    @Override public String toString() { return "MobSpec(" + "kind=" + kind + ", " + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ", " + "roomIndex=" + roomIndex + ", " + "starred=" + starred + ", " + "required=" + required + ", " + "hp=" + hp + ", " + "extras=" + extras + ")"; }
}
