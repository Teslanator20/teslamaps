// Ported from ADM's p3sim/clear/blood/BloodExit.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.util.KRandom;

/** The clock of the blood room's exit, counted from the defeat tick D (pure). */
public class BloodExit {
    /** Absolute server tick of the defeat. */
    public final int defeatTick;
    /** PORTAL_SHORT or PORTAL_LONG. */
    public final int portalDelay;

    public BloodExit(int defeatTick, int portalDelay) {
        this.defeatTick = defeatTick;
        this.portalDelay = portalDelay;
    }

    public int portalAt() { return defeatTick + portalDelay; }

    public int strikeAt() { return portalAt() - 1; }

    public int watcherGoneAt() { return defeatTick + WATCHER_GONE; }

    public static final int PORTAL_SHORT = 80;
    public static final int PORTAL_LONG = 90;
    public static final double P_LONG = 24.0 / 36.0;
    public static final int WATCHER_GONE = 81;
    public static final int ENTRY_LAG = 1;

    public static BloodExit roll(int defeatTick, KRandom random) {
        return new BloodExit(defeatTick, random.nextDouble() < P_LONG ? PORTAL_LONG : PORTAL_SHORT);
    }
}
