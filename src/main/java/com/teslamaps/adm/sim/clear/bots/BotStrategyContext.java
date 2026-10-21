// Ported from ADM's p3sim/clear/bots/BotStrategies.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.run.plugin.StrategyContext;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;

/** What a strategy sees at plan time. */
public class BotStrategyContext implements StrategyContext {
    private final DungeonPlan plan;
    private final List<Seat> seats;
    private final KRandom random;
    private final RunStatePack tables;
    private final int now;

    public BotStrategyContext(DungeonPlan plan, List<Seat> seats, KRandom random, RunStatePack tables, int now) {
        this.plan = plan;
        this.seats = seats;
        this.random = random;
        this.tables = tables;
        this.now = now;
    }

    @Override public DungeonPlan plan() { return plan; }

    @Override public List<Seat> seats() { return seats; }

    @Override public KRandom random() { return random; }

    @Override public RunStatePack tables() { return tables; }

    @Override public int now() { return now; }
}
