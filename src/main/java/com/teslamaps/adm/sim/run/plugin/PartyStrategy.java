// Ported from ADM's p3sim/run/plugin/PartyStrategy.kt
package com.teslamaps.adm.sim.run.plugin;

/** A party strategy preset: who goes to the blood room and when. ADS ships only "neutral". */
public interface PartyStrategy {
    /** Used in settings and RunConfig.strategy. */
    String id();

    String label();

    /** One sentence for the tooltip. */
    default String description() { return ""; }

    /** Once when the ClearPlan is built; again on a re-plan. */
    BloodAssignment bloodAssignment(StrategyContext ctx);
}
