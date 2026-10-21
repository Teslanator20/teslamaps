// Ported from ADM's p3sim/clear/bots/ClearPlanner.kt
package com.teslamaps.adm.sim.clear.bots;

/** A room a seat has already started: the plan keeps it. */
public class Commit {
    public final int seat;
    public final int room;
    public final RoomWork work;
    public final int arriveT;
    public final Integer whiteT;
    public final int greenT;

    public Commit(int seat, int room, RoomWork work, int arriveT, Integer whiteT, int greenT) {
        this.seat = seat;
        this.room = room;
        this.work = work;
        this.arriveT = arriveT;
        this.whiteT = whiteT;
        this.greenT = greenT;
    }
}
