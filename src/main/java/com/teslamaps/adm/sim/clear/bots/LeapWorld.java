// Ported from ADM's p3sim/clear/bots/Leaps.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.List;

/** What the leap pass needs from the plan's geometry. */
public interface LeapWorld {
    /** Whether the walk from [from] to [to] goes through door [door]. */
    boolean through(Pt from, Pt to, int door);

    /** The legs of a walk from [from] to [to] starting at [t0]. */
    List<Leg> walk(Pt from, Pt to, int t0);
}
