// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.Objects;

/** A skull texture. {@code role} is {@code essence redstoneKey decor unknown}. */
public final class SkullTexture {
    public final String id;
    public final String value;
    public final String profileId;
    public final String role;

    public SkullTexture(String id, String value, String profileId, String role) {
        this.id = id; this.value = value; this.profileId = profileId; this.role = role;
    }

    @Override public boolean equals(Object o) {
        return o instanceof SkullTexture s && Objects.equals(id, s.id) && Objects.equals(value, s.value) && Objects.equals(profileId, s.profileId)
                && Objects.equals(role, s.role);
    }
    @Override public int hashCode() { return Objects.hash(id, value, profileId, role); }
    @Override public String toString() { return "SkullTexture(id=" + id + ", value=" + value + ", profileId=" + profileId + ", role=" + role + ")"; }
}
