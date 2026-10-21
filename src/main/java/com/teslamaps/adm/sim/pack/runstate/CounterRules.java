// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;

public class CounterRules {
    public final int totalUnits;
    public final boolean excludeEntrance;
    public final int tabRefreshTicks;
    public final JsonObject raw;

    public CounterRules(int totalUnits, boolean excludeEntrance, int tabRefreshTicks, JsonObject raw) {
        this.totalUnits = totalUnits;
        this.excludeEntrance = excludeEntrance;
        this.tabRefreshTicks = tabRefreshTicks;
        this.raw = raw;
    }
}
