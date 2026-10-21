// Ported from ADM's p3sim/pack/layouts/LayoutPack.kt
package com.teslamaps.adm.sim.pack.layouts;

/** The final door kind. {@code FAIRY} is the free wither door into the Fairy room (no key, no chat line). */
public enum DoorKind {
    NONE, NORMAL, WITHER, BLOOD, ENTRANCE, FAIRY, UNKNOWN;

    /** A doorway exists (kind is not {@code none} and not {@code unknown}). */
    public boolean connected() { return this != NONE && this != UNKNOWN; }

    public static DoorKind fromWord(String w) {
        for (DoorKind k : values()) if (k.name().equalsIgnoreCase(w)) return k;
        return UNKNOWN;
    }
}
