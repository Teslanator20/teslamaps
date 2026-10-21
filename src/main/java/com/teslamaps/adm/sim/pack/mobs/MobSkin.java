// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A skin to give a disguised body. {@code value} is the base64 textures property, null when the pack lacks it. */
public final class MobSkin {
    public final String id;
    public final String value;
    public final String signature;
    public final boolean slim;
    public final List<MobSkin> variants;

    public MobSkin(String id, String value, String signature, boolean slim, List<MobSkin> variants) {
        this.id = id; this.value = value; this.signature = signature; this.slim = slim; this.variants = variants;
    }

    public MobSkin(String id, String value, String signature, boolean slim) { this(id, value, signature, slim, new ArrayList<>()); }

    public MobSkin(String id, String value, String signature) { this(id, value, signature, false); }

    @Override public boolean equals(Object o) {
        return o instanceof MobSkin s && Objects.equals(id, s.id) && Objects.equals(value, s.value) && Objects.equals(signature, s.signature)
                && slim == s.slim && Objects.equals(variants, s.variants);
    }
    @Override public int hashCode() { return Objects.hash(id, value, signature, slim, variants); }
    @Override public String toString() {
        return "MobSkin(id=" + id + ", value=" + value + ", signature=" + signature + ", slim=" + slim + ", variants=" + variants + ")";
    }
}
