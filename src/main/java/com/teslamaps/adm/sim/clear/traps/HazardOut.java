package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

import com.teslamaps.adm.sim.clear.puzzles.hard.BlockEdit;
import java.util.ArrayList;
import java.util.HashSet;

/** What one tick of a room's hazards asks for. */
public class HazardOut {
    public final ArrayList<BlockEdit> edits = new ArrayList<>();
    public final ArrayList<HazardHit> hits = new ArrayList<>();
    public final ArrayList<SoundCue> cues = new ArrayList<>();

    /** Canonical cells that turn into falling-block entities this tick (read BEFORE edits apply). */
    public final ArrayList<int[]> debris = new ArrayList<>();

    /** Indexes of the tripwire lines touched this tick. */
    public final HashSet<Integer> touched = new HashSet<>();

    /** The arrow banks that fire this tick. */
    public final ArrayList<ArrowRule> volleys = new ArrayList<>();

    /** The flame streams to draw this tick. */
    public final ArrayList<FlameFx> flames = new ArrayList<>();
}
