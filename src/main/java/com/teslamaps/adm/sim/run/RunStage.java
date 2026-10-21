// Ported from ADM's p3sim/run/RunStage.kt
package com.teslamaps.adm.sim.run;

public enum RunStage {
    COUNTDOWN, CLEAR, BLOOD, BOSS_ENTRY, BOSS, END;

    /** "In boss" for Odin. */
    public boolean inBoss() { return this == BOSS_ENTRY || this == BOSS; }

    /** The clear surfaces are live. */
    public boolean inDungeon() { return this == COUNTDOWN || this == CLEAR || this == BLOOD; }

    /** Forward by one, or to END from anywhere before it. */
    public static boolean canGo(RunStage from, RunStage to) {
        return (to == END && from != END) || (to.ordinal() == from.ordinal() + 1 && to != END);
    }
}
