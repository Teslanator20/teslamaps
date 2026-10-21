// Ported from ADM's p3sim/clear/puzzles/ChestJingle.kt
package com.teslamaps.adm.sim.clear.puzzles;

/** The harp notes after a reward chest opens: five, every 5 ticks, starting 3 ticks after the lid signal. */
public final class ChestJingle {
    private ChestJingle() {}

    public static final float[] PITCHES = {0.7936508f, 0.8888889f, 1.0f, 1.0952381f, 1.1904762f};
    public static final int FIRST_NOTE = 3;
    public static final int NOTE_GAP = 5;
}
