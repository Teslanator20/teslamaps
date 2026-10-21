// Ported from ADM's p3sim/clear/bots/BotStrategies.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.plugin.BloodAssignment;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import com.teslamaps.adm.sim.run.plugin.StrategyContext;

/** The party strategy the bots module ships: nobody is routed to the blood room, the bots just finish their rooms. */
public final class NeutralStrategy implements PartyStrategy {
    public static final NeutralStrategy INSTANCE = new NeutralStrategy();

    private NeutralStrategy() {}

    public static final String ID = "neutral";

    @Override public String id() { return ID; }

    @Override public String label() { return "Neutral"; }

    @Override public String description() { return "The bots finish the rooms they are given. Nobody is sent to the blood room."; }

    @Override public BloodAssignment bloodAssignment(StrategyContext ctx) { return BloodAssignment.NOBODY; }
}
