// Ported from ADM's p3sim/run/api/PuzzleApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;
import java.util.UUID;

/** The shared puzzle frame. Implemented by PuzzlesModule. */
public interface PuzzleHost {
    /** The live puzzle in a room, or null. */
    default Puzzle puzzleOf(Run run, int roomIndex) { return null; }

    /** SOLVED, PuzzleSolved, "PUZZLE SOLVED!", the tab row, the reward chest. */
    default void reportSolved(Puzzle puzzle, UUID by) {}

    /** FAILED, PuzzleFailed, "PUZZLE FAIL!"; reason is the line's generic ending. */
    default void reportFailed(Puzzle puzzle, UUID by, String reason) {}

    /** The reward chest finished opening. */
    default void rewardChestOpened(Puzzle puzzle, UUID by) {}
}
