// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;

import java.util.Map;

/** Milestones, ticks since T0. */
public class Splits {
    public final Map<String, Dist> dists;
    public final JsonObject raw;

    public Splits(Map<String, Dist> dists, JsonObject raw) { this.dists = dists; this.raw = raw; }

    public Dist bloodOpen() { return dists.get("bloodOpen"); }
    public Dist bloodKey() { return dists.get("bloodKey"); }
    public Dist watcherDone() { return dists.get("watcherDone"); }
    public Dist maxorEntry() { return dists.get("maxorEntry"); }
    public Dist defeated() { return dists.get("defeated"); }
    public Dist campTicks() { return dists.get("campTicks"); }
    public Dist provenToMaxor() { return dists.get("provenToMaxor"); }
    public Dist tilesDoneAtMaxor() { return dists.get("tilesDoneAtMaxor"); }
    public Dist roomsGreenFracAtMaxor() { return dists.get("roomsGreenFracAtMaxor"); }
}
