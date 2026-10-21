// Ported from ADM's p3sim/clear/bots/BotHealth.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;

/** Decides each tick, for each teammate that is fighting, whether a mob lands a hit and how big it is. */
public class HitClock {
    private final KRandom random;

    public HitClock(KRandom random) { this.random = random; }

    /** The size of the hit this tick, or null for none. */
    public Double roll() { return random.nextDouble() < BotHealth.HITS_PER_MINUTE / 1200.0 ? (Double) BotHealth.hitPercent(random.nextDouble()) : null; }
}
