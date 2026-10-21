// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import java.util.UUID;

/** One puzzle of the layout. Puzzles module. */
public class PuzzleRecord {
    public final int roomIndex;
    /** BLAZE BOULDER BEAMS WATER_BOARD TP_MAZE ICE_PATH WEIRDOS QUIZ TTT ICE_FILL */
    public final String kind;
    public final String tabName;

    public PuzzleRecord(int roomIndex, String kind, String tabName) {
        this.roomIndex = roomIndex;
        this.kind = kind;
        this.tabName = tabName;
    }

    public PuzzleStatus status = PuzzleStatus.UNSOLVED;
    public boolean revealed = false;
    public int failures = 0;
    public UUID solvedBy = null;
}
