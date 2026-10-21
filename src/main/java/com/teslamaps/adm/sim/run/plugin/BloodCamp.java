// Ported from ADM's p3sim/run/plugin/BloodCamp.kt
package com.teslamaps.adm.sim.run.plugin;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.run.RunEvent;

/** One camp. Ticks on the server thread. */
public interface BloodCamp {
    /** The blood door has just opened and the Watcher is in the room. */
    void start();

    /** Once per server tick while the camp runs (including the tick of start). */
    void tick();

    /** Every run event. Default: ignore. */
    default void onEvent(RunEvent event) {}

    /** True when the camp is over ("You may pass." follows). Read after every tick. */
    boolean finished();

    default JsonObject snapshot() { return null; }

    default void restore(JsonObject data) {}
}
