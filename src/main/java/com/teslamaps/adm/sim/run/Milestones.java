// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

/** Run-level clock readings. */
public class Milestones {
    /** Absolute server ticks. */
    public Integer countdownStartTick = null;
    public Integer t0Tick = null;
    /** Run ticks. */
    public Integer bloodKeyT = null;
    public Integer bloodOpenT = null;
    public Integer watcherDoneT = null;
    public Integer bossEntryT = null;
    public Integer bossDefeatedT = null;
    public Integer endT = null;
}
