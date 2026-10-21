// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

/** What the plan says about one room. [soft]: it belongs to the human seat. */
public class RoomPlan {
    public final int room;
    public final int seat;
    public final RoomWork work;
    public final int arriveT;
    public final Integer whiteT;
    public final Integer greenT;
    public final boolean soft;

    public RoomPlan(int room, int seat, RoomWork work, int arriveT, Integer whiteT, Integer greenT, boolean soft) {
        this.room = room;
        this.seat = seat;
        this.work = work;
        this.arriveT = arriveT;
        this.whiteT = whiteT;
        this.greenT = greenT;
        this.soft = soft;
    }
}
