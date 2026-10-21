// Ported from ADM's p3sim/run/Savepoints.kt
package com.teslamaps.adm.sim.run;

/** The store before the engine installs one: saves nothing. */
public final class NoSavepoints implements SavepointStore {
    public static final NoSavepoints INSTANCE = new NoSavepoints();

    private NoSavepoints() {}
}
