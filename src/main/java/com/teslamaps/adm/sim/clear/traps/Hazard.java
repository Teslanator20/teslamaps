package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.clear.puzzles.hard.BlockEdit;
import java.util.List;

/** A hazard of one room beyond the crushers and strings (falling floor, arrow trap, flamethrower). Runs on the room's clock. */
public interface Hazard {
    void tick(int clock, List<PlayerPos> players, HazardOut out);

    default void snapshot(JsonObject o) {}

    default void restore(JsonObject o) {}

    /** The block edits that put the hazard's changes back after a savepoint restore. */
    default List<BlockEdit> currentEdits() { return List.of(); }
}
