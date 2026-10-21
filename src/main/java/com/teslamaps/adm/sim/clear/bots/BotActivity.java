// Ported from ADM's p3sim/clear/bots/BotMotion.kt
package com.teslamaps.adm.sim.clear.bots;

/** What a bot is busy with, which decides what it holds and whether it swings. */
public enum BotActivity {
    STAND, WALK, FIGHT, SEARCH, PUZZLE;

    public boolean working() { return this == FIGHT || this == SEARCH || this == PUZZLE; }
}
