// Ported from ADM's p3sim/clear/bots/ClearPlanner.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.plugin.BloodAssignment;
import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;
import java.util.Map;

/** Everything the planner reads. */
public class PlannerInput {
    public final DungeonPlan plan;
    public final BotTables tables;
    public final List<Seat> seats;
    public final KRandom random;
    public final BloodAssignment assignment;
    public final PlanState state;
    /** Multiplies the planning speed (setting "Bots Speed Scale"). */
    public final double speedScale;
    public final double workScale;
    /** Recorded walks through rooms, when the optional pack is installed; null: the plain route. */
    public final RoomTraversals traversals;
    /** Where each bot seat stands at tick 0 of the first plan. */
    public final Map<Integer, Pt> startAt;
    /** The floor of a room as a bot can walk it; null: the bots wander over the room's tiles. */
    public final RoomAreas areas;
    /** Wither doors people opened since the last plan: the bots may leap to them. */
    public final List<PersonDoor> people;

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment, PlanState state, double speedScale,
                        double workScale, RoomTraversals traversals, Map<Integer, Pt> startAt, RoomAreas areas, List<PersonDoor> people) {
        this.plan = plan;
        this.tables = tables;
        this.seats = seats;
        this.random = random;
        this.assignment = assignment;
        this.state = state;
        this.speedScale = speedScale;
        this.workScale = workScale;
        this.traversals = traversals;
        this.startAt = startAt;
        this.areas = areas;
        this.people = people;
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment, PlanState state, double speedScale,
                        double workScale, RoomTraversals traversals, Map<Integer, Pt> startAt, RoomAreas areas) {
        this(plan, tables, seats, random, assignment, state, speedScale, workScale, traversals, startAt, areas, List.of());
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment, PlanState state, double speedScale,
                        double workScale, RoomTraversals traversals, Map<Integer, Pt> startAt) {
        this(plan, tables, seats, random, assignment, state, speedScale, workScale, traversals, startAt, null, List.of());
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment, PlanState state, double speedScale,
                        double workScale, RoomTraversals traversals) {
        this(plan, tables, seats, random, assignment, state, speedScale, workScale, traversals, Map.of(), null, List.of());
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment, PlanState state, double speedScale,
                        double workScale) {
        this(plan, tables, seats, random, assignment, state, speedScale, workScale, null, Map.of(), null, List.of());
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment, PlanState state, double speedScale) {
        this(plan, tables, seats, random, assignment, state, speedScale, BotTuning.ROOM_WORK_SCALE, null, Map.of(), null, List.of());
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment, PlanState state) {
        this(plan, tables, seats, random, assignment, state, 1.0, BotTuning.ROOM_WORK_SCALE, null, Map.of(), null, List.of());
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random, BloodAssignment assignment) {
        this(plan, tables, seats, random, assignment, PlanState.initial(), 1.0, BotTuning.ROOM_WORK_SCALE, null, Map.of(), null, List.of());
    }

    public PlannerInput(DungeonPlan plan, BotTables tables, List<Seat> seats, KRandom random) {
        this(plan, tables, seats, random, BloodAssignment.NOBODY, PlanState.initial(), 1.0, BotTuning.ROOM_WORK_SCALE, null, Map.of(), null, List.of());
    }
}
