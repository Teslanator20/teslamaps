// Ported from ADM's p3sim/clear/bots/RoomWalk.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;

/** A floor read from the room's blocks: wanders over what can be walked and goes round what is in the way. */
public class GridArea implements WalkArea {
    public final RoomGrid grid;

    public GridArea(RoomGrid grid) { this.grid = grid; }

    @Override
    public Pt wander(Pt from, KRandom random) { return grid.randomPoint(from, random, WANDER_MIN, WANDER_MAX); }

    /** No way round (the floor is in pieces): a straight line. */
    @Override
    public List<Pt> route(Pt a, Pt b) {
        List<Pt> p = grid.path(a, b);
        return p != null ? p : List.of(b);
    }

    /** How far the next place to wander to is, blocks. */
    public static final double WANDER_MIN = 4.0;
    public static final double WANDER_MAX = 25.0;
}
