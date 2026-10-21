// Ported from ADM's p3sim/clear/bots/RoomWalk.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;

/** Where a bot may wander in a room and how it gets from one place to another there. */
public interface WalkArea {
    /** A place to wander to from [from], or null when there is nowhere to go. */
    Pt wander(Pt from, KRandom random);

    /** The points to pass on the way from [a] to [b], the last one being where the walker ends up. */
    List<Pt> route(Pt a, Pt b);
}
