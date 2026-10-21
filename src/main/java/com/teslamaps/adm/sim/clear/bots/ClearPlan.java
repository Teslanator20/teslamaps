// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.plugin.BloodAssignment;
import com.teslamaps.adm.sim.run.plugin.Seat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** A whole plan. */
public class ClearPlan {
    public final List<Seat> seats;
    public final List<SeatTrack> tracks;
    public final Map<Integer, RoomPlan> rooms;
    public final Map<Integer, DoorPlan> doors;
    public final PlanMilestones milestones;
    /** The run tick the plan was built at (0 for the first plan). */
    public final int builtAt;
    public final BloodAssignment assignment;
    /** Teammates leaping to a door opener; their tracks already have them in. */
    public final List<Leap> leaps;

    public ClearPlan(List<Seat> seats, List<SeatTrack> tracks, Map<Integer, RoomPlan> rooms, Map<Integer, DoorPlan> doors, PlanMilestones milestones,
                     int builtAt, BloodAssignment assignment, List<Leap> leaps) {
        this.seats = seats;
        this.tracks = tracks;
        this.rooms = rooms;
        this.doors = doors;
        this.milestones = milestones;
        this.builtAt = builtAt;
        this.assignment = assignment;
        this.leaps = leaps;
    }

    public ClearPlan(List<Seat> seats, List<SeatTrack> tracks, Map<Integer, RoomPlan> rooms, Map<Integer, DoorPlan> doors, PlanMilestones milestones,
                     int builtAt, BloodAssignment assignment) {
        this(seats, tracks, rooms, doors, milestones, builtAt, assignment, List.of());
    }

    public SeatTrack track(int seat) {
        for (SeatTrack t : tracks) if (t.seat == seat) return t;
        return null;
    }

    /** The seat the plan gives a room to, or null. */
    public Integer ownerOf(int room) {
        RoomPlan r = rooms.get(room);
        return r != null ? r.seat : null;
    }

    /** Rooms done (white or better) at [t] by the plan, tile-weighted by [tiles]. */
    public int tilesWhiteBy(int t, Function<Integer, Integer> tiles) {
        int sum = 0;
        for (RoomPlan r : rooms.values()) {
            int w = r.whiteT != null ? r.whiteT : r.greenT != null ? r.greenT : Integer.MAX_VALUE;
            if (w <= t) sum += tiles.apply(r.room);
        }
        return sum;
    }

    public int roomsGreenBy(int t) {
        int n = 0;
        for (RoomPlan r : rooms.values()) if ((r.greenT != null ? r.greenT : Integer.MAX_VALUE) <= t) n++;
        return n;
    }
}
