// Ported from ADM's p3sim/clear/bots/BotStrategies.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.plugin.AdsPlugins;
import com.teslamaps.adm.sim.run.plugin.BloodAssignment;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.run.plugin.StrategyContext;
import java.util.ArrayList;
import java.util.List;

public final class BotStrategies {
    private BotStrategies() {}

    /** The strategy named by [id]: the built-in neutral one for null, "neutral" or a name no plug-in offers. */
    public static PartyStrategy resolve(String id) {
        if (id == null || id.equals(NeutralStrategy.ID)) return NeutralStrategy.INSTANCE;
        PartyStrategy s = AdsPlugins.strategy(id);
        return s != null ? s : NeutralStrategy.INSTANCE;
    }

    /** Every choice for a settings list: the neutral default first, then what the plug-ins offer. */
    public static List<PartyStrategy> available() {
        ArrayList<PartyStrategy> out = new ArrayList<>();
        out.add(NeutralStrategy.INSTANCE);
        for (PartyStrategy s : AdsPlugins.strategies()) if (!s.id().equals(NeutralStrategy.ID)) out.add(s);
        return out;
    }

    /** The blood assignment of [strategy] for these seats, guarded: a plug-in that throws, or names a bad seat or the human, gets NOBODY. */
    public static BloodAssignment assignment(PartyStrategy strategy, StrategyContext ctx) {
        BloodAssignment a;
        try { a = strategy.bloodAssignment(ctx); } catch (Throwable t) { return BloodAssignment.NOBODY; }
        Integer seat = a.seat;
        if (seat == null) return a;
        Seat s = null;
        for (Seat it : ctx.seats()) if (it.index == seat) { s = it; break; }
        return s == null || s.isHuman ? BloodAssignment.NOBODY : a;
    }
}
