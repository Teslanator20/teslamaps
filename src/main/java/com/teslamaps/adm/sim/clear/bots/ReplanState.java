// Ported from ADM's p3sim/clear/bots/BotRoster.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.DoorPhase;
import com.teslamaps.adm.sim.run.DoorRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RunState;
import com.teslamaps.adm.sim.run.plugin.Seat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** Reads the run's hub into the PlanState a re-plan starts from. Pure. */
public final class ReplanState {
    private ReplanState() {}

    /**
     * [plan] is the plan being replaced; [seatPos] where each bot stands; [humanTouched] room to the tick a human last went into it; [covered]
     * whether the bots now cover for the human; [openerHint] the seat that is the door crew.
     */
    public static PlanState build(RunState state, int now, ClearPlan plan, Roster roster, Map<Integer, Pt> seatPos,
                                  Map<Integer, Integer> humanTouched, boolean covered, Integer openerHint, int touchWindow) {
        HashSet<Integer> white = new HashSet<>();
        HashSet<Integer> green = new HashSet<>();
        for (RoomRecord r : state.rooms) {
            if (r.check == CheckMark.WHITE) white.add(r.index());
            else if (r.check == CheckMark.GREEN) { white.add(r.index()); green.add(r.index()); }
        }
        HashMap<Integer, Integer> passable = new HashMap<>();
        for (DoorRecord d : state.doors) {
            if (d.plan.kind == DoorKind.NORMAL || !d.plan.kind.connected()) continue;
            if (d.phase != DoorPhase.CLOSED) passable.put(d.index(), (d.openedAt != null ? d.openedAt : now) + BotTuning.DOOR_PASSABLE_AFTER);
        }
        HashSet<UUID> humanIds = new HashSet<>();
        for (Seat s : roster.humans()) {
            UUID id = roster.uuids.get(s.index);
            if (id != null) humanIds.add(id);
        }
        ArrayList<Integer> occupied = new ArrayList<>();
        for (RoomRecord r : state.rooms) {
            boolean any = false;
            for (UUID o : r.occupants) if (humanIds.contains(o)) { any = true; break; }
            if (any) occupied.add(r.index());
        }
        ArrayList<Integer> touched = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : humanTouched.entrySet()) {
            if (now - e.getValue() <= touchWindow && !green.contains(e.getKey())) touched.add(e.getKey());
        }
        LinkedHashSet<Integer> humanRooms = new LinkedHashSet<>();
        for (int r : occupied) if (!green.contains(r)) humanRooms.add(r);
        for (int r : touched) if (!green.contains(r)) humanRooms.add(r);
        ArrayList<Commit> commits = new ArrayList<>();
        HashMap<Integer, Integer> free = new HashMap<>();
        for (Seat s : roster.bots()) {
            free.put(s.index, now);
            if (plan == null) continue;
            RoomPlan mine = null;
            for (RoomPlan it : plan.rooms.values()) {
                if (it.seat != s.index || it.soft || it.arriveT > now) continue;
                int end = it.greenT != null ? it.greenT : it.whiteT != null ? it.whiteT : 0;
                if (end <= now || green.contains(it.room) || humanRooms.contains(it.room)) continue;
                if (mine == null || it.arriveT > mine.arriveT) mine = it;
            }
            if (mine != null) commits.add(new Commit(s.index, mine.room, mine.work, mine.arriveT, mine.whiteT,
                    mine.greenT != null ? mine.greenT : mine.whiteT != null ? mine.whiteT : now));
        }
        return new PlanState(now, white, green, passable, state.keys.witherHeld, seatPos, free, commits, humanRooms, !covered, openerHint);
    }

    public static PlanState build(RunState state, int now, ClearPlan plan, Roster roster, Map<Integer, Pt> seatPos,
                                  Map<Integer, Integer> humanTouched, boolean covered, Integer openerHint) {
        return build(state, now, plan, roster, seatPos, humanTouched, covered, openerHint, BotTuning.COVER_AFTER_TICKS);
    }
}
