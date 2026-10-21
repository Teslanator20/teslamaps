// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

/** The ticks the plan implies for the run's milestones. */
public class PlanMilestones {
    public final int bloodKeyT;
    public final int bloodOpenT;
    /** The Watcher's "proven" line and the first boss line, drawn from the pack's milestone tables. */
    public final int watcherDoneT;
    public final int maxorT;
    /** The seat routed to the blood room, or null. */
    public final Integer bloodSeat;

    public PlanMilestones(int bloodKeyT, int bloodOpenT, int watcherDoneT, int maxorT, Integer bloodSeat) {
        this.bloodKeyT = bloodKeyT;
        this.bloodOpenT = bloodOpenT;
        this.watcherDoneT = watcherDoneT;
        this.maxorT = maxorT;
        this.bloodSeat = bloodSeat;
    }
}
