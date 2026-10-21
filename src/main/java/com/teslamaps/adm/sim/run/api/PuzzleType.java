// Ported from ADM's p3sim/run/api/PuzzleApi.kt
package com.teslamaps.adm.sim.run.api;

import java.util.Set;
import java.util.function.Function;

/** A puzzle kind the dungeon can contain, listed by a module's puzzleTypes(). */
public class PuzzleType {
    public final String kind;
    /** The name in the tab's `Puzzles (N)` list. */
    public final String tabName;
    /** Rooms-pack families that carry this puzzle. */
    public final Set<String> families;
    public final Function<PuzzleContext, Puzzle> create;

    public PuzzleType(String kind, String tabName, Set<String> families, Function<PuzzleContext, Puzzle> create) {
        this.kind = kind;
        this.tabName = tabName;
        this.families = families;
        this.create = create;
    }
}
