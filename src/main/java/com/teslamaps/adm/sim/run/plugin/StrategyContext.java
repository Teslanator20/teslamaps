// Ported from ADM's p3sim/run/plugin/PartyStrategy.kt
package com.teslamaps.adm.sim.run.plugin;

import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;

/** What a strategy sees. May gain members. */
public interface StrategyContext {
    DungeonPlan plan();

    List<Seat> seats();

    KRandom random();

    /** null when that pack is missing. */
    RunStatePack tables();

    /** Run ticks now. */
    int now();
}
