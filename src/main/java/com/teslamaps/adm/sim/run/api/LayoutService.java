// Ported from ADM's p3sim/run/api/LayoutApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlanFailure;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.WorldRegion;

/** Builds the dungeon. Implemented by LayoutModule. */
public interface LayoutService {
    /** Chooses (LayoutPick.pick) and resolves the layout. Pure. */
    default DungeonPlan.Result plan(RunConfig config, PackSet packs) {
        return new DungeonPlan.Result.Failed(new PlanFailure("The layout builder is not installed yet."));
    }

    /** The world region holding the dungeon's blocks; fill runs on worker threads. Null when not available. */
    default WorldRegion region(DungeonPlan plan, PackSet packs) { return null; }
}
