// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.ArrayList;
import java.util.List;

public final class SecretChestFinale {
    private SecretChestFinale() {}

    public enum Step { CORNER_OPEN, LID_AND_CENTRE_OPEN, HARP, REVEAL }

    public static class Item {
        public final int at;
        public final Step step;
        public final int note;

        public Item(int at, Step step, int note) { this.at = at; this.step = step; this.note = note; }

        public Item(int at, Step step) { this(at, step, -1); }
    }

    public static final int REVEAL_AT = 24;

    public static List<Item> schedule(boolean reward) {
        if (!reward) return List.of(new Item(1, Step.LID_AND_CENTRE_OPEN));
        List<Item> out = new ArrayList<>();
        out.add(new Item(0, Step.CORNER_OPEN));
        out.add(new Item(1, Step.LID_AND_CENTRE_OPEN));
        for (int i = 0; i < 5; i++) out.add(new Item(4 + 5 * i, Step.HARP, i));
        out.add(new Item(REVEAL_AT, Step.REVEAL));
        return out;
    }
}
