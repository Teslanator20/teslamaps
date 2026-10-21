// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class MobRef {
    public final UUID id;
    public final String kind;
    public final Integer room;
    public final boolean starred;
    public final boolean required;

    public MobRef(UUID id, String kind, Integer room, boolean starred, boolean required) {
        this.id = id;
        this.kind = kind;
        this.room = room;
        this.starred = starred;
        this.required = required;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MobRef x && Objects.equals(id, x.id) && Objects.equals(kind, x.kind) && Objects.equals(room, x.room) && Objects.equals(starred, x.starred) && Objects.equals(required, x.required);
    }
    @Override public int hashCode() { return Objects.hash(id, kind, room, starred, required); }
    @Override public String toString() { return "MobRef(" + "id=" + id + ", " + "kind=" + kind + ", " + "room=" + room + ", " + "starred=" + starred + ", " + "required=" + required + ")"; }
}
