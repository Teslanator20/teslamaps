// Ported from ADM's p3sim/run/api/SecretsApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;

/** Implemented by ScoreModule. Server thread only. */
public interface ScoreService {
    /** Recomputes the team score now. Posts ScoreUpdated. */
    default int recompute(Run run) { return run.state.score.score; }

    /** The end screen as the run stands (the last published score). */
    default EndScreen endScreen(Run run) {
        return new EndScreen(run.state.score.score, "D", run.state.counters.clearedPct(), run.state.secrets.teamFound, run.state.secrets.teamMax,
                run.state.secrets.crypts, run.state.deaths.teamDeaths, run.t());
    }

    /** Puts the end screen up for everyone. The engine calls this at END. */
    default void showEndScreen(Run run) {}
}
