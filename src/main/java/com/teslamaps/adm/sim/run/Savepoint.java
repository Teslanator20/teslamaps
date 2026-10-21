// Ported from ADM's p3sim/run/Savepoints.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonObject;

/**
 * A point a run can restart from: RunState snapshot + one entry per module + players.
 * data: { "state": ..., "modules": { "<id>": ... }, "players": [ ... ], "serverTick": n }
 */
public class Savepoint {
    public final String id;
    /** "Room: Dino Site", "Start". */
    public final String label;
    /** Run clock when taken. */
    public final int t;
    public final RunStage stage;
    /** The room being entered, or null. */
    public final Integer roomIndex;
    public final JsonObject data;

    public Savepoint(String id, String label, int t, RunStage stage, Integer roomIndex, JsonObject data) {
        this.id = id;
        this.label = label;
        this.t = t;
        this.stage = stage;
        this.roomIndex = roomIndex;
        this.data = data;
    }
}
