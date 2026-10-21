// Ported from ADM's p3sim/clear/bots/Traversals.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

/** What the planner asks for a room's walk: legs filling [t0, t1] starting at [from], or null for "use the plain route". */
@FunctionalInterface
public interface RoomTraversals {
    Pair<List<Leg>, Pt> legs(PlacedRoom room, Pt from, int t0, int t1, double bpt, KRandom random);
}
