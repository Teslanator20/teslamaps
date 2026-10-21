// Ported from ADM's p3sim/run/ClientRun.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.SimNet;

/** What the CLIENT thread knows about the run: only the stage (from the "runstage" message). */
public final class ClientRun {
    private ClientRun() {}

    public static volatile String runId = null;
    public static volatile String floor = null;
    public static volatile RunStage stage = null;

    private static volatile boolean lastInBoss = true;

    /** A full run is on. */
    public static boolean inRun() { return stage != null; }

    /** No run at all, or the boss stages; END keeps the stage before it. */
    public static boolean bridgeInBoss() {
        RunStage s = stage;
        if (s == null) return true;
        if (s == RunStage.END) return lastInBoss;
        return s.inBoss();
    }

    /** A run is on and it is not in the boss: Odin derives the dungeon itself. */
    public static boolean bridgeAsClear() { return inRun() && !bridgeInBoss(); }

    /** COUNTDOWN, CLEAR, BLOOD. */
    public static boolean inClear() {
        RunStage s = stage;
        return s != null && s.inDungeon();
    }

    public static void register() {
        SimNet.onClient(RunWire.KIND, data -> apply(RunWire.decode(data)));
    }

    public static void apply(RunWire.Msg m) {
        runId = m != null ? m.runId : null;
        floor = m != null ? m.floor : null;
        stage = m != null ? m.stage : null;
        if (m != null && m.stage != null && m.stage != RunStage.END) lastInBoss = m.stage.inBoss();
    }

    /** The world was left: forget the run. */
    public static void reset() { apply(null); }
}
