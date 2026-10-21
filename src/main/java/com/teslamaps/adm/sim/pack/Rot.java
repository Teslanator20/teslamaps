// Ported from ADM's p3sim/pack/PackTypes.kt
package com.teslamaps.adm.sim.pack;

public enum Rot {
    SOUTH(0), WEST(1), NORTH(2), EAST(3);

    public final int q;

    Rot(int q) { this.q = q; }

    public static Rot fromWord(String word) {
        if (word == null) return null;
        for (Rot r : values()) if (r.name().equalsIgnoreCase(word)) return r;
        return null;
    }
}
