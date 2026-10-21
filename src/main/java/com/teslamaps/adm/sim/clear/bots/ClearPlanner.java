// Ported from ADM's p3sim/clear/bots/ClearPlanner.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.runstate.Dist;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds a ClearPlan from a dungeon, the measured tables and the party: the door and key schedule first, then the rooms by list scheduling,
 * with every walk routed through the door graph and held at closed doors. Deterministic from PlannerInput.random. Pure.
 */
public class ClearPlanner {
    private final PlannerInput inp;
    private final DungeonPlan plan;
    private final PlanGeometry geo;
    private final BotTables tables;
    private final PlanState st;
    private final KRandom rnd;
    private final PlanMath.Pace pace;
    private final PlanMath.Pace roomPace;
    private final KRandom pick;
    /** The door-to-door and last-door-to-blood-key gaps: independent of the party's pace. */
    private final KRandom gaps;

    private final int entrance;
    private final Integer fairy;
    private final Integer bloodRoom;

    /** From when each door may be walked through. Filled by scheduleDoors; plain doorways are 0. */
    private final HashMap<Integer, Integer> pass = new HashMap<>();

    private static final class Builder {
        final Seat seat;
        int t;
        Pt pos;
        int room;
        final ArrayList<Leg> legs = new ArrayList<>();
        final ArrayList<Act> acts = new ArrayList<>();
        /** Work this seat must be somewhere for at a set time, earliest first. */
        final ArrayList<Duty> duties = new ArrayList<>();

        Builder(Seat seat, int t, Pt pos, int room) {
            this.seat = seat;
            this.t = t;
            this.pos = pos;
            this.room = room;
        }

        /** The duty that is due first, or null. */
        Duty duty() {
            Duty best = null;
            for (Duty d : duties) if (best == null || d.startBy < best.startBy) best = d;
            return best;
        }

        void stand(int until) { if (until > t) { legs.add(new Leg(t, until, pos, pos)); t = until; } }
    }

    /** Work a seat must be somewhere for at a set time. */
    private static final class Duty {
        final int startBy;
        final Consumer<Builder> run;

        Duty(int startBy, Consumer<Builder> run) {
            this.startBy = startBy;
            this.run = run;
        }
    }

    private final long taskSeed;

    public ClearPlanner(PlannerInput inp) {
        this.inp = inp;
        this.plan = inp.plan;
        this.geo = new PlanGeometry(plan);
        this.tables = inp.tables;
        this.st = inp.state;
        this.rnd = inp.random;
        this.pace = new PlanMath.Pace(new KRandom(rnd.nextLong()), BotTuning.PACE_CORRELATION);
        this.roomPace = new PlanMath.Pace(new KRandom(rnd.nextLong()), BotTuning.ROOM_PACE_CORRELATION);
        this.pick = new KRandom(rnd.nextLong());
        this.gaps = new KRandom(rnd.nextLong());
        PlacedRoom ent = plan.entrance();
        this.entrance = ent != null ? ent.index : !plan.rooms.isEmpty() ? plan.rooms.get(0).index : 0;
        PlacedRoom f = plan.fairy();
        this.fairy = f != null ? f.index : null;
        PlacedRoom b = plan.blood();
        this.bloodRoom = b != null ? b.index : null;
        this.taskSeed = pick.nextLong();
    }

    public ClearPlan build() {
        int now = st.now;
        ArrayList<Seat> active = new ArrayList<>();
        for (Seat s : inp.seats) if (!s.isHuman || st.humanSeatActive) active.add(s);
        ArrayList<Seat> bots = new ArrayList<>();
        for (Seat s : inp.seats) if (!s.isHuman) bots.add(s);
        LinkedHashMap<Integer, Builder> builders = new LinkedHashMap<>();
        for (Seat s : active) {
            Integer freeAt = st.seatFreeAt.get(s.index);
            Pt pos = st.seatPos.get(s.index);
            if (pos == null) pos = inp.startAt.get(s.index);
            if (pos == null) pos = geo.spread(geo.centre(entrance), s.index, inp.seats.size());
            builders.put(s.index, new Builder(s, Math.max(now, freeAt != null ? freeAt : now), pos, entrance));
        }

        // ---- doors and keys
        DoorSchedule ms = scheduleDoors(now);
        PlanMilestones milestones = ms.milestones;

        // ---- carried-over commitments
        LinkedHashMap<Integer, RoomPlan> rooms = new LinkedHashMap<>();
        for (Commit c : st.commits) {
            Builder b = builders.get(c.seat);
            if (b == null) continue;
            commitRoom(b, c, rooms);
        }

        // ---- door crew and blood assignment as duties
        LinkedHashMap<Integer, DoorPlan> doorPlans = new LinkedHashMap<>();
        ArrayList<Seat> crewBots = new ArrayList<>();
        for (Seat s : bots) if (builders.get(s.index) != null) crewBots.add(s);
        assignDoorDuties(ms, crewBots, builders, doorPlans, rooms);
        Integer bloodSeat = null;
        Integer idx = inp.assignment.seat;
        if (idx != null) {
            Seat s = null;
            for (Seat it : inp.seats) if (it.index == idx) { s = it; break; }
            Builder b = builders.get(idx);
            if (s != null && !s.isHuman && b != null && bloodRoom != null) {
                bloodSeat = idx;
                PlacedDoor doorToBlood = null;
                for (PlacedDoor d : plan.doors) if (d.kind == DoorKind.BLOOD) { doorToBlood = d; break; }
                int byT = Math.max(now, milestones.bloodOpenT - (doorToBlood != null ? geoTicks(b, geo.doorPt(doorToBlood.index), b.room) : 0));
                b.duties.add(new Duty(byT, bb -> campDuty(bb, milestones, inp.assignment.arriveAtT)));
            }
        }

        // ---- the rooms, by list scheduling
        scheduleRooms(builders, rooms, ms);

        // ---- idle seats gather at the room before the blood room
        int gatherRoom = plan.path.size() >= 2 ? plan.path.get(plan.path.size() - 2) : entrance;
        int i = 0;
        for (Builder b : builders.values()) {
            int index = i++;
            if (b.seat.isHuman) continue;
            Pt dest = geo.spread(geo.centre(gatherRoom), index, builders.size());
            if (b.room != gatherRoom) walk(b, geo.route(b.pos, b.room, gatherRoom, dest), tables.moveBpt(b.seat.dungeonClass) * inp.speedScale, null);
            b.room = gatherRoom;
        }

        ArrayList<SeatTrack> walked = new ArrayList<>();
        for (Builder b : builders.values()) {
            if (b.seat.isHuman) continue;
            ArrayList<Act> acts = new ArrayList<>(b.acts);
            acts.sort(Comparator.comparingInt(Act::t));
            walked.add(new SeatTrack(b.seat.index, b.legs, acts));
        }
        // ---- teammates leap to the door opener
        Integer bloodDoor = null;
        for (PlacedDoor d : plan.doors) if (d.kind == DoorKind.BLOOD) { bloodDoor = d.index; break; }
        ArrayList<DoorPlan> futureDoors = new ArrayList<>();
        for (DoorPlan d : doorPlans.values()) if (d.openT >= now) futureDoors.add(d);
        LinkedHashSet<Integer> humans = new LinkedHashSet<>();
        for (Seat s : inp.seats) if (s.isHuman) humans.add(s.index);
        Pair<List<SeatTrack>, List<Leap>> lp = Leaps.add(walked, futureDoors, bloodDoor, humans, leapWorld, new KRandom(rnd.nextLong()), inp.people);
        // ---- and, once their rooms are done, into the blood camp
        Pair<List<SeatTrack>, List<Leap>> cp = CampLeaps.add(lp.first, bloodSeat, milestones.bloodOpenT, milestones.watcherDoneT, now, humans, plan.seed);
        ArrayList<Leap> allLeaps = new ArrayList<>(lp.second);
        allLeaps.addAll(cp.second);
        allLeaps.sort(Comparator.comparingInt(l -> l.t));
        return new ClearPlan(inp.seats, cp.first, rooms, doorPlans, copyMs(milestones, bloodSeat), now, inp.assignment, allLeaps);
    }

    /** The plan's geometry for the leap pass. */
    private final LeapWorld leapWorld = new LeapWorld() {
        @Override
        public boolean through(Pt from, Pt to, int door) {
            PlacedRoom a = geo.roomAt(from);
            if (a == null) return false;
            PlacedRoom b = geo.roomAt(to);
            if (b == null) return false;
            return a.index != b.index && geo.doorsOnRoute(a.index, b.index).contains(door);
        }

        @Override
        public List<Leg> walk(Pt from, Pt to, int t0) {
            Integer a = null;
            for (int i = 0; i <= 8; i++) {
                PlacedRoom r = geo.roomAt(from.lerp(to, Math.min(1.0, i / Math.max(1.0, from.dist(to)))));
                if (r != null) { a = r.index; break; }
            }
            if (a == null) return List.of();
            PlacedRoom br = geo.roomAt(to);
            int b = br != null ? br.index : a;
            ArrayList<Leg> legs = new ArrayList<>();
            walkEnd(expand(geo.route(from, a, b, to)), t0, BotTuning.DEFAULT_PLAN_BPT * inp.speedScale, legs);
            return legs;
        }
    };

    // ---- doors, keys and the blood milestones

    private static final class DoorSchedule {
        final PlanMilestones milestones;
        /** Paid doors in path order with the key room that feeds each and the planned key and open ticks. */
        final List<PaidDoor> paid;
        final Integer bloodDoor;
        final Integer bloodKeyRoom;
        /** The blood door has not opened yet. */
        final boolean bloodPending;
        /** Whether another bot than the opener is to kill the blood-key room's last mob. */
        final boolean bloodSplit;

        DoorSchedule(PlanMilestones milestones, List<PaidDoor> paid, Integer bloodDoor, Integer bloodKeyRoom, boolean bloodPending, boolean bloodSplit) {
            this.milestones = milestones;
            this.paid = paid;
            this.bloodDoor = bloodDoor;
            this.bloodKeyRoom = bloodKeyRoom;
            this.bloodPending = bloodPending;
            this.bloodSplit = bloodSplit;
        }
    }

    /** [splitWanted]: the draw says the bot that takes the key is not the one that opens the door. */
    private static final class PaidDoor {
        final int door;
        final Integer keyRoom;
        final int keyT;
        final int openT;
        final boolean done;
        final boolean splitWanted;

        PaidDoor(int door, Integer keyRoom, int keyT, int openT, boolean done, boolean splitWanted) {
            this.door = door;
            this.keyRoom = keyRoom;
            this.keyT = keyT;
            this.openT = openT;
            this.done = done;
            this.splitWanted = splitWanted;
        }
    }

    private static PlanMilestones copyMs(PlanMilestones m, Integer bloodSeat) {
        return new PlanMilestones(m.bloodKeyT, m.bloodOpenT, m.watcherDoneT, m.maxorT, bloodSeat);
    }

    private int passOrFar(int d) {
        Integer p = pass.get(d);
        return p != null ? p : FAR;
    }

    private int[] reachNow() { return geo.reachTimes(entrance, this::passOrFar); }

    private static int roundToInt(double v) { return (int) Math.round(v); }

    private DoorSchedule scheduleDoors(int now) {
        for (PlacedDoor d : plan.doors) {
            switch (d.kind) {
                case NORMAL -> pass.put(d.index, 0);
                case ENTRANCE -> pass.put(d.index, BotTuning.DOOR_PASSABLE_AFTER + 2);
                case FAIRY -> pass.put(d.index, BotTuning.DOOR_PASSABLE_AFTER + 1);
                default -> {}
            }
        }
        for (Map.Entry<Integer, Integer> e : st.passableAt.entrySet()) pass.put(e.getKey(), e.getValue());

        List<Integer> pathRooms = plan.path;
        int n = pathRooms.size();
        ArrayList<PlacedDoor> onPath = new ArrayList<>();
        for (PlacedDoor d : plan.pathDoors()) if (d.kind == DoorKind.WITHER) onPath.add(d);
        ArrayList<PlacedDoor> paidDoors = new ArrayList<>(onPath);
        for (PlacedDoor d : plan.doors) {
            if (d.kind != DoorKind.WITHER) continue;
            boolean seen = false;
            for (PlacedDoor o : onPath) if (o.index == d.index) { seen = true; break; }
            if (!seen) paidDoors.add(d);
        }
        ArrayList<Integer> keyRooms = new ArrayList<>();
        if (n >= 4) for (int k = 1; k <= n - 3; k++) {
            Integer r = pathRooms.get(k);
            if (!Objects.equals(r, fairy)) keyRooms.add(r);
        }

        int botCount = 0;
        for (Seat s : inp.seats) if (!s.isHuman) botCount++;
        ArrayList<PaidDoor> paid = new ArrayList<>();
        int prevOpen = now;
        Integer prevOpenT = null;
        Integer prevDoor = null;
        int keysLeft = st.witherKeys;
        for (int j = 0; j < paidDoors.size(); j++) {
            PlacedDoor door = paidDoors.get(j);
            Integer keyRoom = j < keyRooms.size() ? keyRooms.get(j) : null;
            Integer already = pass.get(door.index);
            if (already != null) {
                int openedAt = Math.max(already - BotTuning.DOOR_PASSABLE_AFTER, 0);
                paid.add(new PaidDoor(door.index, keyRoom, now, openedAt, true, false));
                prevOpen = Math.max(prevOpen, already);
                prevOpenT = openedAt;
                continue;
            }
            boolean splitWanted = botCount >= 2 && pick.nextDouble() < BotTuning.KEY_SPLIT_CHANCE;
            double u = pace.u();
            int keyUse = Math.max(roundToInt(tables.keyToUse().sample(roomPace.u())), 1);
            int keyUseMin = !splitWanted && keyRoom != null ? walkTicks(geo.centre(keyRoom), keyRoom, door.index) + BotTuning.OPEN_SETTLE : 1;
            keyUse = Math.max(keyUse, keyUseMin);
            int[] reach = reachNow();
            int keyReach = now;
            if (keyRoom != null && reach[keyRoom] < FAR) keyReach = reach[keyRoom];
            PlacedDoor entranceDoor = null;
            for (PlacedDoor d : plan.doors) if (d.kind == DoorKind.ENTRANCE) { entranceDoor = d; break; }
            Pt exit = entranceDoor != null ? geo.doorPt(entranceDoor.index) : geo.centre(entrance);
            int travel = keyRoom != null ? ticksAtMax(geo.length(geo.route(exit, entrance, keyRoom))) : 0;
            Pt doorSpot = geo.doorPt(door.index);
            int openerWalk;
            if (prevDoor != null) {
                int pdoor = prevDoor;
                openerWalk = prevOpen + BotTuning.DOOR_PASSABLE_AFTER + ticksAtMax(geo.length(expand(geo.route(geo.doorPt(pdoor), nearRoom(pdoor), nearRoom(door.index), doorSpot))));
            } else {
                int r = reach[nearRoom(door.index)];
                openerWalk = (r < FAR ? r : now) + ticksAtMax(geo.length(expand(geo.route(exit, entrance, nearRoom(door.index), doorSpot))));
            }
            int lo = Math.max(Math.max(now + 1, prevOpen + BotTuning.MIN_DOOR_GAP),
                    Math.max(keyReach + BotTuning.MIN_CLEAR_TICKS + keyUseMin + (j == 0 ? travel : 0), openerWalk + BotTuning.OPEN_SETTLE));
            int openT;
            int keyT;
            if (keysLeft > 0) {
                keysLeft--;
                openT = Math.max(lo, now + keyUse);
                keyT = now;
            } else {
                double drawn = prevOpenT != null ? prevOpenT + tables.doorGap(j + 1).sample(gaps.nextDouble())
                        : (j == 0 ? tables.firstDoor() : tables.doorOpen(j + 1)).sample(u);
                openT = Math.max(roundToInt(drawn), lo);
                keyT = Math.max(openT - keyUse, keyReach + BotTuning.MIN_CLEAR_TICKS + (j == 0 ? travel : 0));
            }
            pass.put(door.index, openT + BotTuning.DOOR_PASSABLE_AFTER);
            prevOpen = openT;
            prevDoor = door.index;
            prevOpenT = openT;
            paid.add(new PaidDoor(door.index, keyRoom, keyT, openT, false, splitWanted));
        }

        // The blood key (the last path room before the blood room) and the blood door.
        PlacedDoor bloodDoor = null;
        for (PlacedDoor d : plan.doors) if (d.kind == DoorKind.BLOOD) { bloodDoor = d; break; }
        Integer bloodKeyRoom = n >= 2 ? pathRooms.get(n - 2) : null;
        int[] reach = reachNow();
        int lastPaid = now;
        if (!paid.isEmpty()) {
            lastPaid = paid.get(0).openT;
            for (PaidDoor p : paid) lastPaid = Math.max(lastPaid, p.openT);
        }
        int bloodReach = 0;
        if (bloodKeyRoom != null && reach[bloodKeyRoom] < FAR) bloodReach = reach[bloodKeyRoom];
        int keyFloor = Math.max(Math.max(now + 1, lastPaid + BotTuning.OPENER_LEAD + 4), bloodReach + BotTuning.MIN_CLEAR_TICKS);
        double keyDraw = !paid.isEmpty() ? lastPaid + tables.bloodKeyAfterLastDoor().sample(gaps.nextDouble()) : tables.bloodKey().sample(pace.u());
        int bloodKeyT = Math.max(roundToInt(keyDraw), keyFloor);
        Integer bloodAlready = bloodDoor != null ? pass.get(bloodDoor.index) : null;
        boolean bloodSplit = botCount >= 2 && pick.nextDouble() < BotTuning.KEY_SPLIT_CHANCE;
        int doorAfterKey = Math.max(roundToInt(tables.bloodDoorAfterKey().sample(roomPace.u())), 1);
        if (!bloodSplit && bloodDoor != null && bloodKeyRoom != null) doorAfterKey = Math.max(doorAfterKey, walkTicks(geo.centre(bloodKeyRoom), bloodKeyRoom, bloodDoor.index) + BotTuning.OPEN_SETTLE);
        int bloodOpenT = bloodAlready != null ? Math.max(bloodAlready - BotTuning.DOOR_PASSABLE_AFTER, 0) : bloodKeyT + doorAfterKey;
        if (bloodDoor != null && bloodAlready == null) pass.put(bloodDoor.index, bloodOpenT + BotTuning.DOOR_PASSABLE_AFTER);
        int watcherDone = bloodOpenT + roundToInt(tables.campTicks().sample(roomPace.u()));
        int maxor = watcherDone + roundToInt(tables.provenToMaxor().sample(roomPace.u()));
        for (PaidDoor p : paid) if (!p.done && p.keyRoom != null) keyWhite.put(p.keyRoom, p.keyT);
        if (bloodAlready == null && bloodKeyRoom != null) keyWhite.put(bloodKeyRoom, bloodKeyT);
        return new DoorSchedule(new PlanMilestones(bloodKeyT, bloodOpenT, watcherDone, maxor, null), paid, bloodDoor != null ? bloodDoor.index : null, bloodKeyRoom,
                bloodDoor != null && bloodAlready == null, bloodSplit);
    }

    private static int ticksAtMax(double len) { return (int) Math.ceil(len / BotTuning.MAX_TRAVEL_BPT); }

    /** The room of a door's two sides that is nearer the path's start: the side its opener stands on. */
    private int nearRoom(int door) {
        PlacedDoor d = plan.doors.get(door);
        int ia = plan.path.indexOf(d.roomA);
        if (ia < 0) ia = Integer.MAX_VALUE;
        int ib = plan.path.indexOf(d.roomB);
        if (ib < 0) ib = Integer.MAX_VALUE;
        return ia <= ib ? d.roomA : d.roomB;
    }

    /** Ticks to run from [from] (in [fromRoom]) to a door's gap at the planning maximum speed. */
    private int walkTicks(Pt from, int fromRoom, int door) {
        return ticksAtMax(geo.length(expand(geo.route(from, fromRoom, nearRoom(door), geo.doorPt(door)))));
    }

    /** Where an opener stands when the door falls. */
    private Pt standPoint(int door, int near) {
        Pt spot = geo.doorPt(door);
        Pt centre = geo.centre(near);
        double len = spot.dist(centre);
        if (len < 1e-6) return spot;
        double d = PlanMath.curve(BotTuning.OPENER_STAND_BLOCKS, pick.nextDouble());
        return spot.lerp(centre, Math.min(d / len, 1.0));
    }

    private Seat drawOpener(List<Seat> bots, Integer excl) {
        ArrayList<Seat> cand = new ArrayList<>();
        for (Seat it : bots) if (excl == null || it.index != excl) cand.add(it);
        double[] w = new double[cand.size()];
        double sum = 0.0;
        for (int i = 0; i < w.length; i++) { w[i] = tables.doorOpenerShare(cand.get(i).dungeonClass) + 0.02; sum += w[i]; }
        double x = pick.nextDouble() * sum;
        for (int i = 0; i < cand.size(); i++) { x -= w[i]; if (x <= 0) return cand.get(i); }
        return cand.get(cand.size() - 1);
    }

    /** Door crew: one bot opens every door or two share; the key room's last mob is often another bot's. */
    private void assignDoorDuties(DoorSchedule ms, List<Seat> bots, Map<Integer, Builder> builders, Map<Integer, DoorPlan> doorPlans, Map<Integer, RoomPlan> rooms) {
        ArrayList<PaidDoor> open = new ArrayList<>();
        for (PaidDoor p : ms.paid) if (!p.done) open.add(p);
        boolean bloodPending = ms.bloodPending;
        if (bots.isEmpty() || (open.isEmpty() && !bloodPending)) return;
        Seat hinted = null;
        if (st.openerHint != null) for (Seat it : bots) if (it.index == st.openerHint) { hinted = it; break; }
        Seat first = hinted != null ? hinted : drawOpener(bots, null);
        int total = open.size() + (bloodPending ? 1 : 0);
        Seat second = hinted == null && bots.size() >= 2 && total >= 2 && pick.nextDouble() < BotTuning.SECOND_OPENER_CHANCE ? drawOpener(bots, first.index) : null;
        int handoff = second != null ? pick.nextInt(1, total) : total;
        List<PaidDoor> crewDoors = open;
        final Seat firstF = first;
        final Seat secondF = second;
        Function<Integer, Seat> seatOf = ix -> ix < handoff ? firstF : secondF != null ? secondF : firstF;
        LinkedHashMap<Integer, List<Pair<PaidDoor, Integer>>> bySeat = new LinkedHashMap<>();
        for (int i = 0; i < crewDoors.size(); i++) bySeat.computeIfAbsent(seatOf.apply(i).index, k -> new ArrayList<>()).add(Pair.of(crewDoors.get(i), i));
        if (bloodPending) bySeat.computeIfAbsent(seatOf.apply(crewDoors.size()).index, k -> new ArrayList<>()).add(Pair.of(null, crewDoors.size()));

        // The key rooms another bot kills: door index to the killer seat.
        HashMap<Integer, Integer> killerOf = new HashMap<>();
        HashSet<Integer> crewSeats = new HashSet<>();
        crewSeats.add(first.index);
        if (second != null) crewSeats.add(second.index);
        ArrayList<Seat> killers = new ArrayList<>();
        for (Seat it : bots) if (!crewSeats.contains(it.index) && builders.get(it.index) != null) killers.add(it);
        if (!killers.isEmpty()) {
            HashMap<Integer, Integer> busyUntil = new HashMap<>();
            HashMap<Integer, Integer> roomAt = new HashMap<>();
            HashMap<Integer, Integer> lastStart = new HashMap<>();
            ArrayList<Triple<Integer, Integer, Integer>> wanted = new ArrayList<>();
            for (PaidDoor it : crewDoors) if (it.splitWanted && it.keyRoom != null) wanted.add(Triple.of(it.door, it.keyRoom, it.keyT));
            if (bloodPending && ms.bloodSplit && ms.bloodDoor != null && ms.bloodKeyRoom != null) wanted.add(Triple.of(ms.bloodDoor, ms.bloodKeyRoom, ms.milestones.bloodKeyT));
            ArrayList<Triple<Integer, Integer, Integer>> sortedWanted = new ArrayList<>(wanted);
            sortedWanted.sort(Comparator.comparingInt(tr -> tr.third));
            for (Triple<Integer, Integer, Integer> w : sortedWanted) {
                int door = w.first, keyRoom = w.second, keyT = w.third;
                if (!keyRoomOk(keyRoom)) continue;
                ArrayList<Triple<Seat, Integer, Double>> tries = new ArrayList<>();
                for (Seat k : killers) {
                    Builder b = builders.get(k.index);
                    Integer ra = roomAt.get(k.index);
                    Pt from = ra != null ? geo.centre(ra) : b.pos;
                    int fromRoom = ra != null ? ra : b.room;
                    double bpt = tables.moveBpt(k.dungeonClass) * inp.speedScale;
                    List<RoutePoint> route = geo.route(from, fromRoom, keyRoom, geo.centre(keyRoom));
                    Integer bu = busyUntil.get(k.index);
                    int t0 = Math.max(b.t, bu != null ? bu : 0);
                    int arrive = route.size() < 2 ? t0 : walkEnd(expand(route), t0, Math.max(bpt, BotTuning.MAX_TRAVEL_BPT), null);
                    tries.add(Triple.of(k, arrive, pick.nextDouble()));
                }
                Triple<Seat, Integer, Double> best = null;
                for (Triple<Seat, Integer, Double> tr : tries) {
                    if (best == null || tr.second < best.second || (tr.second.equals(best.second) && tr.third < best.third)) best = tr;
                }
                if (best == null) continue;
                if (best.second > keyT - KEY_CLEAR_WINDOW + SPLIT_SLACK) continue;
                Seat killer = best.first;
                killerOf.put(door, killer.index);
                busyUntil.put(killer.index, keyT);
                roomAt.put(killer.index, keyRoom);
                Builder b = builders.get(killer.index);
                PlacedRoom pr = null;
                for (PlacedRoom it : plan.rooms) if (it.index == keyRoom) { pr = it; break; }
                if (pr == null) throw new java.util.NoSuchElementException("no room " + keyRoom);
                Dist toWhite = tables.roomToWhite(pr);
                int whiteEst = (int) ((toWhite != null ? toWhite.median() : tables.roomDur(pr, killer.dungeonClass).median() / 5.0) * inp.workScale);
                Integer ls = lastStart.get(killer.index);
                int startBy = Math.max(Math.max(b.t, keyT - whiteEst - (best.second - b.t) - 20), (ls != null ? ls : Integer.MIN_VALUE) + 1);
                lastStart.put(killer.index, startBy);
                killerRooms.add(keyRoom);
                final PlacedRoom prF = pr;
                final int keyTF = keyT;
                b.duties.add(new Duty(startBy, bb -> keyVisit(bb, prF, keyTF, rooms)));
            }
        }

        Map<Integer, Integer> split = killerOf;
        for (Map.Entry<Integer, List<Pair<PaidDoor, Integer>>> e : bySeat.entrySet()) {
            Builder b = builders.get(e.getKey());
            if (b == null) continue;
            List<Pair<PaidDoor, Integer>> list = e.getValue();
            PaidDoor firstDoor = list.get(0).first;
            Integer firstDoorIdx = firstDoor != null ? (Integer) firstDoor.door : ms.bloodDoor;
            int firstOpenT = firstDoor != null ? firstDoor.openT : ms.milestones.bloodOpenT;
            int firstKeyT = firstDoor != null ? firstDoor.keyT : ms.milestones.bloodKeyT;
            Integer fr = firstDoor != null ? firstDoor.keyRoom : null;
            if (fr == null) fr = ms.bloodKeyRoom;
            int firstRoom = fr != null ? fr : entrance;
            int startBy;
            if (list.get(0).second == 0) startBy = b.t;
            else if (firstDoorIdx != null && split.containsKey(firstDoorIdx)) startBy = Math.max(b.t, firstOpenT - geoTicks(b, geo.doorPt(firstDoorIdx), nearRoom(firstDoorIdx)) - 20);
            else startBy = Math.max(b.t, firstKeyT - geoTicks(b, geo.centre(firstRoom), b.room) - 20);
            ArrayList<PaidDoor> doors = new ArrayList<>();
            for (Pair<PaidDoor, Integer> p : list) doors.add(p.first);
            b.duties.add(new Duty(startBy, bb -> crewDuty(bb, doors, split, ms, doorPlans, rooms)));
        }
    }

    /** A room that is already done, that the human has taken, or whose "mobs" are a puzzle stays with the opener's own key kill. */
    private boolean keyRoomOk(int room) {
        if (st.white.contains(room) || st.green.contains(room) || st.humanRooms.contains(room)) return false;
        for (PlacedRoom it : plan.rooms) if (it.index == room) return it.type != RoomType.PUZZLE;
        return true;
    }

    /** Time for a seat at its position to walk to [to] at its own speed, round the rooms' contents and held at closed doors, or 0. */
    private int geoTicks(Builder b, Pt to, int toRoom) {
        List<RoutePoint> route = geo.route(b.pos, b.room, toRoom, to);
        if (route.size() < 2) return 0;
        return walkEnd(expand(route), b.t, Math.max(tables.moveBpt(b.seat.dungeonClass) * inp.speedScale, 0.1), null) - b.t;
    }

    /** A bot's visit to a key room: a normal room visit, timed so the last mob dies when the key is due. */
    private void keyVisit(Builder b, PlacedRoom room, int keyT, Map<Integer, RoomPlan> rooms) {
        int[] reach = geo.reachTimes(entrance, this::passOrFar);
        int r = room.index >= 0 && room.index < reach.length ? reach[room.index] : FAR;
        int reachT = r < FAR ? r : 0;
        assign(b, new Task(room, RoomWork.FULL, reachT), rooms, keyT);
    }

    /** To the key room, in time to kill its last mob when the key is due, and wait there for it. */
    private void keyKill(Builder b, int keyRoom, int keyT, double bpt, Map<Integer, RoomPlan> rooms) {
        if (b.room != keyRoom || b.pos.dist(geo.centre(keyRoom)) > 4.0)
            walk(b, geo.route(b.pos, b.room, keyRoom, geo.spread(geo.centre(keyRoom), b.seat.index, 5)), bpt, keyT - 2);
        b.room = keyRoom;
        int from = Math.max(b.t, keyT - KEY_CLEAR_WINDOW);
        b.stand(from);
        b.acts.add(new ClearMobs(b.seat.index, from, Math.max(from, keyT), keyRoom));
        b.stand(Math.max(b.t, keyT));
        recordKeyRoom(rooms, keyRoom, b.seat, from, keyT);
    }

    /** The doors one seat opens, in order. A null door is the blood door. */
    private void crewDuty(Builder b, List<PaidDoor> doors, Map<Integer, Integer> split, DoorSchedule ms, Map<Integer, DoorPlan> doorPlans, Map<Integer, RoomPlan> rooms) {
        double bpt = tables.moveBpt(b.seat.dungeonClass) * inp.speedScale;
        for (PaidDoor pd : doors) {
            Integer keyRoom = pd != null ? pd.keyRoom : ms.bloodKeyRoom;
            int keyT = pd != null ? pd.keyT : ms.milestones.bloodKeyT;
            Integer doorIdx = pd != null ? (Integer) pd.door : ms.bloodDoor;
            int openT = pd != null ? pd.openT : ms.milestones.bloodOpenT;
            if (keyRoom != null && (doorIdx == null || !split.containsKey(doorIdx))) keyKill(b, keyRoom, keyT, bpt, rooms);
            if (doorIdx != null) {
                int near = nearRoom(doorIdx);
                Pt stand = standPoint(doorIdx, near);
                if (b.pos.dist(stand) > 1.0) walk(b, geo.route(b.pos, b.room, near, stand), bpt, openT - BotTuning.OPENER_LEAD);
                b.room = near;
                int openAt = Math.max(openT, b.t + BotTuning.OPEN_SETTLE);
                if (openAt > openT) pass.put(doorIdx, openAt + BotTuning.DOOR_PASSABLE_AFTER);
                b.stand(Math.max(b.t, openAt));
                b.acts.add(new OpenDoor(b.seat.index, openAt, doorIdx, geo.doorPt(doorIdx)));
                Integer ks = split.get(doorIdx);
                doorPlans.put(doorIdx, new DoorPlan(doorIdx, b.seat.index, keyT, openAt, ks != null ? ks : b.seat.index));
                b.stand(Math.max(b.t, openAt + BotTuning.DOOR_PASSABLE_AFTER));
            }
        }
    }

    private void recordKeyRoom(Map<Integer, RoomPlan> rooms, int room, Seat seat, int from, int whiteT) {
        keyWhite.put(room, whiteT);
        PlacedRoom r = null;
        for (PlacedRoom it : plan.rooms) if (it.index == room) { r = it; break; }
        if (r == null) throw new java.util.NoSuchElementException("no room " + room);
        rooms.putIfAbsent(room, new RoomPlan(room, seat.index, r.secretsMax > 0 ? RoomWork.WHITE_ONLY : RoomWork.FULL, from, whiteT, r.secretsMax > 0 ? null : whiteT, false));
    }

    /** Rooms whose mobs the door crew kills for a key, with the tick the last one dies. */
    private final HashMap<Integer, Integer> keyWhite = new HashMap<>();

    /** The seat the strategy sends to the blood room: it arrives and stays till the camp's end. */
    private void campDuty(Builder b, PlanMilestones ms, Integer arriveBy) {
        if (bloodRoom == null) return;
        int room = bloodRoom;
        double bpt = tables.moveBpt(b.seat.dungeonClass) * inp.speedScale;
        Pt dest = geo.spread(geo.centre(room), b.seat.index, 5);
        walk(b, geo.route(b.pos, b.room, room, dest), bpt, arriveBy);
        b.room = room;
        b.stand(Math.max(b.t, ms.watcherDoneT));
    }

    /** A room the seat was already in when the plan was rebuilt. */
    private void commitRoom(Builder b, Commit c, Map<Integer, RoomPlan> rooms) {
        int now = st.now;
        boolean soft = b.seat.isHuman;
        if (c.work == RoomWork.FULL || c.work == RoomWork.WHITE_ONLY) {
            if (c.whiteT != null && c.whiteT > now) b.acts.add(new ClearMobs(c.seat, now, c.whiteT, c.room));
        }
        if (c.work != RoomWork.WHITE_ONLY && c.work != RoomWork.PUZZLE && c.greenT > now) b.acts.add(new FindSecrets(c.seat, Math.max(now, c.whiteT != null ? c.whiteT : now), c.greenT, c.room));
        if (c.work == RoomWork.PUZZLE && c.greenT > now) b.acts.add(new SolvePuzzle(c.seat, now, c.greenT, c.room));
        b.stand(Math.max(b.t, c.greenT));
        b.room = c.room;
        rooms.put(c.room, new RoomPlan(c.room, c.seat, c.work, c.arriveT, c.whiteT, c.greenT, soft));
    }

    // ---- the rooms

    private static final class Task {
        final PlacedRoom room;
        final RoomWork work;
        final int avail;

        Task(PlacedRoom room, RoomWork work, int avail) {
            this.room = room;
            this.work = work;
            this.avail = avail;
        }
    }

    /** The room list being worked through, the random it uses and the clear's expected end. */
    private final ArrayList<Task> restTasks = new ArrayList<>();
    private KRandom roomRand = new KRandom(0L);
    private int roomDeadline = Integer.MAX_VALUE;
    /** Key rooms another bot visits as a normal room: not in the room list. */
    private final HashSet<Integer> killerRooms = new HashSet<>();

    private void scheduleRooms(Map<Integer, Builder> builders, Map<Integer, RoomPlan> rooms, DoorSchedule ms) {
        int[] reach = geo.reachTimes(entrance, this::passOrFar);
        ArrayList<Task> pool = new ArrayList<>();
        for (PlacedRoom r : plan.rooms) {
            if (r.type == RoomType.ENTRANCE || r.type == RoomType.FAIRY || r.type == RoomType.BLOOD) continue;
            if (st.green.contains(r.index) || st.humanRooms.contains(r.index) || rooms.containsKey(r.index) || killerRooms.contains(r.index)) continue;
            int reachT = r.index >= 0 && r.index < reach.length ? reach[r.index] : FAR;
            if (reachT >= FAR) continue;
            Integer kw = keyWhite.get(r.index);
            RoomWork work;
            if (st.white.contains(r.index) || kw != null) work = r.secretsMax > 0 ? RoomWork.GREEN_ONLY : null;
            else if (r.type == RoomType.PUZZLE) work = RoomWork.PUZZLE;
            else work = RoomWork.FULL;
            if (work == null) continue;
            pool.add(new Task(r, work, Math.max(reachT, kw != null ? kw : 0)));
        }
        ArrayList<Task> rest = restTasks;
        rest.clear();
        ArrayList<Task> sortedPool = new ArrayList<>(pool);
        sortedPool.sort(Comparator.comparingInt(t -> t.room.index));
        rest.addAll(sortedPool);
        roomRand = new KRandom(taskSeed);
        roomDeadline = ms.milestones.maxorT;
        int guard = 0;
        while (!rest.isEmpty() && guard++ < 10_000) {
            Builder b = null;
            int bKey = 0;
            for (Builder it : builders.values()) {
                int k = it.t * 10 + it.seat.index;
                if (b == null || k < bKey) { b = it; bKey = k; }
            }
            if (b == null) break;
            Duty duty = b.duty();
            double bpt = tables.moveBpt(b.seat.dungeonClass) * inp.speedScale;
            Task best = null;
            double bestCost = Double.MAX_VALUE;
            boolean promptFresh = false;
            for (Task it : rest) if (it.work != RoomWork.GREEN_ONLY && it.avail <= b.t + PROMPT_SLACK + 40) { promptFresh = true; break; }
            for (Task t : rest) {
                if (promptFresh && t.work == RoomWork.GREEN_ONLY) continue;
                List<RoutePoint> route = geo.route(b.pos, b.room, t.room.index);
                int travel = (int) Math.ceil(geo.length(route) / Math.max(bpt, 0.1));
                int start = Math.max(b.t + travel, t.avail);
                double cost = start;
                if (t.work == RoomWork.PUZZLE) cost += (1.0 - tables.puzzleAffinity(b.seat.dungeonClass)) * BotTuning.PUZZLE_AFFINITY_PENALTY;
                if (duty != null) {
                    int est = start + Math.min((int) (tables.roomDur(t.room, b.seat.dungeonClass).median() * inp.workScale), 900);
                    if (est > duty.startBy - 10) cost += OVER_DUTY_PENALTY;
                }
                if (cost < bestCost - 1e-9) { bestCost = cost; best = t; }
            }
            if (duty != null && (best == null || bestCost >= OVER_DUTY_PENALTY || b.t >= duty.startBy - 10)) {
                b.duties.remove(duty);
                duty.run.accept(b);
                continue;
            }
            if (best == null) break;
            Task task = best;
            rest.remove(task);
            assign(b, task, rooms, null);
        }
        for (Builder b : builders.values()) while (true) {
            Duty d = b.duty();
            if (d == null) break;
            b.duties.remove(d);
            d.run.accept(b);
        }
    }

    /** Gives [b] the room of [task] as its next piece of work. With [pin] set the room's mobs die at that tick. */
    private void assign(Builder b, Task task, Map<Integer, RoomPlan> rooms, Integer pin) {
        KRandom rr = roomRand;
        ArrayList<Task> rest = restTasks;
        int deadline = roomDeadline;
        PlacedRoom room = task.room;
        String cls = b.seat.dungeonClass;
        double bpt = tables.moveBpt(cls) * inp.speedScale;
        Pt centre = geo.centre(room.index);
        boolean soft = b.seat.isHuman;
        double u = roomPace.u();
        double dur = tables.roomDur(room, cls).sample(u) * inp.workScale;
        int gFull = Math.max(roundToInt(dur), BotTuning.MIN_ROOM_DWELL);
        boolean hasSecrets = room.secretsMax > 0;
        int whiteOff;
        int greenOff;
        switch (task.work) {
            case GREEN_ONLY -> {
                Dist tg = tables.roomToGreen(room);
                double g = tg != null ? tg.sample(roomPace.u()) * inp.workScale : dur * 0.7;
                whiteOff = 0;
                greenOff = Math.max(roundToInt(g), BotTuning.MIN_GREEN_TASK);
            }
            case PUZZLE -> { whiteOff = gFull; greenOff = gFull; }
            default -> {
                int w;
                if (hasSecrets) {
                    Dist tw = tables.roomToWhite(room);
                    w = tw != null ? roundToInt(tw.sample(roomPace.u()) * inp.workScale) : gFull / 5;
                } else w = gFull;
                whiteOff = Math.clamp(w, BotTuning.MIN_CLEAR_TICKS, gFull);
                greenOff = hasSecrets ? Math.max(gFull, whiteOff + 1) : whiteOff;
            }
        }
        Integer target = pin != null ? pin - whiteOff : null;
        Integer walkBy = pin != null && target != null && target <= b.t ? (Integer) (pin - BotTuning.MIN_CLEAR_TICKS) : target;
        if (!soft) walk(b, geo.route(b.pos, b.room, room.index, centre), bpt, walkBy);
        else {
            double len = geo.length(geo.route(b.pos, b.room, room.index, centre));
            b.t += (int) Math.ceil(len / Math.max(bpt, 0.1));
            b.pos = centre;
        }
        b.room = room.index;
        int arrive = Math.max(Math.max(b.t, task.avail), target != null ? target : Integer.MIN_VALUE);
        if (!soft) b.stand(arrive); else b.t = arrive;
        if (pin != null && task.work != RoomWork.GREEN_ONLY) whiteOff = Math.clamp(pin - arrive, BotTuning.MIN_CLEAR_TICKS, Math.max(whiteOff, BotTuning.MIN_CLEAR_TICKS));
        RoomWork work = task.work;
        int greenOff2 = greenOff;
        boolean freshWaiting = false;
        for (Task it : rest) if (it.work != RoomWork.GREEN_ONLY && it.avail <= arrive + whiteOff + PROMPT_SLACK) { freshWaiting = true; break; }
        Duty bd = b.duty();
        boolean dutySoon = !soft && bd != null && arrive + greenOff > bd.startBy - 10;
        if (work == RoomWork.FULL && hasSecrets && whiteOff < greenOff && (arrive + greenOff > deadline || freshWaiting || dutySoon)) {
            work = RoomWork.WHITE_ONLY;
            greenOff2 = whiteOff;
            keyWhite.put(room.index, arrive + whiteOff);
            rest.add(new Task(room, RoomWork.GREEN_ONLY, arrive + whiteOff));
        }
        Integer whiteT;
        if (work == RoomWork.GREEN_ONLY) {
            Integer kw = keyWhite.get(room.index);
            whiteT = kw != null ? kw : st.white.contains(room.index) ? (Integer) st.now : null;
        } else whiteT = arrive + whiteOff;
        int greenT = arrive + greenOff2;
        if (!soft) {
            List<SecretSpot> spots = work == RoomWork.WHITE_ONLY ? List.of() : RoomRoutes.secretSpots(room, room.secretsMax);
            Pair<List<Leg>, Pt> walked = work == RoomWork.WHITE_ONLY || inp.traversals == null ? null : inp.traversals.legs(room, b.pos, arrive, greenT, bpt, rr);
            if (walked == null) {
                ArrayList<Pt> pts = new ArrayList<>();
                for (SecretSpot s : spots) pts.add(s.pt());
                WalkArea area = inp.areas != null ? inp.areas.of(room) : null;
                if (area == null) area = FreeArea.of(room);
                walked = RoomRoutes.legs(arrive, greenT, b.pos, pts, centre, bpt, rr, area);
            }
            b.legs.addAll(walked.first);
            b.pos = walked.second;
            switch (work) {
                case FULL -> {
                    b.acts.add(new ClearMobs(b.seat.index, arrive, arrive + whiteOff, room.index));
                    if (hasSecrets) b.acts.add(new FindSecrets(b.seat.index, arrive + whiteOff, greenT, room.index));
                }
                case GREEN_ONLY -> b.acts.add(new FindSecrets(b.seat.index, arrive, greenT, room.index));
                case PUZZLE -> b.acts.add(new SolvePuzzle(b.seat.index, arrive, greenT, room.index));
                case WHITE_ONLY -> b.acts.add(new ClearMobs(b.seat.index, arrive, arrive + whiteOff, room.index));
            }
        } else b.pos = centre;
        b.t = greenT;
        rooms.put(room.index, new RoomPlan(room.index, b.seat.index, work, arrive, whiteT, work == RoomWork.WHITE_ONLY ? null : greenT, soft));
    }

    // ---- walking

    /** Inside a room the walk goes round what is in the room, not through it. */
    private List<RoutePoint> expand(List<RoutePoint> route0) {
        RoomAreas areas = inp.areas;
        if (areas == null) return route0;
        return RouteExpander.expand(route0, p -> {
            PlacedRoom r = geo.roomAt(p);
            return r != null ? areas.of(r) : null;
        });
    }

    /** The tick a walk of [route] started at [t0] ends: legs at [speed], held at each closed door until it is passed. Legs go to [legs] when given. */
    private int walkEnd(List<RoutePoint> route, int t0, double speed, List<Leg> legs) {
        int t = t0;
        for (int i = 1; i < route.size(); i++) {
            Pt a = route.get(i - 1).pt;
            Pt c = route.get(i).pt;
            double len = a.dist(c);
            int ticks = len <= 0.0 ? 0 : (int) Math.ceil(len / speed);
            if (legs != null && (len > 0.0 || ticks > 0)) legs.add(new Leg(t, t + ticks, a, c, ticks > 0 && len / ticks > BotTuning.HOP_ABOVE_BPT));
            t += ticks;
            Integer gate = route.get(i).gate;
            if (gate != null) {
                int p = passOrFar(gate);
                if (p >= t + 1 && p < FAR) {
                    if (legs != null) legs.add(new Leg(t, p, c, c));
                    t = p;
                }
            }
        }
        return t;
    }

    /** Appends the legs of a walk along [route0] to [b], at [bpt], standing at each closed door; speeds up to make a [deadline]. */
    private void walk(Builder b, List<RoutePoint> route0, double bpt, Integer deadline) {
        if (route0.size() < 2) return;
        List<RoutePoint> route = expand(route0);
        double speed = Math.max(bpt, 0.1);
        if (deadline != null && deadline <= b.t) speed = Math.max(speed, BotTuning.MAX_TRAVEL_BPT);
        else if (deadline != null) {
            int natural = walkEnd(route, b.t, speed, null);
            if (natural > deadline) {
                double lo = speed;
                double hi = Math.max(speed, BotTuning.MAX_TRAVEL_BPT);
                if (walkEnd(route, b.t, hi, null) > deadline) speed = hi;
                else {
                    for (int k = 0; k < 12; k++) {
                        double mid = (lo + hi) / 2;
                        if (walkEnd(route, b.t, mid, null) <= deadline) hi = mid; else lo = mid;
                    }
                    speed = hi;
                }
            }
        }
        b.t = walkEnd(route, b.t, speed, b.legs);
        b.pos = route.get(route.size() - 1).pt;
    }

    /** A door that is never passed (not yet planned), as a tick. */
    public static final int FAR = Integer.MAX_VALUE / 2;

    /** How long before the key is due the crew bot starts killing in the key room. */
    public static final int KEY_CLEAR_WINDOW = 12;

    /** Added to the cost of a room that would make a seat late for its duty. */
    public static final double OVER_DUTY_PENALTY = 1.0e7;

    /** A room counts as startable "about now" when it opens within this many ticks of a seat getting there. */
    public static final int PROMPT_SLACK = 30;

    /** A bot may be chosen to kill a key room when it can get there this many ticks later than the kill window opens. */
    public static final int SPLIT_SLACK = 10;
}
