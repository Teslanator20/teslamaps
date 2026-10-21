// Ported from ADM's p3sim/run/BossHandoff.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Fight;

/** What Run.beginBoss does once the run is in BOSS: the boss practice's phase chain, unchanged. ViewPin untouched. */
public final class BossHandoff {
    private BossHandoff() {}

    /** Starts P1; Maxor's first line follows the jump by options.firstLineDelayTicks. Installed as Run.bossStarter. */
    public static void start(Run run, BossEntryOptions options) {
        // the boss changes arena blocks from here on
        ArenaRegion.markDirty();
        // distances stay pinned: Hypixel's window only re-centres at the jump
        Integer t0 = run.t0Tick;
        if (options.firstLineDelayTicks > 0) Fight.later(options.firstLineDelayTicks, "run boss start", () -> Fight.startRunBoss(options, t0));
        else Fight.startRunBoss(options, t0);
    }
}
