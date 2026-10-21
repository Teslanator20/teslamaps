// Ported from ADM's p3sim/run/RunSavepoints.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonArray;

public interface SavepointWorld {
    JsonArray capturePlayers();

    void restorePlayers(JsonArray data);

    int resyncRegions();

    void clearEntities();

    final class None implements SavepointWorld {
        public static final None INSTANCE = new None();

        private None() {}

        @Override public JsonArray capturePlayers() { return new JsonArray(); }
        @Override public void restorePlayers(JsonArray data) {}
        @Override public int resyncRegions() { return 0; }
        @Override public void clearEntities() {}
    }
}
