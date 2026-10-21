package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/ChestReward.kt

import java.util.ArrayList;
import java.util.List;

/** The ticks after a reward chest click: open sounds, the lid event, five harp notes. */
public final class ChestFinale {
    private ChestFinale() {}

    public enum Step { OPEN_SOUND, OPEN_SOUND_CENTRE, LID_EVENT, HARP }

    public static class Item {
        public final int at;
        public final Step step;
        public final float pitch;

        public Item(int at, Step step, float pitch) {
            this.at = at;
            this.step = step;
            this.pitch = pitch;
        }

        public Item(int at, Step step) { this(at, step, 1f); }
    }

    /** The harp pitches of the jingle, in order. */
    public static final float[] HARP_PITCHES = {0.7936508f, 0.8888889f, 1.0f, 1.0952381f, 1.1904762f};

    public static final int CLICK_TO_LID = 3;

    public static List<Item> schedule() {
        List<Item> out = new ArrayList<>();
        out.add(new Item(CLICK_TO_LID - 1, Step.OPEN_SOUND, 0.984f));
        out.add(new Item(CLICK_TO_LID, Step.OPEN_SOUND_CENTRE, 0.91f));
        out.add(new Item(CLICK_TO_LID, Step.LID_EVENT));
        for (int i = 0; i < HARP_PITCHES.length; i++) out.add(new Item(CLICK_TO_LID + 3 + 5 * i, Step.HARP, HARP_PITCHES[i]));
        return out;
    }
}
