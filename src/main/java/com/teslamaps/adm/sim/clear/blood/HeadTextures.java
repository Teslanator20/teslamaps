// Ported from ADM's p3sim/clear/blood/HeadTextures.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Turns a camp's head key (texture id or pack role) into a skull texture (pure). */
public final class HeadTextures {
    private HeadTextures() {}

    /** The key that removes a head from its slot. */
    public static final String NONE = "none";

    /** The key for a plain head with no texture. */
    public static final String PLAIN = "plain";

    /** A skull profile: the `textures` property value and a profile id. */
    public static final class Resolved {
        public final String value;
        public final UUID profileId;

        public Resolved(String value, UUID profileId) {
            this.value = value;
            this.profileId = profileId;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Resolved r && Objects.equals(value, r.value) && Objects.equals(profileId, r.profileId);
        }
        @Override public int hashCode() { return Objects.hash(value, profileId); }
        @Override public String toString() { return "Resolved(value=" + value + ", profileId=" + profileId + ")"; }
    }

    private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{16,64}$");

    /** The base64 `textures` value for a texture id. */
    public static String valueFor(String textureId) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + textureId + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** The pack's own entry (by id, then by role), else one built from the id; null for NONE, PLAIN or an unknown key. */
    public static Resolved resolve(String key, List<SkullTexture> table) {
        if (key.equals(NONE) || key.equals(PLAIN) || key.isBlank()) return null;
        SkullTexture entry = null;
        for (SkullTexture t : table) if (t.id != null && t.id.equalsIgnoreCase(key)) { entry = t; break; }
        if (entry == null) for (SkullTexture t : table) if (key.equals(t.role)) { entry = t; break; }
        String value = entry != null ? entry.value : null;
        if (value == null) {
            if (HEX.matcher(key).matches()) value = valueFor(key.toLowerCase(Locale.ROOT));
            else return null;
        }
        UUID id = null;
        if (entry != null && entry.profileId != null) {
            try { id = UUID.fromString(entry.profileId); } catch (Throwable ignored) {}
        }
        if (id == null) id = UUID.nameUUIDFromBytes(("ads-head:" + key).getBytes(StandardCharsets.UTF_8));
        return new Resolved(value, id);
    }
}
