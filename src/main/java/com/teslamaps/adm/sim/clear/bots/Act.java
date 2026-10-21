// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

/** What a seat does at a tick, besides moving. Windows run from t() to until(). */
public interface Act {
    int seat();

    /** First tick of the act. */
    int t();

    /** Last tick of the window; equal to t() for a single action. */
    int until();
}
