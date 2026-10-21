// Ported from ADM's p3sim/run/ClientRun.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Objects;
import java.util.function.BiConsumer;

/** The wire form of the run's stage, server to client (SimNet kind KIND). Pure. */
public final class RunWire {
    private RunWire() {}

    public static final String KIND = "runstage";

    /** stage null = no run. */
    public static final class Msg {
        public final String runId;
        public final String floor;
        public final RunStage stage;

        public Msg(String runId, String floor, RunStage stage) {
            this.runId = runId;
            this.floor = floor;
            this.stage = stage;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Msg x && Objects.equals(runId, x.runId) && Objects.equals(floor, x.floor) && stage == x.stage;
        }
        @Override public int hashCode() { return Objects.hash(runId, floor, stage); }
        @Override public String toString() { return "Msg(runId=" + runId + ", floor=" + floor + ", stage=" + stage + ")"; }
    }

    public static String encode(String runId, String floor, RunStage stage) {
        JsonObject o = new JsonObject();
        if (runId != null) o.addProperty("run", runId);
        if (floor != null) o.addProperty("floor", floor);
        if (stage != null) o.addProperty("stage", stage.name());
        return o.toString();
    }

    private static String prim(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    public static Msg decode(String text) {
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            String s = prim(o, "stage");
            RunStage stage = null;
            for (RunStage e : RunStage.values()) if (e.name().equals(s)) { stage = e; break; }
            return new Msg(prim(o, "run"), prim(o, "floor"), stage);
        } catch (Exception e) {
            return null;
        }
    }

    /** How the server sends to every game in the sim. AdsBootstrap installs SimNet.sendAll; a no-op until then. */
    public static volatile BiConsumer<String, String> sender = (kind, data) -> {};

    /** Server side: every game hears the run's stage (or that there is none). */
    public static void broadcast(Run run) {
        sender.accept(KIND, encode(run != null ? run.id : null, run != null ? run.config.floor : null, run == null || run.isOver() ? null : run.stage));
    }
}
