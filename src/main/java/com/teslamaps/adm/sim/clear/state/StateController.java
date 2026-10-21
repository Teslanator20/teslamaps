// Ported from ADM's p3sim/clear/state/StateController.kt
package com.teslamaps.adm.sim.clear.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.pack.runstate.ChestClear;
import com.teslamaps.adm.sim.run.BloodDoorOpened;
import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.DoorFell;
import com.teslamaps.adm.sim.run.DoorLocked;
import com.teslamaps.adm.sim.run.DoorOpened;
import com.teslamaps.adm.sim.run.DoorPhase;
import com.teslamaps.adm.sim.run.DoorRecord;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.KeyDropped;
import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.KeyPicked;
import com.teslamaps.adm.sim.run.MobKilled;
import com.teslamaps.adm.sim.run.ModuleEvent;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerLeft;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PuzzleFailed;
import com.teslamaps.adm.sim.run.PuzzleSolved;
import com.teslamaps.adm.sim.run.RoomEntered;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.RunState;
import com.teslamaps.adm.sim.run.SavepointRestored;
import com.teslamaps.adm.sim.run.SecretFound;
import com.teslamaps.adm.sim.run.StageChanged;
import com.teslamaps.adm.sim.run.WatcherDone;
import com.teslamaps.adm.sim.run.api.DoorOpenResult;
import com.teslamaps.adm.sim.run.api.KeyPlan;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class StateController {
    public final Run run;
    public final StateRules rules;
    public final StateConfig cfg;
    public final StateEffects fx;
    public final DungeonPlan plan;
    public final RunState state;
    public final KeyPlan keyPlan;
    public final KeyLedger ledger;
    private final DoorFalls falls = new DoorFalls();
    private final TickSchedule<StartStep> schedule = new TickSchedule<>();
    private final KRandom rng;

    public int lastT = 0;
    private final LinkedHashMap<Integer, KeyStand> stands = new LinkedHashMap<>();
    private int nextStandId = 1;
    private final LinkedHashMap<Integer, Integer> synth = new LinkedHashMap<>();
    private final LinkedHashMap<Integer, Integer> keyFallback = new LinkedHashMap<>();
    private final HashMap<Integer, Pair<UUID, Integer>> lastKill = new HashMap<>();
    private Integer bloodGreenAt = null;
    private Map<Integer, Triple<Integer, Integer, Integer>> clearChests;

    public StateController(Run run, StateRules rules, StateConfig cfg, StateEffects fx) {
        this.run = run;
        this.rules = rules;
        this.cfg = cfg;
        this.fx = fx;
        this.plan = run.plan;
        this.state = run.state;
        this.keyPlan = KeyPlans.of(plan);
        this.ledger = new KeyLedger(state.keys, keyPlan);
        this.rng = run.subRandom("doors");
    }

    private Map<Integer, Triple<Integer, Integer, Integer>> clearChests() {
        if (clearChests == null) {
            LinkedHashMap<Integer, Triple<Integer, Integer, Integer>> m = new LinkedHashMap<>();
            for (PlacedRoom r : plan.rooms) {
                ChestClear cc = rules.chestClear(r.name);
                Pos cell = cc == null ? null : cc.cell;
                if (cell == null) continue;
                if (r.placement == null) continue;
                WorldPos w = r.placement.toWorld(cell.x, cell.y, cell.z);
                if (w == null) continue;
                m.put(r.index, Triple.of(w.x, w.y, w.z));
            }
            clearChests = m;
        }
        return clearChests;
    }

    private static <T> T getOrNull(List<T> l, int i) { return i >= 0 && i < l.size() ? l.get(i) : null; }

    private static boolean eqIc(String a, String b) { return a == null ? b == null : a.equalsIgnoreCase(b); }

    // ---- start ----

    public void start() {
        if (plan.practice != null) { startPractice(); return; }
        int lead = rules.startLeadTicks;
        int delay = Math.max(cfg.countdownDelayTicks, 0);
        run.setT0(run.serverTick + delay + 60 + lead);
        lastT = run.t() - 1;
        schedule.clear();
        int s4 = -(60 + lead);
        for (int i = 0; i < 5; i++) schedule.add(Math.max(s4 - 12 + i, lastT + 1), new StartStep.Ready(i));
        for (int n = 4; n >= 1; n--) schedule.add(-(lead + 20 * (n - 1)), new StartStep.Starting(n));
        int entranceFall = -lead + rules.entranceFallAfterStart1;
        schedule.add(entranceFall + rules.freeDoorOffset, StartStep.FreeDoorFalls.INSTANCE);
        schedule.add(entranceFall, StartStep.EntranceDoorFalls.INSTANCE);
        schedule.add(0, new StartStep.Mort(0)); schedule.add(0, StartStep.EntranceDiscovered.INSTANCE);
        schedule.add(40, new StartStep.Mort(1)); schedule.add(70, new StartStep.Mort(2));
        schedule.add(rules.entranceNtoG, StartStep.EntranceGreen.INSTANCE);
        if (state.milestones.countdownStartTick == null) state.milestones.countdownStartTick = run.serverTick;
        refreshCounters();
    }

    private void startPractice() {
        run.setT0(run.serverTick);
        lastT = run.t() - 1;
        schedule.clear();
        schedule.add(0, StartStep.PracticeGo.INSTANCE);
        if (state.milestones.countdownStartTick == null) state.milestones.countdownStartTick = run.serverTick;
        refreshCounters();
    }

    // ---- tick ----

    public void tick() {
        if (run.stage == RunStage.COUNTDOWN && plan.practice == null && run.t() < 0 && ReadyUpMenu.holding()) {
            Integer t0 = run.t0Tick;
            if (t0 != null) run.setT0(t0 + 1);
        }
        int t = run.t();
        fx.tick();
        for (StartStep step : schedule.due(lastT, t)) runStep(step);
        for (FallStep s : falls.restDue(t, lastT)) applyStep(s);
        for (FallStep s : falls.due(t)) { applyStep(s); state.doors.get(s.door).phase = DoorPhase.OPEN; run.bus.post(new DoorFell(t, s.door)); }
        lastT = t;
        detectRooms();
        pollRooms(t);
        pollStands(t);
    }

    private void runStep(StartStep step) {
        if (step instanceof StartStep.Ready r) {
            PlayerRecord it = getOrNull(new ArrayList<>(state.players.values()), r.index);
            if (it != null) fx.chat(StateText.ready(it.name, nameColourOf(it.uuid)));
        } else if (step instanceof StartStep.Starting s) {
            fx.chat(StateText.startingIn(s.seconds)); fx.sound(StateText.COUNTDOWN_TICK);
        } else if (step instanceof StartStep.Mort m) {
            if (m.index == 0) classDoubledLines();
            fx.chat(StateText.mort(m.index)); fx.sound(StateText.MORT_LINE);
            if (m.index == 0) run.requestStage(RunStage.CLEAR);
            if (m.index == 1) MortNpc.blankNames(MortNpc.BLANK_AFTER_SECOND_LINE);
        } else if (step instanceof StartStep.EntranceDoorFalls) {
            List<PlacedDoor> ds = new ArrayList<>();
            for (PlacedDoor d : plan.doors) if (d.kind == DoorKind.ENTRANCE) ds.add(d);
            for (PlacedDoor d : ds) beginFall(d.index, null);
        } else if (step instanceof StartStep.FreeDoorFalls) {
            List<PlacedDoor> ds = new ArrayList<>();
            for (PlacedDoor d : plan.doors) if (d.kind == DoorKind.FAIRY) ds.add(d);
            for (PlacedDoor d : ds) beginFall(d.index, null);
        } else if (step instanceof StartStep.EntranceDiscovered) {
            PlacedRoom e = plan.entrance();
            if (e != null) discover(e.index);
        } else if (step instanceof StartStep.EntranceGreen) {
            PlacedRoom e = plan.entrance();
            if (e != null) mark(e.index, CheckMark.GREEN);
        } else if (step instanceof StartStep.PracticeGo) {
            run.requestStage(RunStage.CLEAR);
        }
    }

    private void classDoubledLines() {
        List<PlayerRecord> all = new ArrayList<>(state.players.values());
        for (PlayerRecord p : all) {
            if (p.isBot) continue;
            int same = 0;
            for (PlayerRecord o : all) if (eqIc(o.dungeonClass, p.dungeonClass)) same++;
            if (same != 1) continue;
            List<String> lines = StateText.classDoubled(p.dungeonClass);
            if (lines != null) for (String l : lines) fx.chat(l, p.uuid);
        }
    }

    // ---- doors ----

    public Integer doorAtBlock(int x, int y, int z) {
        PlacedDoor d = plan.doorContaining(x, y, z);
        return d == null ? null : d.index;
    }

    public DoorOpenResult tryOpen(int door, UUID by) {
        PlacedDoor d = getOrNull(plan.doors, door);
        if (d == null) return DoorOpenResult.NOT_A_DOOR;
        if (!StateEffectsKt.hasBody(d.kind)) return DoorOpenResult.NOT_A_DOOR;
        DoorRecord rec = state.doors.get(door);
        if (rec.phase != DoorPhase.CLOSED) return DoorOpenResult.ALREADY_OPEN;
        KeyKind needs;
        if (d.kind == DoorKind.WITHER) needs = KeyKind.WITHER;
        else if (d.kind == DoorKind.BLOOD) needs = KeyKind.BLOOD;
        else return DoorOpenResult.ALREADY_OPEN;
        if (!ledger.consume(needs)) { noKey(door, by); return DoorOpenResult.NO_KEY; }
        int t = run.t();
        beginFall(door, by);
        if (needs == KeyKind.WITHER) {
            fx.chat(StateText.witherOpened(nameOf(by), nameColourOf(by))); fx.sound(StateText.DOOR_OPENED);
        } else {
            fx.chat(StateText.BLOOD_OPENED); fx.chat(StateText.SHIVER); fx.sound(StateText.DOOR_OPENED); fx.sound(StateText.BLOOD_DOOR);
            state.milestones.bloodOpenT = t;
            run.bus.post(new BloodDoorOpened(t));
        }
        return DoorOpenResult.OPENED;
    }

    public boolean touch(int door, UUID by) {
        PlacedDoor d = getOrNull(plan.doors, door);
        if (d == null) return false;
        if (d.kind != DoorKind.WITHER && d.kind != DoorKind.BLOOD) return false;
        if (state.doors.get(door).phase != DoorPhase.CLOSED) return false;
        return tryOpen(door, by) != DoorOpenResult.OPENED;
    }

    private final int NO_KEY_REPEAT = 2;

    private final HashMap<UUID, Integer> noKeyTold = new HashMap<>();

    private void noKey(int door, UUID by) {
        int t = run.t();
        Integer prev = noKeyTold.put(by, t);
        if (prev != null && prev == t) return;
        for (int i = 0; i < NO_KEY_REPEAT; i++) { fx.chat(StateText.NO_KEY_LINE, by); fx.sound(StateText.NO_KEY, by); }
        if (by != null) run.bus.post(new DoorLocked(t, door, by));
    }

    public void beginFall(int door, UUID by) {
        PlacedDoor d = getOrNull(plan.doors, door);
        if (d == null) return;
        DoorRecord rec = state.doors.get(door);
        if (rec.phase != DoorPhase.CLOSED || !StateEffectsKt.hasBody(d.kind)) return;
        int t = run.t();
        rec.phase = DoorPhase.FALLING; rec.openedAt = t; rec.openedBy = by;
        int delay = d.kind == DoorKind.WITHER || d.kind == DoorKind.BLOOD ? rules.keyDoorBarrierDelay : 0;
        for (FallStep s : falls.begin(door, t, rules.barrierFor(d.kind), rules.firstBlockToRestTicks, delay)) applyStep(s);
        fx.spawnDoorDebris(d);
        if (d.kind != DoorKind.FAIRY) { discover(d.roomA); discover(d.roomB); }
        run.bus.post(new DoorOpened(t, door, d.kind, by));
    }

    private void applyStep(FallStep s) {
        PlacedDoor d = getOrNull(plan.doors, s.door);
        if (d != null) fx.setBody(d, s.blocks, s.onlyFirst);
    }

    private String nameOf(UUID id) {
        if (id != null) {
            PlayerRecord p = state.players.get(id);
            if (p != null && p.name != null) return p.name;
        }
        return "Someone";
    }

    private String nameColourOf(UUID id) {
        Object v = null;
        if (id != null) {
            PlayerRecord p = state.players.get(id);
            if (p != null) v = p.extras.get("nameColour");
        }
        return v instanceof String s ? s : "§b";
    }

    private String rankBracketOf(UUID id) {
        Object v = null;
        if (id != null) {
            PlayerRecord p = state.players.get(id);
            if (p != null) v = p.extras.get("rankBracket");
        }
        return v instanceof String s ? s : StateText.KEY_RANK_PREFIX;
    }

    // ---- keys ----

    public KeyKind keyForRoom(int room) { return ledger.pending(room); }

    public void dropKey(int room, double x, double z) {
        KeyKind kind = ledger.pending(room);
        if (kind == null) return;
        ledger.markDropped(room);
        keyFallback.remove(room);
        int t = run.t();
        int id = nextStandId++;
        double y = fx.spawnStand(id, kind, x, z, rules.standYDefault);
        UUID killer = null;
        if (!keysFromRecording) {
            Pair<UUID, Integer> lk = lastKill.get(room);
            if (lk != null && t - lk.second <= 2) killer = lk.first;
        }
        KeyStand stand = new KeyStand(id, kind, room, x, y, z, t, killer);
        stands.put(id, stand);
        run.bus.post(new KeyDropped(t, kind, room, x, y, z));
        if (killer != null && rng.nextDouble() < rules.pickupInstantFrac && canPick(killer)) pickUp(stand, killer);
        else if (killer != null && isBot(killer)) {
            int wait = Math.max((int) Math.round(rules.pickupDelayed.sample(rng.nextDouble())), 1);
            botPickups.put(id, Pair.of(killer, t + wait));
        }
    }

    private boolean isBot(UUID id) {
        PlayerRecord p = state.players.get(id);
        return p != null && p.isBot;
    }

    private final HashMap<Integer, Pair<UUID, Integer>> botPickups = new HashMap<>();

    private boolean canPick(UUID id) {
        PlayerRecord p = state.players.get(id);
        return p != null && !p.ghost;
    }

    private KeyStand oldestOf(KeyKind kind) {
        KeyStand best = null;
        for (KeyStand s : stands.values()) if (s.kind == kind && (best == null || s.spawnT < best.spawnT)) best = s;
        return best;
    }

    public boolean pickUpKey(UUID by, KeyKind kind, Integer room, double x, double z) {
        KeyStand first = oldestOf(kind);
        if (first != null) { pickUp(first, by); return true; }
        Integer r = room != null && ledger.pending(room) == kind ? room : null;
        if (r == null) {
            for (PlacedRoom pr : run.plan.rooms) if (ledger.pending(pr.index) == kind) { r = pr.index; break; }
        }
        if (r == null) return false;
        dropKey(r, x, z);
        KeyStand again = oldestOf(kind);
        if (again != null) { pickUp(again, by); return true; }
        return false;
    }

    private void pickUp(KeyStand stand, UUID by) {
        if (stands.remove(stand.id) == null) return;
        fx.removeStand(stand.id);
        ledger.pick(stand.kind);
        int t = run.t();
        if (stand.kind == KeyKind.BLOOD) state.milestones.bloodKeyT = t;
        fx.chat(StateText.keyObtained(stand.kind, nameOf(by), rankBracketOf(by)));
        fx.chat(StateText.rightClick(stand.kind));
        fx.sound(StateText.KEY_OBTAINED); fx.sound(StateText.KEY_SHUTTER);
        run.bus.post(new KeyPicked(t, stand.kind, by));
    }

    private void pollStands(int t) {
        if (stands.isEmpty()) { botPickups.clear(); return; }
        if (keysFromRecording) return;
        for (Map.Entry<Integer, Pair<UUID, Integer>> e : new ArrayList<>(botPickups.entrySet())) {
            int id = e.getKey();
            Pair<UUID, Integer> due = e.getValue();
            if (t >= due.second) {
                botPickups.remove(id);
                KeyStand stand = stands.get(id);
                if (stand == null) continue;
                if (canPick(due.first)) pickUp(stand, due.first);
            }
        }
        for (KeyStand stand : new ArrayList<>(stands.values())) {
            List<Pair<PlayerRecord, Double>> near = new ArrayList<>();
            for (PlayerRecord p : state.players.values()) if (!p.ghost) near.add(Pair.of(p, dist(p, stand)));
            PlayerRecord taker = null;
            Pair<PlayerRecord, Double> best = null;
            for (Pair<PlayerRecord, Double> n : near) if (n.second <= cfg.pickupRadius && (best == null || n.second < best.second)) best = n;
            if (best != null) taker = best.first;
            else if (t - stand.spawnT >= rules.autoCollectTicks) {
                Pair<PlayerRecord, Double> any = null;
                for (Pair<PlayerRecord, Double> n : near) if (any == null || n.second < any.second) any = n;
                if (any != null) taker = any.first;
            }
            if (taker != null) pickUp(stand, taker.uuid);
        }
    }

    private double dist(PlayerRecord p, KeyStand s) {
        double dx = p.x - s.x; double dz = p.z - s.z; double dy = p.y - s.y;
        return Math.abs(dy) > cfg.pickupRadius + 1.5 ? Double.MAX_VALUE : Math.sqrt(dx * dx + dz * dz);
    }

    // ---- rooms ----

    private void mark(int room, CheckMark to) {
        RoomRecord r = getOrNull(state.rooms, room);
        if (r == null) return;
        CheckMark before = r.check;
        if (before == to) return;
        state.setCheck(room, to);
        refreshCounters();
        if (before == CheckMark.UNDISCOVERED && to == CheckMark.NONE) scheduleSynth(room);
    }

    public void discover(int room) {
        RoomRecord r = getOrNull(state.rooms, room);
        if (r == null) return;
        if (r.check != CheckMark.UNDISCOVERED) return;
        mark(room, RoomChecks.onDiscovered(r.plan.type));
    }

    private void refreshCounters() {
        Pair<Integer, Integer> c = RoomChecks.counters(state.rooms);
        state.counters.opened = c.first; state.counters.completed = c.second;
    }

    public void objectiveDone(int room) {
        RoomRecord r = getOrNull(state.rooms, room);
        if (r == null) return;
        if (r.plan.type == RoomType.ENTRANCE || r.plan.type == RoomType.FAIRY || r.plan.type == RoomType.BLOOD) return;
        if (r.check == CheckMark.WHITE || r.check == CheckMark.GREEN) return;
        synth.remove(room);
        mark(room, RoomChecks.onObjectiveDone(r.secretsMax(), r.secretsFound));
        if (ledger.pending(room) != null) {
            if (r.mobsRequired == 0) fallbackKey(room); else keyFallback.putIfAbsent(room, run.t() + cfg.keyFallbackTicks);
        }
    }

    private void fallbackKey(int room) {
        Pair<Double, Double> c = centre(room);
        dropKey(room, c.first, c.second);
        KeyStand stand = null;
        for (KeyStand s : stands.values()) if (s.room == room) { stand = s; break; }
        UUID who = null;
        RoomRecord rr = getOrNull(state.rooms, room);
        if (rr != null) for (UUID u : rr.occupants) if (canPick(u)) { who = u; break; }
        if (stand != null && who != null) pickUp(stand, who);
    }

    private void scheduleSynth(int room) {
        if (!cfg.synthesiseClears) return;
        RoomRecord r = state.rooms.get(room);
        if (!MOB_TYPES.contains(r.plan.type)) return;
        if (clearChests().containsKey(room)) return;
        int delay = rules.whiteDelay(r.plan.name, r.plan.type.name(), rng.nextDouble());
        synth.put(room, run.t() + delay);
    }

    private void pollRooms(int t) {
        for (RoomRecord r : state.rooms) {
            if (!MOB_TYPES.contains(r.plan.type)) continue;
            switch (r.check) {
                case NONE -> { if (r.mobsRequired > 0 && r.mobsRequiredAlive <= 0) objectiveDone(r.index()); }
                case WHITE -> { if (RoomChecks.secretsComplete(r.secretsMax(), r.secretsFound)) mark(r.index(), CheckMark.GREEN); }
                default -> {}
            }
        }
        for (Map.Entry<Integer, Integer> e : new ArrayList<>(synth.entrySet())) {
            int room = e.getKey();
            int at = e.getValue();
            if (t >= at) {
                synth.remove(room);
                RoomRecord r = state.rooms.get(room);
                if (r.check == CheckMark.NONE && (r.mobsRequired == 0 || clearChests().containsKey(room))) objectiveDone(room);
            }
        }
        Integer bg = bloodGreenAt;
        if (bg != null && t >= bg) greenBlood();
        for (Map.Entry<Integer, Integer> e : new ArrayList<>(keyFallback.entrySet())) {
            int room = e.getKey();
            int at = e.getValue();
            if (t >= at) {
                keyFallback.remove(room);
                if (ledger.pending(room) != null) fallbackKey(room);
            }
        }
    }

    public Pair<Double, Double> centre(int room) {
        List<Tile> tiles = plan.rooms.get(room).tiles;
        double sx = 0, sz = 0;
        for (Tile tl : tiles) sx += DungeonFrame.tileCentre(tl.x) + 0.5;
        for (Tile tl : tiles) sz += DungeonFrame.tileCentre(tl.z) + 0.5;
        double x = tiles.isEmpty() ? Double.NaN : sx / tiles.size();
        double z = tiles.isEmpty() ? Double.NaN : sz / tiles.size();
        return Pair.of(x, z);
    }

    private void detectRooms() {
        for (PlayerRecord p : state.players.values()) {
            boolean inBox = DungeonFrame.inDungeonBox((int) Math.floor(p.x), (int) Math.floor(p.z));
            Integer here = null;
            if (inBox && !p.ghost) {
                RoomRecord at = state.roomAt(p.x, p.z);
                if (at != null) here = at.index();
            }
            if (here == null) {
                if (!inBox || p.ghost) leaveRoom(p);
                continue;
            }
            if (Objects.equals(p.roomIndex, here)) continue;
            leaveRoom(p);
            p.roomIndex = here;
            RoomRecord r = state.rooms.get(here);
            r.occupants.add(p.uuid); r.visitors.add(p.uuid);
            discover(here);
            run.bus.post(new RoomEntered(run.t(), here, p.uuid));
        }
    }

    private void leaveRoom(PlayerRecord p) {
        if (p.roomIndex != null) {
            RoomRecord r = getOrNull(state.rooms, p.roomIndex);
            if (r != null) r.occupants.remove(p.uuid);
        }
        p.roomIndex = null;
    }

    // ---- events ----

    public void onEvent(RunEvent e) {
        if (e instanceof MobKilled k) {
            Integer room = k.mob.room;
            if (room == null) return;
            if (k.mob.required && k.by != null) lastKill.put(room, Pair.of(k.by, k.t()));
            RoomRecord r = getOrNull(state.rooms, room);
            if (r == null) return;
            if (k.mob.required && r.check == CheckMark.NONE && r.mobsRequiredAlive <= 0) objectiveDone(room);
        } else if (e instanceof SecretFound s) {
            RoomRecord r = getOrNull(state.rooms, s.room);
            if (r != null) {
                if (r.check == CheckMark.WHITE && RoomChecks.secretsComplete(r.secretsMax(), r.secretsFound)) mark(r.index(), CheckMark.GREEN);
                chestOpened(r, s);
            }
        } else if (e instanceof PuzzleSolved ps) {
            RoomRecord r = getOrNull(state.rooms, ps.room);
            if (r != null && r.check != CheckMark.GREEN) {
                if (r.check == CheckMark.UNDISCOVERED) discover(r.index());
                forceObjective(r.index());
            }
        } else if (e instanceof PuzzleFailed pf) {
            RoomRecord r = getOrNull(state.rooms, pf.room);
            if (r != null && r.check != CheckMark.GREEN) {
                if (r.check == CheckMark.UNDISCOVERED) discover(r.index());
                mark(r.index(), CheckMark.RED);
            }
        } else if (e instanceof StageChanged sc) {
            if (sc.to == RunStage.BLOOD) {
                PlacedRoom b = plan.blood();
                if (b != null) { bloodGreenAt = null; discover(b.index); mark(b.index, CheckMark.RED); }
            } else if (sc.to == RunStage.BOSS_ENTRY || sc.to == RunStage.BOSS) {
                if (bloodGreenAt != null) greenBlood();
            }
        } else if (e instanceof WatcherDone w) {
            scheduleBloodGreen(w.t() + rules.bloodGreenAfterPass);
        } else if (e instanceof ModuleEvent m) {
            if ("bots".equals(m.source)) botNotice(m);
            else if ("blood".equals(m.source) && "portal".equals(m.name)) scheduleBloodGreen(m.t() + rules.bloodGreenAfterPortal);
        } else if (e instanceof PlayerLeft pl) {
            PlayerRecord p = state.players.get(pl.player);
            if (p != null) leaveRoom(p);
        } else if (e instanceof SavepointRestored) {
            refreshCounters();
        }
    }

    private void scheduleBloodGreen(int at) {
        PlacedRoom b = plan.blood();
        if (b == null) return;
        if (state.rooms.get(b.index).check == CheckMark.GREEN) return;
        bloodGreenAt = Math.min(bloodGreenAt != null ? bloodGreenAt : Integer.MAX_VALUE, at);
    }

    private void greenBlood() {
        bloodGreenAt = null;
        PlacedRoom b = plan.blood();
        if (b != null) mark(b.index, CheckMark.GREEN);
    }

    private void chestOpened(RoomRecord r, SecretFound e) {
        Triple<Integer, Integer, Integer> cell = clearChests().get(r.index());
        if (cell == null) return;
        if (e.x != cell.first || e.y != cell.second || e.z != cell.third) return;
        if (r.check != CheckMark.NONE || synth.containsKey(r.index())) return;
        int delay = rules.chestOpenDelay(r.plan.name, rng.nextDouble());
        if (delay <= 0) objectiveDone(r.index()); else synth.put(r.index(), run.t() + delay);
    }

    private void botNotice(ModuleEvent e) {
        Object rv = e.data.get("room");
        if (!(rv instanceof Number num)) return;
        int room = num.intValue();
        RoomRecord r = getOrNull(state.rooms, room);
        if (r == null) return;
        switch (e.name) {
            case "clear.white" -> { discover(room); objectiveDone(room); }
            case "clear.green", "clear.puzzle" -> {
                if (r.plan.type == RoomType.ENTRANCE || r.plan.type == RoomType.FAIRY || r.plan.type == RoomType.BLOOD) return;
                discover(room);
                if (r.check == CheckMark.NONE) objectiveDone(room);
                if (r.secretsFound < r.secretsMax()) {
                    UUID who = null;
                    if (e.data.get("player") instanceof String ps) {
                        try { who = UUID.fromString(ps); } catch (Throwable ignored) {}
                    }
                    Services.secrets().creditRoom(run, room, who);
                }
                if (r.check != CheckMark.GREEN && RoomChecks.onObjectiveDone(r.secretsMax(), r.secretsFound) == CheckMark.GREEN) mark(room, CheckMark.GREEN);
            }
            default -> {}
        }
    }

    private void forceObjective(int room) {
        RoomRecord r = state.rooms.get(room);
        if (r.check == CheckMark.RED) {
            mark(room, RoomChecks.onObjectiveDone(r.secretsMax(), r.secretsFound));
        } else objectiveDone(room);
    }

    // ---- savepoints ----

    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("lastT", lastT);
        o.addProperty("nextStandId", nextStandId);
        JsonArray dropped = new JsonArray();
        for (Integer d : ledger.droppedRooms()) dropped.add(d);
        o.add("dropped", dropped);
        JsonArray sa = new JsonArray();
        for (KeyStand s : stands.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", s.id); j.addProperty("kind", s.kind.name()); j.addProperty("room", s.room);
            j.addProperty("x", s.x); j.addProperty("y", s.y); j.addProperty("z", s.z); j.addProperty("t", s.spawnT);
            if (s.killer != null) j.addProperty("killer", s.killer.toString());
            sa.add(j);
        }
        o.add("stands", sa);
        JsonArray syn = new JsonArray();
        for (Map.Entry<Integer, Integer> en : synth.entrySet()) {
            JsonObject j = new JsonObject(); j.addProperty("room", en.getKey()); j.addProperty("at", en.getValue()); syn.add(j);
        }
        o.add("synth", syn);
        JsonArray kf = new JsonArray();
        for (Map.Entry<Integer, Integer> en : keyFallback.entrySet()) {
            JsonObject j = new JsonObject(); j.addProperty("room", en.getKey()); j.addProperty("at", en.getValue()); kf.add(j);
        }
        o.add("keyFallback", kf);
        if (bloodGreenAt != null) o.addProperty("bloodGreenAt", bloodGreenAt);
        return o;
    }

    public void restore(JsonObject o) {
        JsonElement lt = o.get("lastT");
        lastT = lt != null ? lt.getAsInt() : run.t();
        JsonElement ns = o.get("nextStandId");
        nextStandId = ns != null ? ns.getAsInt() : 1;
        List<Integer> dropped = new ArrayList<>();
        for (JsonElement el : orEmpty(o.getAsJsonArray("dropped"))) dropped.add(el.getAsInt());
        ledger.restoreDropped(dropped);
        falls.clear(); synth.clear(); keyFallback.clear(); lastKill.clear();
        JsonElement bg = o.get("bloodGreenAt");
        bloodGreenAt = bg != null ? bg.getAsInt() : null;
        fx.reset();
        for (JsonElement j : orEmpty(o.getAsJsonArray("synth"))) {
            JsonObject it = j.getAsJsonObject();
            synth.put(it.get("room").getAsInt(), it.get("at").getAsInt());
        }
        for (JsonElement j : orEmpty(o.getAsJsonArray("keyFallback"))) {
            JsonObject it = j.getAsJsonObject();
            keyFallback.put(it.get("room").getAsInt(), it.get("at").getAsInt());
        }
        for (PlacedDoor d : plan.doors) {
            DoorRecord rec = state.doors.get(d.index);
            if (!StateEffectsKt.hasBody(d.kind)) continue;
            switch (rec.phase) {
                case OPEN -> fx.setBody(d, BodyBlocks.AIR);
                case FALLING -> {
                    fx.setBody(d, BodyBlocks.BARRIER);
                    falls.resume(d.index, rec.openedAt != null ? rec.openedAt : run.t(), rules.barrierFor(d.kind));
                }
                case CLOSED -> {}
            }
        }
        fx.clearStands(); stands.clear(); botPickups.clear();
        for (JsonElement j : orEmpty(o.getAsJsonArray("stands"))) {
            JsonObject s = j.getAsJsonObject();
            KeyKind kind = KeyKind.valueOf(s.get("kind").getAsString());
            int id = s.get("id").getAsInt();
            double y = fx.spawnStand(id, kind, s.get("x").getAsDouble(), s.get("z").getAsDouble(), s.get("y").getAsDouble());
            JsonElement k = s.get("killer");
            String ks = k != null ? k.getAsString() : null;
            stands.put(id, new KeyStand(id, kind, s.get("room").getAsInt(), s.get("x").getAsDouble(), y, s.get("z").getAsDouble(), s.get("t").getAsInt(),
                    ks != null ? UUID.fromString(ks) : null));
        }
        refreshCounters();
    }

    public void end() { fx.clearStands(); stands.clear(); botPickups.clear(); }

    public List<KeyStand> standList() { return new ArrayList<>(stands.values()); }

    public static volatile boolean keysFromRecording = false;

    private static final Set<RoomType> MOB_TYPES = EnumSet.of(RoomType.NORMAL, RoomType.TRAP, RoomType.YELLOW, RoomType.RARE);

    private static List<JsonElement> orEmpty(JsonArray a) {
        if (a == null) return Collections.emptyList();
        List<JsonElement> out = new ArrayList<>(a.size());
        for (JsonElement e : a) out.add(e);
        return out;
    }
}
