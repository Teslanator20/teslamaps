// Ported from ADM's p3sim/run/api/PuzzleApi.kt
package com.teslamaps.adm.sim.run.api;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import java.util.UUID;

/** One puzzle of the dungeon; in-world state exactly as Odin's solver reads it. Server thread only. */
public interface Puzzle {
    /** BLAZE, BOULDER, BEAMS, WATER_BOARD, TP_MAZE, ICE_PATH, WEIRDOS, QUIZ, TTT, ICE_FILL */
    String kind();

    PuzzleContext context();

    default PuzzleStatus status() { return context().record.status; }

    /** Once per run (and again after a restore, then restore). */
    default void onRunStart() {}

    default void onRoomEntered(UUID player) {}

    /** Every tick in CLEAR or BLOOD while a player is within the room's chunks. */
    default void onTick() {}

    /** A right click on a block in this room; true = consumed. */
    default boolean onInteract(UUID player, int x, int y, int z) { return false; }

    /** Quiz, Three Weirdos: the NPC with this name was clicked. True = consumed. */
    default boolean onNpcClick(UUID player, String npcName) { return false; }

    /** The reset item: back to the unsolved start (no failure counted). */
    default void onReset() {}

    default JsonObject snapshot() { return null; }

    default void restore(JsonObject data) {}
}
