// Ported from ADM's p3sim/clear/bots/BotsModule.kt
package com.teslamaps.adm.sim.clear.bots;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.DungeonClass;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.P3Plan;
import com.teslamaps.adm.sim.Players;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.DoorFell;
import com.teslamaps.adm.sim.run.DoorOpened;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.KeyDropped;
import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.KeyPicked;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerDamaged;
import com.teslamaps.adm.sim.run.PlayerDied;
import com.teslamaps.adm.sim.run.PlayerJoined;
import com.teslamaps.adm.sim.run.PlayerLeft;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.PlayerRevived;
import com.teslamaps.adm.sim.run.RoomCleared;
import com.teslamaps.adm.sim.run.RoomEntered;
import com.teslamaps.adm.sim.run.RoomGreen;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.SavepointRestored;
import com.teslamaps.adm.sim.run.api.BotService;
import com.teslamaps.adm.sim.run.api.LeapTarget;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.run.plugin.BloodAssignment;
import com.teslamaps.adm.sim.run.plugin.PartyStrategy;
import com.teslamaps.adm.sim.run.plugin.Seat;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Teammate bots in the clear: a ClearPlan per layout, visible door-to-door movement, doors opened with keys, re-planning when the human takes a
 * room or opens a door, and the party-strategy hook. The ClearPlan is the truth; the body is only shown while a human is near.
 */
public final class BotsModule implements AdsModule, BotService {
    public static final BotsModule INSTANCE = new BotsModule();

    private BotsModule() {}

    @Override public String id() { return "bots"; }

    @Override public String owner() { return "bots"; }

    // ---- settings (created in registerSettings)

    private static NumSetting showDistanceS;
    private static NumSetting speedScaleS;
    private static BoolSetting coverS;
    private static NumSetting coverAfterS;
    private static NumSetting graceS;
    private static BoolSetting namesS;
    private static BoolSetting playersS;

    @Override
    public void registerSettings() {
        showDistanceS = SimSettings.num("Bots Show Distance", (int) BotTuning.BODY_RADIUS, 24, 120, 4, "", "How near you have to be for a teammate bot to be drawn. Bots farther away are still running the plan; you just do not see them (the world only keeps the rooms near you loaded).");
        speedScaleS = SimSettings.num("Bots Speed Scale", 1.0, 0.5, 2.0, 0.1, "", "How fast the bots travel between rooms compared with the measured pace of a real party (1.0). Faster bots get ahead of you sooner; their rooms still finish on the same schedule.");
        coverS = SimSettings.bool("Bots Cover Your Rooms", true, "A room the plan gave to you that you have not gone into after a while is cleared by a bot, as a teammate would. Off: your share waits for you.");
        coverAfterS = SimSettings.num("Bots Cover After", BotTuning.COVER_AFTER_TICKS, 100, 3000, 50, " ticks", "How long after the plan expected you in a room the bots start covering it (20 ticks a second).");
        namesS = SimSettings.bool("Bots Use Player Names", true, "Teammate bots in a full run have names, ranks and levels of their own, like a party on Hypixel. Off: they are named for their class (Healer, Mage...), as the boss practice names them.");
        playersS = SimSettings.bool("Bots Are Players", true, "Teammate bots reach your game as real player entities with a name over the head, as Hypixel's teammates do (Odin's class glow and teammate tracking need that). Off: they are mannequins, as in the first version. Takes effect when a bot is next drawn.");
        graceS = SimSettings.num("Bots Wait For You", BotTuning.HUMAN_ROOM_GRACE_TICKS, 0, 1200, 20, " ticks", "While you stand in a room a bot was going to clear, it holds back the last mob this long, in case you are about to kill it yourself.");
    }

    private static double showDistance() { return showDistanceS != null ? (double) showDistanceS.getInt() : BotTuning.BODY_RADIUS; }

    private static double speedScale() { return speedScaleS != null ? speedScaleS.get() : 1.0; }

    private static boolean coverOn() { return coverS != null ? coverS.get() : true; }

    private static int coverAfter() { return coverAfterS != null ? coverAfterS.getInt() : BotTuning.COVER_AFTER_TICKS; }

    private static boolean playerNames() { return namesS != null ? namesS.get() : true; }

    private static boolean playerBodies() { return playersS != null ? playersS.get() : true; }

    private static int grace() { return graceS != null ? graceS.getInt() : BotTuning.HUMAN_ROOM_GRACE_TICKS; }

    // ---- one run's bots

    /** Everything the module holds for the current run. */
    public static class Session {
        public final Run run;
        public final Roster roster;
        public final BotTables tables;
        public final BotRooms rooms;
        public final BotWorldImpl world;
        public final BotBodies bodies;
        public final TraversalLibrary traversals;
        /** The walkable floor of each room, from the rooms pack's blocks. */
        public final PackRoomAreas areas;

        public PlanExecutor executor;
        public boolean active = true;
        public boolean announcedJoin = false;
        public boolean covered = false;
        public Integer replanAt = null;
        public String replanReason = "";
        public int replans = 0;
        public int lastCoverCheck = 0;
        /** Room to the tick a human last went into it. */
        public final HashMap<Integer, Integer> humanTouched = new HashMap<>();
        public final HashMap<Integer, Pt> pos = new HashMap<>();
        public final HashMap<Integer, Double> y = new HashMap<>();
        public final HashMap<Integer, Float> yaw = new HashMap<>();
        /** The room each bot was last in. */
        public final HashMap<Integer, Integer> roomOf = new HashMap<>();
        /** A bot sent to a key stand. */
        public final HashMap<Integer, Excursion> excursion = new HashMap<>();
        /** Key stands nobody has taken yet. */
        public final HashMap<KeyKind, StandWatch> stands = new HashMap<>();
        public final ArrayList<String> kept = new ArrayList<>();
        public final StepClock steps;
        /** Leaps already posted ("seat:door"), and each bot's spelling of the line. */
        public final HashSet<String> leapsSaid = new HashSet<>();
        public final HashMap<Integer, Double> leapSpelling = new HashMap<>();
        public final KRandom leapRandom;
        /** Blood camp mobs to the tick the bot camping blood kills them. */
        public final HashMap<UUID, Integer> campDeath = new HashMap<>();
        public final KRandom campRandom;
        /** Wither doors the player opened that the next plan has not seen yet. */
        public final ArrayList<PersonDoor> personDoors = new ArrayList<>();
        /** The teammate deaths still to come. */
        public final ArrayList<TeamDeath> deaths = new ArrayList<>();
        /** When a mob lands a hit on a fighting teammate. */
        public final HitClock hits;
        /** Door to its crack animation, while it is shown. */
        public final HashMap<Integer, Crack> cracking = new HashMap<>();
        /** Where the party appeared and the height it appeared at; null when the human was not in the entrance room. */
        public Pt spawn = null;
        public double spawnY = BotTuning.DEFAULT_FLOOR_Y;
        /** Where each bot waits for the clock to start. */
        public final HashMap<Integer, Pt> gates = new HashMap<>();
        /** Each bot's walk from the spawn block to its gate. */
        public final HashMap<Integer, SeatTrack> countdown = new HashMap<>();
        public boolean countdownBuilt = false;
        /** Bots still dropping from the spawn height onto the floor. */
        public final HashMap<Integer, Fall> falls = new HashMap<>();
        /** The small life of the bodies. */
        public final BotMotion motion;
        /** Dice for the sounds of a kill. */
        public final KRandom cast;

        public Session(Run run, Roster roster, BotTables tables, BotRooms rooms, BotWorldImpl world, BotBodies bodies, TraversalLibrary traversals, PackRoomAreas areas) {
            this.run = run;
            this.roster = roster;
            this.tables = tables;
            this.rooms = rooms;
            this.world = world;
            this.bodies = bodies;
            this.traversals = traversals;
            this.areas = areas;
            this.steps = new StepClock(run.subRandom("bots.steps"));
            this.leapRandom = run.subRandom("bots.leaps");
            this.campRandom = run.subRandom("bots.camp");
            this.hits = new HitClock(run.subRandom("bots.hits"));
            this.motion = new BotMotion(run.subRandom("bots.motion"));
            this.cast = run.subRandom("bots.cast");
        }

        public Session(Run run, Roster roster, BotTables tables, BotRooms rooms, BotWorldImpl world, BotBodies bodies, TraversalLibrary traversals) {
            this(run, roster, tables, rooms, world, bodies, traversals, new PackRoomAreas(run.packs.rooms));
        }

        public ClearPlan plan() { return executor.plan; }
    }

    /** A key stand somebody has to take. */
    public static class StandWatch {
        public final Pt at;
        public final int droppedT;
        public boolean sentFallback;

        public StandWatch(Pt at, int droppedT, boolean sentFallback) {
            this.at = at;
            this.droppedT = droppedT;
            this.sentFallback = sentFallback;
        }

        public StandWatch(Pt at, int droppedT) { this(at, droppedT, false); }
    }

    /** A bot-opened door's block-break animation: the tick the door opened and the breaker id the packets carry. */
    public static class Crack {
        public final int openT;
        public final int breakerId;

        public Crack(int openT, int breakerId) {
            this.openT = openT;
            this.breakerId = breakerId;
        }
    }

    public static volatile Session session = null;

    /** A test driver's switch: the human only watches and bots fill every seat. Off in play. */
    public static volatile boolean devObserve = false;

    /** The party changed classes before the start: the bots are planned again from the start. Server thread. */
    public static void reclass(Run run) {
        Session old = session;
        if (old != null) old.bodies.removeAll();
        session = null;
        run.state.players.values().removeIf(p -> p.isBot);
        INSTANCE.onRunStart(run);
    }

    private static Session live(Run run) {
        Session s = session;
        return s != null && s.run == run && s.active ? s : null;
    }

    @Override
    public void onRunStart(Run run) {
        Session old = session;
        if (old != null) old.bodies.removeAll();
        session = null;
        if (!run.config.bots) { Ec.LOG.info("[ads] bots: none planned (Run Bots is off)"); return; }
        try {
            PackSet set = run.packs;
            BotTables tables = new BotTables(set.runstate);
            List<Triple<UUID, String, String>> humans = humansOf(run);
            Roster roster = BotRoster.build(humans, true, run.config.partySize, botOrder(), playerNames() ? run.subRandom("bots.identity") : null);
            if (roster.bots().isEmpty()) { Ec.LOG.info("[ads] bots: none planned (party size " + run.config.partySize + " leaves no seat for a bot)"); return; }
            for (Seat b : roster.bots()) {
                UUID uuid = roster.uuids.get(b.index);
                PlayerRecord rec = run.state.players.computeIfAbsent(uuid, k -> new PlayerRecord(uuid, roster.names.get(b.index), b.dungeonClass, true));
                BotIdentity identity = roster.identities.get(b.index);
                if (identity != null) identity.applyTo(rec);
            }
            TraversalLibrary trav;
            try { trav = TraversalLibrary.load(set.dir, set.report); } catch (Throwable t) { trav = null; }
            PartyStrategy strategy = BotStrategies.resolve(run.config.strategy);
            BotRooms rooms = new BotRooms(run.plan);
            Session s = new Session(run, roster, tables, rooms, new BotWorldImpl(run, roster, rooms, BotsModule::killFx), new BotBodies(BotsModule::playerBodies), trav);
            planCountdown(run, s);
            ClearPlan first = buildPlan(run, s, PlanState.initial(), strategy);
            s.executor = new PlanExecutor(first, s.world, grace());
            PlanGeometry geo = new PlanGeometry(run.plan);
            PlacedRoom ent = run.plan.entrance();
            Pt start = geo.centre(ent != null ? ent.index : 0);
            for (Seat b : roster.bots()) {
                SeatTrack tr = first.track(b.index);
                s.pos.put(b.index, tr != null ? tr.positionAt(0) : start);
                s.y.put(b.index, BotTuning.DEFAULT_FLOOR_Y);
                s.yaw.put(b.index, 0f);
                s.motion.start(b.index, s.bodies.restingItem(b.dungeonClass));
            }
            for (int t : TeamDeaths.schedule(run.subRandom("bots.deaths"), first.milestones.watcherDoneT)) s.deaths.add(new TeamDeath(t, run.subRandom("bots.death." + t)));
            KRandom hpRandom = run.subRandom("bots.maxhp");
            for (Seat b : roster.bots()) {
                PlayerRecord r = run.state.players.get(roster.uuids.get(b.index));
                if (r != null) r.extras.put("maxHp", BotHealth.maxHp(b.dungeonClass, hpRandom.nextDouble()));
            }
            session = s;
            StringBuilder classes = new StringBuilder();
            for (Seat b : roster.bots()) { if (classes.length() > 0) classes.append(", "); classes.append(b.dungeonClass); }
            Ec.LOG.info("[ads] bots: " + classes + " planned (party of " + run.config.partySize + "); " + s.deaths.size() + " teammate death(s) scheduled; blood open at t="
                    + first.milestones.bloodOpenT + ", " + first.doors.size() + " doors, " + first.rooms.size() + " rooms");
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] the bots could not be planned; the run goes on without them", t);
            session = null;
        }
    }

    /** The countdown: the bots appear on the block the human spawned on and walk to the entrance room's exit door. */
    private static void planCountdown(Run run, Session s) {
        try {
            PlacedRoom entrance = run.plan.entrance();
            if (entrance == null) return;
            List<ServerPlayer> players = Sim.players();
            if (players.isEmpty()) return;
            ServerPlayer me = players.get(0);
            Tile here = DungeonFrame.tileOf(me.getX(), me.getZ());
            if (here == null || !entrance.tiles.contains(here)) return;
            PlacedDoor door = null;
            for (PlacedDoor d : run.plan.doors) if (d.kind == DoorKind.ENTRANCE) { door = d; break; }
            if (door == null) return;
            PlanGeometry geo = new PlanGeometry(run.plan);
            Pt spawn = new Pt(me.getX(), me.getZ());
            KRandom rnd = run.subRandom("bots.start");
            s.spawn = spawn;
            s.spawnY = me.getY();
            for (Seat b : s.roster.bots()) s.gates.put(b.index, Countdown.gate(geo.doorPt(door.index), spawn, rnd));
        } catch (Throwable t) { Ec.LOG.info("[ads] bots: no countdown walk (" + t.getMessage() + ")"); }
    }

    /** The human seats: the run's non-bot records, else the players in the world. */
    private static List<Triple<UUID, String, String>> humansOf(Run run) {
        if (devObserve) return List.of();
        ArrayList<Triple<UUID, String, String>> fromState = new ArrayList<>();
        for (PlayerRecord p : run.state.players.values()) if (!p.isBot) fromState.add(Triple.of(p.uuid, p.name, p.dungeonClass));
        if (!fromState.isEmpty()) return fromState;
        try {
            ArrayList<Triple<UUID, String, String>> out = new ArrayList<>();
            for (ServerPlayer it : Sim.players()) out.add(Triple.of(it.getUUID(), it.getGameProfile().name(), Players.classOf(it).name()));
            return out;
        } catch (Throwable t) { return List.of(); }
    }

    /** The bots' class order: the boss sim's own leap order for the host, else the usual order. */
    private static List<String> botOrder() {
        try {
            ArrayList<String> out = new ArrayList<>();
            for (DungeonClass c : P3Plan.botOrder()) out.add(c.name());
            return out;
        } catch (Throwable t) { return BotRoster.CLASSES; }
    }

    private static ClearPlan buildPlan(Run run, Session s, PlanState state, PartyStrategy strategy) {
        KRandom rnd = run.subRandom("bots.plan." + s.replans);
        BotStrategyContext ctx = new BotStrategyContext(run.plan, s.roster.seats, new KRandom(rnd.nextLong()), run.packs.runstate, state.now);
        BloodAssignment assignment = BotStrategies.assignment(strategy, ctx);
        ArrayList<Seat> seats = new ArrayList<>();
        for (Seat seat : s.roster.seats) {
            if (seat.isHuman) { seats.add(seat); continue; }
            UUID id = s.roster.uuids.get(seat.index);
            PlayerRecord r = id != null ? run.state.players.get(id) : null;
            if (r == null || !r.ghost) seats.add(seat);
        }
        ArrayList<PersonDoor> people = new ArrayList<>();
        for (PersonDoor it : s.personDoors) people.add(new PersonDoor(it.door, it.seat, Math.max(it.t, state.now), it.at));
        s.personDoors.clear();
        return new ClearPlanner(new PlannerInput(run.plan, s.tables, seats, rnd, assignment, state, speedScale(), BotTuning.ROOM_WORK_SCALE, s.traversals, s.gates, s.areas, people)).build();
    }

    private static ClearPlan buildPlan(Run run, Session s, PlanState state) { return buildPlan(run, s, state, BotStrategies.resolve(run.config.strategy)); }

    @Override
    public void onStage(Run run, RunStage from, RunStage to) {
        Session s = session;
        if (s == null) return;
        if (to.inBoss() || to == RunStage.END) shutdown(s);
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) {
        Session s = session;
        if (s != null) shutdown(s);
    }

    /** The clear is over: the bodies go and the plan is dropped. The party records stay for the HUD. */
    private static void shutdown(Session s) {
        s.bodies.removeAll();
        s.active = false;
    }

    // ---- every tick

    @Override
    public void onTick(Run run) {
        Session s = live(run);
        if (s == null) return;
        if (!run.stage.inDungeon()) return;
        int t = run.t();
        if (!s.announcedJoin) {
            s.announcedJoin = true;
            for (Seat b : s.roster.bots()) run.bus.post(new PlayerJoined(t, s.roster.uuids.get(b.index)));
        }
        if (!s.countdownBuilt) { s.countdownBuilt = true; buildCountdown(run, s, t); }
        s.bodies.prune();
        if (t >= 0) {
            if (coverOn()) coverCheck(run, s, t);
            Integer due = s.replanAt;
            if (due != null && t >= due) { s.replanAt = null; replan(run, s, t); }
            s.executor.tick(t);
        }
        move(run, s, t);
        if (t >= 0) { health(run, s, t); doorCracks(run, s, t); standFallback(run, s, t); leapLines(run, s, t); campKills(run, s, t); }
    }

    /** The bot camping blood kills the camp's mobs as they come down, when no person is in the room to do it. */
    private static void campKills(Run run, Session s, int t) {
        Integer seat = s.plan().milestones.bloodSeat;
        if (seat == null) return;
        PlacedRoom bloodRoom = run.plan.blood();
        if (bloodRoom == null) return;
        int blood = bloodRoom.index;
        UUID uuid = s.roster.uuids.get(seat);
        if (uuid == null) return;
        PlayerRecord me = run.state.players.get(uuid);
        if (me == null) return;
        PlacedRoom at = run.plan.roomAtBlock(me.x, me.z);
        if (me.ghost || at == null || at.index != blood || s.world.humanInside(blood)) return;
        for (MobRecord m : Services.mobs().inRoom(blood)) {
            if (!m.alive || CampKills.WATCHER.equals(m.kind)) continue;
            int due = s.campDeath.computeIfAbsent(m.id, k -> t + CampKills.life(s.campRandom.nextDouble()));
            if (t < due) continue;
            Services.damage().hurtMob(run, m.id, Math.max(Math.max(m.hp, m.maxHp), 1.0) * 2.0, DamageSource.MELEE, uuid);
            killFx(seat, m.x, m.y, m.z);
        }
    }

    /** A bot that has just leapt to the door opener posts it in party chat, as its mod would. */
    private static void leapLines(Run run, Session s, int t) {
        for (Leap l : s.plan().leaps) {
            if (l.t < t - 2 || l.t > t || !s.leapsSaid.add(l.seat + ":" + l.door)) continue;
            UUID meId = s.roster.uuids.get(l.seat);
            PlayerRecord me = meId != null ? run.state.players.get(meId) : null;
            if (me == null) continue;
            if (me.ghost) continue;
            UUID toId = s.roster.uuids.get(l.to);
            PlayerRecord to = toId != null ? run.state.players.get(toId) : null;
            if (to == null) continue;
            double u = s.leapSpelling.computeIfAbsent(l.seat, k -> s.leapRandom.nextDouble());
            Services.channels().chat("§9Party §8> " + PartyStyle.bracket(me) + " " + me.name + "§f: " + Leaps.line(to.name, u));
        }
    }

    /** From the first tick of the countdown (run tick [t], negative): every bot's walk from the spawn block to its gate, and the drop. */
    private static void buildCountdown(Run run, Session s, int t) {
        Pt spawn = s.spawn;
        if (spawn == null) return;
        if (t >= 0) return;
        KRandom rnd = run.subRandom("bots.countdown");
        for (Seat b : s.roster.bots()) {
            Pt gate = s.gates.get(b.index);
            if (gate == null) continue;
            s.countdown.put(b.index, new SeatTrack(b.index, Countdown.legs(spawn, gate, t, rnd), List.of()));
            s.pos.put(b.index, spawn);
            s.falls.put(b.index, new Fall(s.spawnY));
            s.y.put(b.index, s.spawnY);
        }
    }

    /** The hands a teammate death works with: the damage service, aimed at the bot's own room. */
    private static final class DeathHands implements TeamDeath.World {
        final Run run;
        final Session s;

        DeathHands(Run run, Session s) {
            this.run = run;
            this.s = s;
        }

        private PlayerRecord rec(int seat) {
            UUID id = s.roster.uuids.get(seat);
            return id != null ? run.state.players.get(id) : null;
        }

        private List<Seat> bots() {
            ArrayList<Seat> out = new ArrayList<>();
            for (Seat it : s.roster.bots()) {
                PlayerRecord r = rec(it.index);
                if (r != null && !r.ghost) out.add(it);
            }
            return out;
        }

        MobRecord mobOf(int seat) {
            Pt p = s.pos.get(seat);
            if (p == null) return null;
            RoomRecord rr = run.state.roomAt(p.x, p.z);
            if (rr == null) return null;
            int room = rr.index();
            for (MobRecord m : Services.mobs().inRoom(room)) if (m.required && m.alive) return m;
            for (MobRecord m : Services.mobs().inRoom(room)) if (m.alive) return m;
            return null;
        }

        private static Integer randomIndex(List<Seat> list, KRandom random) {
            return list.isEmpty() ? null : list.get(random.nextInt(list.size())).index;
        }

        @Override
        public Integer inFight(KRandom random) {
            ArrayList<Seat> fighting = new ArrayList<>();
            for (Seat it : bots()) if (mobOf(it.index) != null) fighting.add(it);
            return randomIndex(fighting, random);
        }

        @Override public Integer anyone(KRandom random) { return randomIndex(bots(), random); }

        @Override
        public boolean alive(int seat) {
            PlayerRecord r = rec(seat);
            return r != null && !r.ghost;
        }

        @Override
        public double hpPercent(int seat) {
            PlayerRecord r = rec(seat);
            return r != null ? r.hpPercent : 0.0;
        }

        @Override
        public void hit(int seat, double percent) {
            PlayerRecord r = rec(seat);
            if (r == null) return;
            MobRecord m = mobOf(seat);
            Services.damage().hurtPlayer(run, r.uuid, percent, DamageSource.MELEE, m != null ? m.id : null);
        }

        @Override
        public void kill(int seat) {
            PlayerRecord r = rec(seat);
            if (r == null) return;
            MobRecord m = mobOf(seat);
            Services.damage().kill(run, r.uuid, DamageSource.MELEE, m != null ? m.id : null);
        }

        @Override
        public void disconnect(int seat) {
            PlayerRecord r = rec(seat);
            if (r == null) return;
            Services.damage().kill(run, r.uuid, DamageSource.DISCONNECT, null);
        }
    }

    /** Teammate health: the rare scheduled death, and the ordinary hits a mob lands on a teammate that is fighting. */
    private static void health(Run run, Session s, int t) {
        DeathHands hands = new DeathHands(run, s);
        for (TeamDeath d : new ArrayList<>(s.deaths)) d.step(t, hands);
        s.deaths.removeIf(d -> d.finished);
        for (Seat b : s.roster.bots()) {
            UUID id = s.roster.uuids.get(b.index);
            PlayerRecord rec = id != null ? run.state.players.get(id) : null;
            if (rec == null) continue;
            if (rec.ghost) continue;
            MobRecord mob = hands.mobOf(b.index);
            if (mob == null) continue;
            Double pct = s.hits.roll();
            if (pct == null) continue;
            if (rec.hpPercent - pct < BotHealth.HP_FLOOR) continue;
            Services.damage().hurtPlayer(run, rec.uuid, pct, DamageSource.MELEE, mob.id);
        }
    }

    /** A bot-opened door shows the opener's block-break crack on its cells for the tick it opens and the next, and clears it when it falls. */
    private static void doorCracks(Run run, Session s, int t) {
        Iterator<Map.Entry<Integer, Crack>> it = s.cracking.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Crack> e = it.next();
            int door = e.getKey();
            Crack c = e.getValue();
            if (t > c.openT + 1) it.remove();
            else crack(run, door, 0, c.breakerId);
        }
    }

    private static void crack(Run run, int door, int progress, int breakerId) {
        if (door < 0 || door >= run.plan.doors.size()) return;
        Pair<Integer, Integer> spot = run.plan.doors.get(door).spot();
        if (spot == null) return;
        ClientboundBlockDestructionPacket pkt = new ClientboundBlockDestructionPacket(breakerId, new BlockPos(spot.first, DOOR_CRACK_Y, spot.second), progress);
        List<ServerPlayer> players;
        try { players = Sim.players(); } catch (Throwable e) { players = List.of(); }
        for (ServerPlayer p : players) p.connection.send(pkt);
    }

    private static void move(Run run, Session s, int t) {
        List<ServerPlayer> humans;
        try { humans = Sim.players(); } catch (Throwable e) { humans = List.of(); }
        double radius = showDistance();
        for (Seat b : s.roster.bots()) {
            int seat = b.index;
            UUID uuid = s.roster.uuids.get(seat);
            PlayerRecord rec = run.state.players.get(uuid);
            if (rec == null) continue;
            // A ghost is not in the world: no body, no steps.
            if (rec.ghost) { s.bodies.remove(seat); s.excursion.remove(seat); continue; }
            SeatTrack walk = t < 0 ? s.countdown.get(seat) : null;
            Pt plan = walk != null ? walk.positionAt(t) : null;
            if (plan == null) plan = s.executor.positionOf(seat, t);
            if (plan == null) plan = s.pos.get(seat);
            if (plan == null) plan = new Pt(rec.x, rec.z);
            // A bot off the plan to take a key walks there and back.
            Excursion trip = s.excursion.get(seat);
            Pt cur0 = s.pos.get(seat);
            Pt stepped = trip != null ? trip.step(cur0 != null ? cur0 : plan, plan) : null;
            if (trip != null && stepped == null) s.excursion.remove(seat);
            Pt target = stepped != null ? stepped : plan;
            Pt old = s.pos.get(seat);
            if (old == null) old = target;
            if (old.dist(target) > 0.05) s.yaw.put(seat, (float) Math.toDegrees(Math.atan2(-(target.x - old.x), target.z - old.z)));
            boolean walking = old.dist(target) > 0.05;
            double speed = old.dist(target);
            s.pos.put(seat, target);
            final Pt tgt = target;
            boolean near = false;
            for (ServerPlayer h : humans) {
                if (Math.hypot(h.getX() - tgt.x, h.getZ() - tgt.z) <= radius + (s.bodies.has(seat) ? BotTuning.BODY_HYSTERESIS : 0.0)) { near = true; break; }
            }
            BotIdentity identity = s.roster.identities.get(seat);
            BotLook look = identity != null ? identity.look() : new BotLook(s.roster.names.get(seat), uuid, "§a", "", "§a");
            // A bot that has just appeared above the floor drops onto it first.
            Fall fall = s.falls.get(seat);
            Double dropY = null;
            if (fall != null) {
                dropY = fall.step(s.bodies.groundY(target.x, target.z, BotTuning.DEFAULT_FLOOR_Y));
                if (fall.landed) s.falls.remove(seat);
            }
            Act work = t >= 0 ? s.executor.workOf(seat, t) : null;
            BotActivity activity;
            if (work instanceof ClearMobs) activity = BotActivity.FIGHT;
            else if (work instanceof FindSecrets) activity = BotActivity.SEARCH;
            else if (work instanceof SolvePuzzle) activity = BotActivity.PUZZLE;
            else activity = speed > BotMotion.MOVING_ABOVE ? BotActivity.WALK : BotActivity.STAND;
            Float yaw0 = s.yaw.get(seat);
            BodyFrame frame = s.motion.step(seat, t, speed, yaw0 != null ? yaw0 : 0f, activity);
            s.yaw.put(seat, frame.bodyYaw);
            // Into another room: the height is looked for from the usual floor again.
            PlacedRoom roomAt = run.plan.roomAtBlock(target.x, target.z);
            Integer room = roomAt != null ? roomAt.index : null;
            boolean reseat = room != null && !room.equals(s.roomOf.get(seat));
            if (room != null) s.roomOf.put(seat, room);
            Double prevY = s.y.get(seat);
            double y = s.bodies.place(seat, b.dungeonClass, look, target.x, target.z, frame, near, reseat ? BotTuning.DEFAULT_FLOOR_Y : prevY != null ? prevY : BotTuning.DEFAULT_FLOOR_Y,
                    s.motion.held(seat), dropY, reseat);
            s.y.put(seat, y);
            if (s.steps.due(seat, t, walking) && s.bodies.has(seat)) stepSound(target.x, y, target.z);
            rec.x = target.x;
            rec.y = y;
            rec.z = target.z;
            Float yawNow = s.yaw.get(seat);
            rec.extras.put("yaw", yawNow != null ? yawNow : 0f);
        }
    }

    /** A bot killed a mob: it swings, the mob's blast is heard, and now and then the bot's own teleport tick. */
    private static void killFx(int seat, double x, double y, double z) {
        Session s = session;
        if (s == null || !s.active) return;
        s.motion.swingNow(seat);
        if (!s.bodies.has(seat)) return;
        try {
            Sim.sound(SoundEvents.GENERIC_EXPLODE, 1f, 1f, new Vec3(x, y, z), SoundSource.BLOCKS);
            Pt at = s.pos.get(seat);
            if (at != null && s.cast.nextDouble() < 0.4) {
                Double by = s.y.get(seat);
                Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f, new Vec3(at.x, by != null ? by : y, at.z), SoundSource.HOSTILE);
            }
        } catch (Throwable e) { /* no world (headless) */ }
    }

    /** A footstep (volume 0.15, pitch 1.0, blocks channel) at a walking teammate's feet. */
    private static void stepSound(double x, double y, double z) {
        BlockState under;
        try { under = RunBlocks.get((int) Math.floor(x), (int) Math.floor(y - 0.5), (int) Math.floor(z)); } catch (Throwable e) { under = null; }
        SoundType st = under != null ? under.getSoundType() : null;
        String group;
        if (st == SoundType.GRAVEL) group = "gravel";
        else if (st == SoundType.WOOL) group = "wool";
        else if (st == SoundType.WOOD) group = "wood";
        else if (st == SoundType.GRASS) group = "grass";
        else group = "";
        SoundEvent event = switch (StepSounds.forFloor(group)) {
            case GRAVEL -> SoundEvents.GRAVEL_STEP;
            case WOOL -> SoundEvents.WOOL_STEP;
            case WOOD -> SoundEvents.WOOD_STEP;
            case GRASS -> SoundEvents.GRASS_STEP;
            case GLASS -> SoundEvents.GLASS_PLACE;
        };
        try { Sim.sound(event, 0.15f, 1f, new Vec3(x, y, z), SoundSource.BLOCKS); } catch (Throwable e) { /* no world (headless) */ }
    }

    // ---- re-planning

    private static void schedule(Session s, int t, String reason) {
        int at = t + BotTuning.REPLAN_DEBOUNCE;
        if (s.replanAt == null) { s.replanAt = at; s.replanReason = reason; } else s.replanReason += "; " + reason;
    }

    private static void replan(Run run, Session s, int t) {
        try {
            s.replans++;
            DoorPlan next = null;
            for (DoorPlan d : s.plan().doors.values()) if (d.openT > t && (next == null || d.openT < next.openT)) next = d;
            Integer opener = next != null ? next.seat : null;
            PlanState state = ReplanState.build(run.state, Math.max(t, 0), s.plan(), s.roster, new LinkedHashMap<>(s.pos), s.humanTouched, s.covered, opener, coverAfter());
            ClearPlan fresh = buildPlan(run, s, state);
            s.executor.replace(fresh);
            s.kept.add("t=" + t + " " + s.replanReason);
            if (s.kept.size() > 12) s.kept.remove(0);
        } catch (Throwable e) { Ec.LOG.warn("[ads] bots re-plan failed; the old plan stays", e); }
    }

    /** Rooms the plan gave the human that nobody has been into for coverAfter ticks after their time: the bots cover them from now on. */
    private static void coverCheck(Run run, Session s, int t) {
        if (s.covered || t - s.lastCoverCheck < COVER_CHECK_TICKS) return;
        s.lastCoverCheck = t;
        int after = coverAfter();
        boolean overdue = false;
        for (RoomPlan rp : s.plan().rooms.values()) {
            if (!rp.soft || t <= rp.arriveT + after) continue;
            RoomRecord r = run.state.room(rp.room);
            if (r == null || r.check.ordinal() >= CheckMark.WHITE.ordinal()) continue;
            if (s.humanTouched.containsKey(rp.room)) continue;
            overdue = true;
            break;
        }
        if (overdue) { s.covered = true; schedule(s, t, "covering the player's rooms"); }
    }

    @Override
    public void requestReplan(Run run, String reason) {
        Session s = live(run);
        if (s == null) return;
        schedule(s, Math.max(run.t(), 0), reason);
    }

    // ---- events

    @Override
    public void onEvent(Run run, RunEvent event) {
        Session s = live(run);
        if (s == null) return;
        int t = run.t();
        if (event instanceof RoomEntered e) {
            Seat seat = s.roster.seatOf(e.player);
            if (seat != null && seat.isHuman) {
                s.humanTouched.put(e.room, t);
                RoomPlan rp = s.plan().rooms.get(e.room);
                if (rp != null && !rp.soft && rp.arriveT > t) schedule(s, t, "the player took room " + e.room);
            }
        } else if (event instanceof DoorOpened e) {
            Seat bySeat = e.by != null ? s.roster.seatOf(e.by) : null;
            Seat opener = bySeat != null && !bySeat.isHuman ? bySeat : null;
            if (opener != null) {
                Integer eid = s.bodies.entityId(opener.index);
                Crack c = new Crack(t, eid != null ? eid : CRACK_ID_BASE + e.door);
                s.cracking.put(e.door, c);
                crack(run, e.door, 0, c.breakerId);
            }
            boolean byHuman = e.by != null && bySeat != null && bySeat.isHuman;
            DoorPlan planned = s.plan().doors.get(e.door);
            if (byHuman && planned != null && planned.openT > t + 10) schedule(s, t, "the player opened door " + e.door);
            if (byHuman && e.door >= 0 && e.door < run.plan.doors.size() && run.plan.doors.get(e.door).kind == DoorKind.WITHER) {
                PlayerRecord rec = run.state.players.get(e.by);
                if (rec != null) {
                    s.personDoors.add(new PersonDoor(e.door, bySeat.index, t, new Pt(rec.x, rec.z)));
                    schedule(s, t, "the player opened door " + e.door + " (leaps)");
                }
            }
        } else if (event instanceof RoomCleared || event instanceof RoomGreen) {
            boolean cleared = event instanceof RoomCleared;
            int room = cleared ? ((RoomCleared) event).room : ((RoomGreen) event).room;
            RoomPlan rp = s.plan().rooms.get(room);
            if (rp != null && !rp.soft) {
                Integer when = cleared ? rp.whiteT : rp.greenT;
                if ((when != null ? when : Integer.MAX_VALUE) > t + EARLY_TICKS) schedule(s, t, "room " + room + " finished early");
            }
        } else if (event instanceof PlayerDamaged e) {
            Seat seat = s.roster.seatOf(e.player);
            if (seat != null && !seat.isHuman && e.percent > 0.0) s.bodies.hurt(seat.index);
        } else if (event instanceof PlayerDied e) {
            Seat seat = s.roster.seatOf(e.player);
            if (seat != null && !seat.isHuman) schedule(s, t, "a bot died");
        } else if (event instanceof PlayerRevived e) {
            Seat seat = s.roster.seatOf(e.player);
            if (seat != null && !seat.isHuman) schedule(s, t, "a bot came back");
        } else if (event instanceof KeyDropped e) {
            keyDropped(s, e, t);
        } else if (event instanceof KeyPicked e) {
            s.stands.remove(e.kind);
            for (Excursion x : s.excursion.values()) x.release();
            s.executor.keyArrived(t);
        } else if (event instanceof PlayerLeft e) {
            Seat seat = s.roster.seatOf(e.player);
            if (seat != null && seat.isHuman) { s.covered = true; schedule(s, t, "a player left"); }
        } else if (event instanceof SavepointRestored) {
            restoredNow(run, s);
        } else if (event instanceof DoorFell e) {
            Crack c = s.cracking.remove(e.door);
            if (c != null) crack(run, e.door, 255, c.breakerId);
        }
    }

    /** A key stand appeared. A bot that is already near it steps onto it; nobody is sent from across the dungeon. */
    private static void keyDropped(Session s, KeyDropped e, int t) {
        Pt stand = new Pt(e.x, e.z);
        s.stands.put(e.kind, new StandWatch(stand, t));
        Integer near = nearestBot(s, stand, NEAR_STAND);
        if (near == null) return;
        s.excursion.putIfAbsent(near, new Excursion(stand, STAND_HOLD_TICKS));
    }

    /** The seat of the living bot nearest to [at] within [within] blocks that is not on a trip, or null. */
    private static Integer nearestBot(Session s, Pt at, double within) {
        LinkedHashMap<Integer, Pt> positions = new LinkedHashMap<>();
        for (Seat b : s.roster.bots()) {
            if (s.excursion.get(b.index) != null) continue;
            PlayerRecord r = s.run.state.players.get(s.roster.uuids.get(b.index));
            if (r == null || r.ghost) continue;
            Pt p = s.pos.get(b.index);
            if (p != null) positions.put(b.index, p);
        }
        return StandDuty.nearest(positions, at, within);
    }

    /** Sends a bot for a stand that has waited too long for anybody to take it. */
    private static void standFallback(Run run, Session s, int t) {
        if (s.stands.isEmpty()) return;
        List<ServerPlayer> humans;
        try { humans = Sim.players(); } catch (Throwable e) { humans = List.of(); }
        for (StandWatch w : s.stands.values()) {
            if (w.sentFallback || t - w.droppedT < FALLBACK_WAIT) continue;
            w.sentFallback = true;
            ArrayList<Pt> hp = new ArrayList<>();
            for (ServerPlayer h : humans) hp.add(new Pt(h.getX(), h.getZ()));
            if (StandDuty.someoneThere(hp, w.at)) continue;
            Integer seat = nearestBot(s, w.at, FALLBACK_REACH);
            if (seat == null) continue;
            s.excursion.put(seat, new Excursion(w.at, FALLBACK_HOLD_TICKS));
        }
    }

    // ---- savepoints

    @Override
    public JsonObject snapshot(Run run) {
        Session s = live(run);
        if (s == null) return null;
        JsonObject o = new JsonObject();
        o.addProperty("covered", s.covered);
        JsonArray a = new JsonArray();
        for (Map.Entry<Integer, Pt> e : s.pos.entrySet()) {
            JsonObject b = new JsonObject();
            b.addProperty("seat", e.getKey());
            b.addProperty("x", e.getValue().x);
            b.addProperty("z", e.getValue().z);
            a.add(b);
        }
        o.add("bots", a);
        return o;
    }

    @Override
    public void restore(Run run, JsonObject data) {
        Session s = live(run);
        if (s == null) return;
        JsonElement cov = data.get("covered");
        s.covered = cov != null && cov.isJsonPrimitive() ? cov.getAsBoolean() : false;
        JsonArray bots = data.getAsJsonArray("bots");
        if (bots != null) for (JsonElement el : bots) {
            JsonObject b = el.getAsJsonObject();
            s.pos.put(b.get("seat").getAsInt(), new Pt(b.get("x").getAsDouble(), b.get("z").getAsDouble()));
        }
    }

    /** After every module restored: forget what the old plan finished and plan again from the restored run. */
    private static void restoredNow(Run run, Session s) {
        s.world.reset();
        s.bodies.removeAll();
        s.humanTouched.clear();
        s.excursion.clear();
        s.stands.clear();
        s.executor.forgetFinished();
        replan(run, s, Math.max(run.t(), 0));
    }

    // ---- the service

    @Override
    public List<UUID> botIds(Run run) {
        Session s = live(run);
        if (s == null) return List.of();
        ArrayList<UUID> out = new ArrayList<>();
        for (Seat b : s.roster.bots()) out.add(s.roster.uuids.get(b.index));
        return out;
    }

    @Override
    public UUID ownerOfRoom(Run run, int roomIndex) {
        Session s = live(run);
        if (s == null) return null;
        RoomPlan rp = s.plan().rooms.get(roomIndex);
        if (rp == null) return null;
        return s.roster.uuids.get(rp.seat);
    }

    @Override
    public List<LeapTarget> leapTargets(Run run, UUID viewer) {
        Session s0 = session;
        Session s = s0 != null && s0.run == run ? s0 : null;
        PlayerRecord me = run.state.players.get(viewer);
        List<String> order = leapOrder(me != null ? me.dungeonClass : null);
        ArrayList<LeapTarget> out = new ArrayList<>();
        for (String cls : order) {
            PlayerRecord rec = null;
            for (PlayerRecord it : run.state.players.values()) if (cls.equals(it.dungeonClass) && !it.uuid.equals(viewer)) { rec = it; break; }
            if (rec == null) continue;
            Seat bseat = s != null ? s.roster.seatOf(rec.uuid) : null;
            Seat bot = bseat != null && !bseat.isHuman ? bseat : null;
            if (bot != null) {
                Pt p0 = s.pos.get(bot.index);
                Pt p = p0 != null ? p0 : new Pt(rec.x, rec.z);
                // A far bot's chunk may not be loaded: asking for its block loads it.
                RunBlocks.get((int) Math.floor(p.x), 69, (int) Math.floor(p.z));
                Double sy = s.y.get(bot.index);
                double hint = sy != null ? sy : rec.y != 0.0 ? rec.y : BotTuning.DEFAULT_FLOOR_Y;
                double y = s.bodies.groundY(p.x, p.z, hint);
                s.y.put(bot.index, y);
                Float yw = s.yaw.get(bot.index);
                out.add(new LeapTarget(rec.uuid, rec.name, cls, p.x, y, p.z, yw != null ? yw : 0f, 0f, !rec.ghost));
            } else if (Boolean.TRUE.equals(rec.extras.get("replayMate"))) {
                // A replay's recorded teammate: where their recorded body is.
                RunBlocks.get((int) Math.floor(rec.x), 69, (int) Math.floor(rec.z));
                Object yw = rec.extras.get("yaw");
                out.add(new LeapTarget(rec.uuid, rec.name, cls, rec.x, rec.y, rec.z, yw instanceof Float f ? f : 0f, 0f, !rec.ghost));
            } else {
                ServerPlayer pl = null;
                try {
                    for (ServerPlayer it : Sim.players()) if (it.getUUID().equals(rec.uuid)) { pl = it; break; }
                } catch (Throwable t) { pl = null; }
                out.add(new LeapTarget(rec.uuid, rec.name, cls, pl != null ? pl.getX() : rec.x, pl != null ? pl.getY() : rec.y, pl != null ? pl.getZ() : rec.z,
                        pl != null ? pl.getYRot() : 0f, pl != null ? pl.getXRot() : 0f, !rec.ghost && pl != null));
            }
        }
        return out;
    }

    /** The classes in the viewer's leap order (slots 1 to 4): the boss sim's order for them, else the usual order without them. */
    private static List<String> leapOrder(String cls) {
        try {
            DungeonClass viewer = null;
            if (cls != null) for (DungeonClass c : DungeonClass.values()) if (c.name().equals(cls)) { viewer = c; break; }
            if (viewer != null) {
                ArrayList<String> out = new ArrayList<>();
                for (DungeonClass c : P3Plan.orderFor(viewer)) out.add(c.name());
                return out;
            }
        } catch (Throwable t) { /* the boss sim's order is not available (headless): the usual order */ }
        ArrayList<String> out = new ArrayList<>();
        for (String c : BotRoster.CLASSES) if (!c.equals(cls)) out.add(c);
        return out;
    }

    // ---- dev commands

    @Override public List<AdsCommand> devCommands() { return BotCommands.commands(); }

    public static final int COVER_CHECK_TICKS = 40;
    /** A bot this near a fresh key stand steps onto it. */
    public static final double NEAR_STAND = 6.0;
    /** Ticks a bot waits on a stand it stepped onto for the key to be taken. */
    public static final int STAND_HOLD_TICKS = 4;
    /** Ticks after a drop before a bot is sent for a stand nobody has taken, the farthest it is sent from, and how long it waits there. */
    public static final int FALLBACK_WAIT = 20;
    public static final double FALLBACK_REACH = 12.0;
    public static final int FALLBACK_HOLD_TICKS = 40;
    /** The height of the door cells the crack is drawn on and the fake breaker ids the cracks use (one per door). */
    public static final int DOOR_CRACK_Y = 70;
    public static final int CRACK_ID_BASE = 1_900_000;
    /** A room finishing this many ticks before the plan says is "early" and makes a re-plan. */
    public static final int EARLY_TICKS = 60;
}
