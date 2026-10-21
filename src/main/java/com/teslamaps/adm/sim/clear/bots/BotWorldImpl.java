// Ported from ADM's p3sim/clear/bots/BotWorldImpl.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.DoorPhase;
import com.teslamaps.adm.sim.run.DoorRecord;
import com.teslamaps.adm.sim.run.ModuleEvent;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunState;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.run.plugin.Seat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** The bots' hands: BotWorld over the run's services. Server thread only. */
public class BotWorldImpl implements BotWorld {
    private final Run run;
    private final Roster roster;
    private final BotRooms rooms;
    private final BotFx fx;

    public BotWorldImpl(Run run, Roster roster, BotRooms rooms, BotFx fx) {
        this.run = run;
        this.roster = roster;
        this.rooms = rooms;
        this.fx = fx;
    }

    public BotWorldImpl(Run run, Roster roster, BotRooms rooms) { this(run, roster, rooms, null); }

    private RunState state() { return run.state; }

    private UUID uuid(int seat) { return roster.uuids.get(seat); }

    private final HashSet<String> announced = new HashSet<>();
    private final HashMap<Integer, Integer> creditedSpots = new HashMap<>();
    private final HashMap<Integer, Integer> stuckSince = new HashMap<>();

    /** A teammate that is a ghost does nothing until it is back. */
    private boolean dead(int seat) {
        UUID id = uuid(seat);
        if (id == null) return false;
        PlayerRecord p = state().players.get(id);
        return p != null && p.ghost;
    }

    @Override
    public boolean openDoor(int seat, int door) {
        if (dead(seat)) return false;
        DoorRecord rec = state().door(door);
        if (rec == null) return true;
        if (rec.phase != DoorPhase.CLOSED) return true;
        switch (rec.plan.kind) {
            case WITHER -> { if (state().keys.witherHeld <= 0) return false; }
            case BLOOD -> { if (!state().keys.bloodHeld) return false; }
            default -> {}
        }
        return switch (Services.doors().tryOpen(run, door, uuid(seat))) {
            case OPENED, ALREADY_OPEN -> true;
            case NO_KEY -> false;
            case NOT_A_DOOR -> true;
        };
    }

    @Override
    public boolean killRequired(int seat, int room, double fraction) {
        if (dead(seat)) return false;
        RoomRecord rr = state().room(room);
        if (rr == null) return true;
        if (rr.check == CheckMark.WHITE || rr.check == CheckMark.GREEN) return true;
        UUID by = uuid(seat);
        ArrayList<MobRecord> required = new ArrayList<>();
        for (MobRecord m : Services.mobs().inRoom(room)) if (m.required) required.add(m);
        int total = Math.max(rr.mobsRequired, required.size());
        UUID myId = uuid(seat);
        PlayerRecord me = myId != null ? state().players.get(myId) : null;
        ArrayList<MobRecord> alive = new ArrayList<>();
        for (MobRecord m : required) if (m.alive) alive.add(m);
        alive.sort(Comparator.comparingDouble(m -> d2(me, m)));
        int want = (int) Math.ceil(Math.clamp(fraction, 0.0, 1.0) * total);
        int killed = total - alive.size();
        for (MobRecord m : alive) {
            if (killed >= want) break;
            if (fraction < 1.0 && d2(me, m) > KILL_REACH * KILL_REACH && killed >= want - KILL_LAG) break;
            Services.damage().hurtMob(run, m.id, Math.max(Math.max(m.hp, m.maxHp), 1.0) * 2.0, DamageSource.MELEE, by);
            if (fx != null) fx.killed(seat, m.x, m.y, m.z);
            killed++;
        }
        int left = 0;
        for (MobRecord m : Services.mobs().inRoom(room)) if (m.required && m.alive) left++;
        if (fraction >= 1.0 && left == 0) {
            RoomRecord now = state().room(room);
            if (now != null && now.check != CheckMark.WHITE && now.check != CheckMark.GREEN) tell("white", room, seat);
        }
        return left == 0;
    }

    private static double d2(PlayerRecord me, MobRecord m) {
        if (me == null) return 0.0;
        return (me.x - m.x) * (me.x - m.x) + (me.z - m.z) * (me.z - m.z);
    }

    @Override
    public boolean findSecrets(int seat, int room, double fraction) {
        if (dead(seat)) return false;
        RoomRecord rr = state().room(room);
        if (rr == null) return true;
        int max = rr.secretsMax();
        if (max <= 0 || rr.secretsFound >= max) return true;
        int want = (int) Math.ceil(Math.clamp(fraction, 0.0, 1.0) * max);
        if (rr.secretsFound >= want) return false;
        UUID by = uuid(seat);
        List<SecretSpot> spots = rooms.secretSpots(room);
        Integer ci = creditedSpots.get(room);
        int i = ci != null ? ci : 0;
        while (rr.secretsFound < want && i < spots.size()) {
            SecretSpot s = spots.get(i++);
            Services.secrets().found(run, room, SecretKinds.of(s.secret.type), by, s.x, s.y, s.z);
        }
        creditedSpots.put(room, i);
        if (rr.secretsFound >= max) return true;
        if (fraction >= 1.0 && i >= spots.size()) {
            int since = stuckSince.computeIfAbsent(room, k -> run.t());
            if (run.t() - since >= STUCK_TICKS) tell("green", room, seat);
        }
        return rr.secretsFound >= max;
    }

    @Override
    public boolean solvePuzzle(int seat, int room) {
        if (dead(seat)) return false;
        RoomRecord rec = state().room(room);
        if (rec == null) return true;
        if (rec.check == CheckMark.GREEN) return true;
        Puzzle puzzle = Services.puzzles().puzzleOf(run, room);
        if (puzzle != null) {
            if (puzzle.status() == PuzzleStatus.UNSOLVED || puzzle.status() == PuzzleStatus.FAILED) Services.puzzles().reportSolved(puzzle, uuid(seat));
            return true;
        }
        tell("puzzle", room, seat);
        return true;
    }

    @Override
    public boolean roomDone(int room, boolean green) {
        RoomRecord r = state().room(room);
        if (r == null) return true;
        CheckMark c = r.check;
        if (c == null) return true;
        return green ? c == CheckMark.GREEN : c == CheckMark.WHITE || c == CheckMark.GREEN;
    }

    @Override
    public boolean humanInside(int room) {
        RoomRecord rr = state().room(room);
        if (rr == null) return false;
        ArrayList<UUID> humans = new ArrayList<>();
        for (Seat s : roster.humans()) {
            UUID id = uuid(s.index);
            if (id != null) humans.add(id);
        }
        for (UUID o : rr.occupants) if (humans.contains(o)) return true;
        return false;
    }

    /** Asks the doors module for a room mark the services could not produce, once per room and kind. */
    private void tell(String kind, int room, int seat) {
        if (!announced.add(kind + ":" + room)) return;
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("room", room);
        data.put("seat", seat);
        UUID id = uuid(seat);
        data.put("player", id != null ? id.toString() : null);
        run.bus.post(new ModuleEvent(run.t(), "bots", "clear." + kind, data));
    }

    /** Forgets progress (a savepoint restore takes the run back). */
    public void reset() { announced.clear(); creditedSpots.clear(); stuckSince.clear(); }

    /** Ticks the secrets service gets to finish a room by itself before the bot asks for the rest. */
    public static final int STUCK_TICKS = 100;
    /** Blocks from the bot within which its kills fall while the room still has time. */
    public static final double KILL_REACH = 6.0;
    /** Kills the count may run behind the plan's pace waiting for mobs to come within KILL_REACH. */
    public static final int KILL_LAG = 3;
}
