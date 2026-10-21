// Ported from ADM's p3sim/run/DungeonPlan.kt
package com.teslamaps.adm.sim.run;

/** Why a layout could not be turned into a plan (plain English). */
public class PlanFailure {
    public final String message;

    public PlanFailure(String message) { this.message = message; }
}
