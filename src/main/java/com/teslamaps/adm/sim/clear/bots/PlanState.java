// Ported from ADM's p3sim/clear/bots/ClearPlanner.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** What a (re-)plan starts from. The first plan is initial(). */
public class PlanState {
    public final int now;
    /** Rooms already WHITE (mobs dead) and rooms already GREEN. */
    public final Set<Integer> white;
    public final Set<Integer> green;
    /** Doors that are open or falling: door index to the tick from which a walker may pass. */
    public final Map<Integer, Integer> passableAt;
    /** Wither keys the party holds now. */
    public final int witherKeys;
    public final Map<Integer, Pt> seatPos;
    /** The tick each seat is next free. */
    public final Map<Integer, Integer> seatFreeAt;
    public final List<Commit> commits;
    /** Rooms the human has taken or stands in. */
    public final Set<Integer> humanRooms;
    /** Whether the human seat still takes part in the plan. */
    public final boolean humanSeatActive;
    /** The seat that has been opening the doors (null: draw one). */
    public final Integer openerHint;

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt, int witherKeys, Map<Integer, Pt> seatPos,
                     Map<Integer, Integer> seatFreeAt, List<Commit> commits, Set<Integer> humanRooms, boolean humanSeatActive, Integer openerHint) {
        this.now = now;
        this.white = white;
        this.green = green;
        this.passableAt = passableAt;
        this.witherKeys = witherKeys;
        this.seatPos = seatPos;
        this.seatFreeAt = seatFreeAt;
        this.commits = commits;
        this.humanRooms = humanRooms;
        this.humanSeatActive = humanSeatActive;
        this.openerHint = openerHint;
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt, int witherKeys, Map<Integer, Pt> seatPos,
                     Map<Integer, Integer> seatFreeAt, List<Commit> commits, Set<Integer> humanRooms, boolean humanSeatActive) {
        this(now, white, green, passableAt, witherKeys, seatPos, seatFreeAt, commits, humanRooms, humanSeatActive, null);
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt, int witherKeys, Map<Integer, Pt> seatPos,
                     Map<Integer, Integer> seatFreeAt, List<Commit> commits, Set<Integer> humanRooms) {
        this(now, white, green, passableAt, witherKeys, seatPos, seatFreeAt, commits, humanRooms, true, null);
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt, int witherKeys, Map<Integer, Pt> seatPos,
                     Map<Integer, Integer> seatFreeAt, List<Commit> commits) {
        this(now, white, green, passableAt, witherKeys, seatPos, seatFreeAt, commits, Set.of(), true, null);
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt, int witherKeys, Map<Integer, Pt> seatPos,
                     Map<Integer, Integer> seatFreeAt) {
        this(now, white, green, passableAt, witherKeys, seatPos, seatFreeAt, List.of(), Set.of(), true, null);
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt, int witherKeys, Map<Integer, Pt> seatPos) {
        this(now, white, green, passableAt, witherKeys, seatPos, Map.of(), List.of(), Set.of(), true, null);
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt, int witherKeys) {
        this(now, white, green, passableAt, witherKeys, Map.of(), Map.of(), List.of(), Set.of(), true, null);
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green, Map<Integer, Integer> passableAt) {
        this(now, white, green, passableAt, 0, Map.of(), Map.of(), List.of(), Set.of(), true, null);
    }

    public PlanState(int now, Set<Integer> white, Set<Integer> green) {
        this(now, white, green, Map.of(), 0, Map.of(), Map.of(), List.of(), Set.of(), true, null);
    }

    public PlanState(int now, Set<Integer> white) {
        this(now, white, Set.of(), Map.of(), 0, Map.of(), Map.of(), List.of(), Set.of(), true, null);
    }

    public PlanState(int now) {
        this(now, Set.of(), Set.of(), Map.of(), 0, Map.of(), Map.of(), List.of(), Set.of(), true, null);
    }

    public static PlanState initial() { return new PlanState(0); }
}
