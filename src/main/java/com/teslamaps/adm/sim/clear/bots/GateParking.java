// Ported from ADM's p3sim/clear/bots/ClearSim.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.plugin.BloodAssignment;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.run.plugin.StrategyContext;
import java.util.List;

/** What the headless gate uses when it is given no strategy: it parks the last bot seat in the blood room. A run never uses it. */
final class GateParking implements PartyStrategy {
    public static final GateParking INSTANCE = new GateParking();

    private GateParking() {}

    @Override public String id() { return "gate-parking"; }

    @Override public String label() { return "Gate parking"; }

    @Override
    public BloodAssignment bloodAssignment(StrategyContext ctx) {
        List<Seat> seats = ctx.seats();
        for (int i = seats.size() - 1; i >= 0; i--) if (!seats.get(i).isHuman) return new BloodAssignment(seats.get(i).index);
        return BloodAssignment.NOBODY;
    }
}
