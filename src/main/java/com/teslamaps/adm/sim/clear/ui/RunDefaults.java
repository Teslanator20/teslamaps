package com.teslamaps.adm.sim.clear.ui;
// Ported from ADM's p3sim/clear/ui/RunArgs.kt

import com.teslamaps.adm.sim.clear.bots.BotStrategies;
import com.teslamaps.adm.sim.run.LagPreset;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import java.util.List;
import java.util.Objects;

public final class RunDefaults {
    public final boolean bots;
    public final int partySize;
    public final LagPreset lag;
    public final boolean noDeaths;
    public final boolean mobHpFromPack;
    public final String strategy;

    public RunDefaults(boolean bots, int partySize, LagPreset lag, boolean noDeaths, boolean mobHpFromPack, String strategy) {
        this.bots = bots;
        this.partySize = partySize;
        this.lag = lag;
        this.noDeaths = noDeaths;
        this.mobHpFromPack = mobHpFromPack;
        this.strategy = strategy;
    }

    public RunDefaults(boolean bots, int partySize, LagPreset lag, boolean noDeaths, boolean mobHpFromPack) { this(bots, partySize, lag, noDeaths, mobHpFromPack, StrategyChoice.AUTO); }

    public RunDefaults(boolean bots, int partySize, LagPreset lag, boolean noDeaths) { this(bots, partySize, lag, noDeaths, false); }

    public RunDefaults(boolean bots, int partySize, LagPreset lag) { this(bots, partySize, lag, false); }

    public RunDefaults(boolean bots, int partySize) { this(bots, partySize, LagPreset.OFF); }

    public RunDefaults(boolean bots) { this(bots, 5); }

    public RunDefaults() { this(true); }

    public RunConfig config(LayoutSource layout, long seed) { return config(layout, seed, BotStrategies.available()); }

    public RunConfig config(LayoutSource layout, long seed, List<PartyStrategy> offered) {
        return new RunConfig("F7", layout, seed, bots, bots ? Math.clamp(partySize, 1, 5) : 1,
                lag, noDeaths, mobHpFromPack ? null : Double.valueOf(1.0), null,
                StrategyChoice.resolve(strategy, offered), null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RunDefaults d)) return false;
        return bots == d.bots && partySize == d.partySize && lag == d.lag && noDeaths == d.noDeaths && mobHpFromPack == d.mobHpFromPack && Objects.equals(strategy, d.strategy);
    }

    @Override
    public int hashCode() { return Objects.hash(bots, partySize, lag, noDeaths, mobHpFromPack, strategy); }

    @Override
    public String toString() {
        return "RunDefaults(bots=" + bots + ", partySize=" + partySize + ", lag=" + lag + ", noDeaths=" + noDeaths + ", mobHpFromPack=" + mobHpFromPack + ", strategy=" + strategy + ")";
    }
}
