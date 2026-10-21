// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.ArrayList;
import java.util.List;

public final class SkullFinale {
    private SkullFinale() {}

    public enum Step { PLING, HARP, REWARD }

    public static class Item {
        public final int at;
        public final Step step;
        public final int note;

        public Item(int at, Step step, int note) { this.at = at; this.step = step; this.note = note; }

        public Item(int at, Step step) { this(at, step, -1); }
    }

    public static final int REWARD_AT = 20;

    public static List<Item> schedule(boolean withPling) {
        List<Item> out = new ArrayList<>();
        if (withPling) out.add(new Item(1, Step.PLING));
        int[] at = {1, 5, 10, 15, 20};
        for (int i = 0; i < at.length; i++) out.add(new Item(at[i], Step.HARP, i));
        out.add(new Item(REWARD_AT, Step.REWARD));
        return out;
    }

    public static int orbPings(boolean isFinder) { return isFinder ? 2 : 1; }
}
