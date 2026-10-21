// Ported from ADM's p3sim/run/RunSystem.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonObject;

/** One participant in a run's lifecycle, called in AdsModules order on the sim server thread. */
public interface RunSystem {
    /** Stable lowercase id; also its savepoint key. */
    String id();

    default void onRunStart(Run run) {}

    default void onTick(Run run) {}

    default void onStage(Run run, RunStage from, RunStage to) {}

    default void onEvent(Run run, RunEvent event) {}

    default void onRunEnd(Run run, EndReason reason) {}

    /** Ids, numbers, strings only. */
    default JsonObject snapshot(Run run) { return null; }

    default void restore(Run run, JsonObject data) {}
}
