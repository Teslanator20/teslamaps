// Ported from ADM's p3sim/clear/mobs/MobsModule.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.pack.mobs.HitQuantiles;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobModifier;
import com.teslamaps.adm.sim.pack.mobs.MobPack;
import com.teslamaps.adm.sim.pack.mobs.HpScaleTable;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.AdsServerContext;
import com.teslamaps.adm.sim.run.DamageSource;
import com.teslamaps.adm.sim.run.DoorOpened;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.MobDamaged;
import com.teslamaps.adm.sim.run.MobKilled;
import com.teslamaps.adm.sim.run.MobRef;
import com.teslamaps.adm.sim.run.MobSpawned;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerJoined;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.DamageService;
import com.teslamaps.adm.sim.run.api.DoorService;
import com.teslamaps.adm.sim.run.api.MobRecord;
import com.teslamaps.adm.sim.run.api.MobService;
import com.teslamaps.adm.sim.run.api.MobSpec;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SimSettings;
import com.teslamaps.adm.sim.util.KRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MobsModule implements AdsModule, MobService {
    public static final MobsModule INSTANCE = new MobsModule();

    private MobsModule() {}

    @Override public String id() { return "mobs"; }

    @Override public String owner() { return "mobs"; }

    private static final int QUICK_VANISH_TICKS = 20;
    private static final int RETRY_AFTER_VANISH_TICKS = 200;

    private static final int BUILD_BACKOFF_TICKS = 40;

    // ---- settings (null until registerSettings; every read has the default) ----

    private static BoolSetting sEnabled = null;
    private static BoolSetting sFakePlayers = null;
    private static BoolSetting sReplay = null;
    private static BoolSetting sHealthData = null;
    private static BoolSetting sAttacks = null;
    private static NumSetting sSpawnDistance = null;
    private static NumSetting sWakeRadius = null;
    private static NumSetting sPartial = null;
    private static NumSetting sMinibossChance = null;
    private static NumSetting sMoverShare = null;
    private static BoolSetting sStroll = null;
    private static NumSetting sAggro = null;
    private static NumSetting sLose = null;
    private static NumSetting sMeleeReach = null;
    private static NumSetting sMeleeCadence = null;
    private static NumSetting sRangedRange = null;
    private static NumSetting sRangedCadence = null;
    private static NumSetting sStaticReach = null;
    private static NumSetting sZombieSpeed = null;
    private static NumSetting sSkeletonSpeed = null;
    private static NumSetting sWitherSpeed = null;
    private static NumSetting sTeleportEvery = null;
    private static NumSetting sOtherSpeed = null;
    private static NumSetting sPatrolSpeed = null;

    @Override
    public void registerSettings() {
        sEnabled = SimSettings.bool("Mobs Spawn", true, "The clear has its mobs. Off: rooms stay empty (they count as cleared at once).");
        sFakePlayers = SimSettings.bool("Mobs Fake Players", true, "Crypt Dreadlords, Souleaters, Zombie Commanders, the minibosses and the Shadow Assassin reach your game as real players (UUID version 2), as on Hypixel, so Odin's and Blade Addons' star ESP draw them. Off: they are zombies.");
        sReplay = SimSettings.bool("Mobs Replay Rooms", true, "A room gets the mobs of one recorded visit (clusters and counts stay together). Off: every spawn cell rolls on its own with the chance the recordings gave it.");
        sHealthData = SimSettings.bool("Mobs Health Data", true, "Writes a mob's HP into its body's health data too (Hypixel does), so recorders that read the entity data agree with the name tag.");
        sAttacks = SimSettings.bool("Mobs Attack", true, "Mobs that reach you hit you (through the damage ledger). Off: they walk and shoot at nothing.");
        sSpawnDistance = SimSettings.num("Mobs Spawn Distance", 48, 16, 160, 4, " blocks", "A room's mobs appear once a player is this close (recorded Hypixel bodies first appear at a median of 41 blocks, 85 percent within 3 chunks).");
        sMinibossChance = SimSettings.num("Mobs Miniboss Chance", 65, 0, 100, 5, "%", "How often an ordinary room that can hold a miniboss really does (Dungeon Recorder: 65 percent). Yellow miniboss rooms always hold theirs.");
        sWakeRadius = SimSettings.num("Mobs Wake Radius", 9.0, 1.0, 16.0, 0.5, " blocks", "A sleeping Fels wakes when a player's body comes this close, in 3D (measured about 9, always awake at 8; only a body wakes it, not arrows or Implosion).");
        sPartial = SimSettings.num("Mobs Partial Weight", 0.25, 0.0, 1.0, 0.05, "", "How often a room replays a recording that only caught part of the room (fewer than half the usual stars), relative to a full one.");
        sMoverShare = SimSettings.num("Mobs Chaser Share", 0, 0, 100, 5, "%", "Share of ordinary mobs that chase a player they see until they reach it. 0 is Hypixel: the Dungeon Recorder shows no chases, only short walks (Mobs Stroll).");
        sStroll = SimSettings.bool("Mobs Stroll", true, "Mobs that do not chase take short walks now and then, more often the nearer you are, a third of them at you (Dungeon Recorder: median 6 blocks; 7 walks a minute within 6 blocks, 1.4 beyond 32). Off: they stand still.");
        sAggro = SimSettings.num("Mobs Follow Range", 35.0, 2.0, 60.0, 1.0, " blocks", "A walking mob takes as its target a player it can see within this (1.8's follow range; recorded first steps reach p90 32 to 43 blocks).");
        sLose = SimSettings.num("Mobs Give Up Range", 35.0, 4.0, 80.0, 1.0, " blocks", "A chasing mob gives up when its target is farther than this, or out of sight for 3 seconds (1.8).");
        sMeleeReach = SimSettings.num("Mobs Hit Reach", 1.43, 1.0, 6.0, 0.05, " blocks", "How close a mob must be to hit (1.8's reach for a zombie-sized body; not in the recordings).");
        sMeleeCadence = SimSettings.num("Mobs Melee Cadence", 20, 5, 100, 1, " ticks", "Ticks between one mob's hits (1.8's attack timer).");
        sRangedRange = SimSettings.num("Mobs Shot Range", 15.0, 4.0, 40.0, 1.0, " blocks", "Skeletons, the Sniper and the Withermancer shoot what they see from at most this far (1.8: 15; recorded shots p90 13.5 blocks).");
        sRangedCadence = SimSettings.num("Mobs Shot Gap", 60, 10, 200, 1, " ticks", "Ticks between one shooter's shots at the edge of its range; 20 at point blank, in between by distance (1.8).");
        sStaticReach = SimSettings.num("Mobs Standing Reach", 1.43, 0.0, 6.0, 0.05, " blocks", "A mob that stands still still hits a player this close. 0: standing mobs never hit.");
        sZombieSpeed = SimSettings.num("Mobs Zombie Speed", 0.28, 0.05, 1.5, 0.01, " b/tick", "Chase speed of zombie-bodied mobs the speed table has no number for (most archetypes have their own measured one).");
        sSkeletonSpeed = SimSettings.num("Mobs Skeleton Speed", 0.137, 0.05, 1.5, 0.01, " b/tick", "Chase speed of skeleton-bodied mobs (same source).");
        sWitherSpeed = SimSettings.num("Mobs Wither Skeleton Speed", 0.27, 0.05, 1.5, 0.01, " b/tick", "Chase speed of the Withermancer (same source; the spread is wide).");
        sOtherSpeed = SimSettings.num("Mobs Walker Speed", 0.32, 0.05, 1.5, 0.01, " b/tick", "Chase speed of Fels and of any body without its own number (Fels: measured 0.32).");
        sPatrolSpeed = SimSettings.num("Mobs Patrol Speed", 0.25, 0.05, 1.0, 0.01, " b/tick", "How fast a miniboss walks its patrol loop before it sees anyone (recorded adventurers: median 0.25 to 0.29).");
        sTeleportEvery = SimSettings.num("Mobs Assassin Teleport", 0, 0, 400, 5, " ticks", "The Shadow Assassin appears behind its target this often. 0: it walks, as recorded assassins do (2 of 65 ever jumped).");
    }

    private static boolean enabled() { return sEnabled != null ? sEnabled.get() : true; }
    static boolean fakePlayersOn() { return sFakePlayers != null ? sFakePlayers.get() : true; }
    static boolean healthDataOn() { return sHealthData != null ? sHealthData.get() : true; }
    private static boolean attacks() { return sAttacks != null ? sAttacks.get() : true; }
    private static double spawnDistance() { return sSpawnDistance != null ? sSpawnDistance.getInt() : 48; }
    private static double wakeRadius(MobArchetype arch) { return sWakeRadius != null ? sWakeRadius.get() : 9.0; }

    private static AiParams params() {
        return new AiParams(
                (sMoverShare != null ? sMoverShare.getInt() : 0) / 100.0, sStroll != null ? sStroll.get() : true,
                AiParams.defStrollRates(), AiParams.defStrollAimed(), AiParams.DEF_STROLL_PACE,
                sAggro != null ? sAggro.get() : 35.0, sLose != null ? sLose.get() : 35.0,
                sMeleeReach != null ? sMeleeReach.get() : 1.43, sMeleeCadence != null ? sMeleeCadence.getInt() : 20,
                sRangedRange != null ? sRangedRange.get() : 15.0, sRangedCadence != null ? sRangedCadence.getInt() : 60,
                AiParams.DEF_RANGED_MIN_CADENCE,
                sStaticReach != null ? sStaticReach.get() : 1.43,
                AiParams.DEF_SEARCH_CHANCE, AiParams.DEF_UNSEEN_MEMORY,
                sZombieSpeed != null ? sZombieSpeed.get() : 0.28, sSkeletonSpeed != null ? sSkeletonSpeed.get() : 0.137, sWitherSpeed != null ? sWitherSpeed.get() : 0.27,
                sOtherSpeed != null ? sOtherSpeed.get() : 0.32,
                sTeleportEvery != null ? sTeleportEvery.getInt() : 0,
                sPatrolSpeed != null ? sPatrolSpeed.get() : 0.25,
                AiParams.DEF_SPEEDY_FACTOR, AiParams.DEF_PATROL_ARRIVE
        );
    }

    public static MobStage stage = RealStage.INSTANCE;
    public static Supplier<DoorService> doors = () -> Services.doors();
    public static Supplier<DamageService> damage = () -> Services.damage();
    public static Consumer<String> chat = it -> Services.channels().chat(it);
    public static BiConsumer<UUID, String> bar = (p, text) ->
            Services.channels().actionBarSegment("combat-xp", text, com.teslamaps.adm.sim.clear.hud.ActionBarModel.REPLACES_DEFENSE, COMBAT_BAR_TTL, new Audience.Only(p));

    public static final double COMBAT_XP_MIN = 600.0;
    public static final double COMBAT_XP_MAX = 780.0;
    public static final double COMBAT_XP_START = 25_000_000.0;
    public static final int COMBAT_BAR_TTL = 45;

    public static String combatBarText(double gain, double total) {
        String g = new java.math.BigDecimal(gain).setScale(1, java.math.RoundingMode.HALF_EVEN).toPlainString();
        if (g.endsWith(".0")) g = g.substring(0, g.length() - 2);
        return "§3+" + g + " Combat (" + String.format(java.util.Locale.ROOT, "%,d", Math.round(total)) + "/0)";
    }

    private static final HashMap<UUID, Double> combatTotals = new HashMap<>();

    // ---- state of the current run ----

    private static final LinkedHashMap<UUID, MobEntry> entries = new LinkedHashMap<>();
    private static final MobChatter chatter = new MobChatter();
    private static KRandom chatterRng = new KRandom(0);
    private static KRandom aiRng = new KRandom(0);
    private static final HashMap<Integer, List<MobEntry>> byRoom = new HashMap<>();
    private static final HashMap<Integer, MobEntry> byEntityId = new HashMap<>();
    private static final HashSet<Integer> rolled = new HashSet<>();

    public static volatile BiFunction<Run, Integer, List<PlannedMob>> roomMobs = null;
    public static volatile Function<UUID, double[]> replayPose = null;

    public static volatile BiFunction<Run, Integer, Double> doorScale = null;

    private static double scale = 1.0;
    private static final HashSet<Integer> scaledRooms = new HashSet<>();
    private static final ArrayList<Integer> pendingScale = new ArrayList<>();
    private static boolean noPackSaid = false;
    private static boolean deferSpawnEvents = false;
    private static final ArrayList<MobRef> pendingSpawned = new ArrayList<>();
    private static final ArrayList<MobEntry> spikes = new ArrayList<>();

    private static void reset() {
        combatTotals.clear(); entries.clear(); byRoom.clear(); byEntityId.clear(); rolled.clear(); scale = 1.0; scaledRooms.clear(); pendingScale.clear(); noPackSaid = false; pendingSpawned.clear(); deferSpawnEvents = false;
    }

    // ---- the run lifecycle ----

    @Override
    public void onRunStart(Run run) {
        FairyRoom.clear(); RoomDecor.clear();
        discardAll();
        reset();
        MobPack pack = run.packs.mobs;
        if (pack == null) {
            noPackSaid = true;
            chat.accept("§8[§6ADS§8] §7The mob tables (" + com.teslamaps.adm.sim.pack.PackFiles.MOBS + ") could not be loaded, so this dungeon has no mobs. /ads dev packs says why.");
            return;
        }
        if (!enabled()) return;
        String floor = run.config.floor;
        if (run.config.mobHpScale != null) scale = run.config.mobHpScale;
        else {
            HpScaleTable table = pack.hpScaleFor(floor);
            scale = table != null ? MobRolls.drawHpScale(table, run.subRandom("mobs.scale")) : 1.0;
        }
        chatterRng = run.subRandom("mobs.chatter");
        aiRng = run.subRandom("mobs.ai");
        deferSpawnEvents = true;
        for (PlacedRoom room : run.plan.rooms) if (room.type != RoomType.ENTRANCE && room.type != RoomType.FAIRY && room.type != RoomType.BLOOD) spawnRoom(run, room.index);
        deferSpawnEvents = false;
    }

    @Override
    public void onStage(Run run, RunStage from, RunStage to) {
        if (to == RunStage.BOSS_ENTRY || to == RunStage.BOSS || to == RunStage.END) clearAll(run);
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) {
        clearAll(run);
    }

    @Override
    public void onEvent(Run run, RunEvent event) {
        if (event instanceof PlayerJoined pj) stage.announceTo(pj.player, entries.values());
        if (event instanceof DoorOpened d && d.kind == com.teslamaps.adm.sim.pack.layouts.DoorKind.WITHER) {
            if (d.door >= 0 && d.door < run.plan.doors.size()) {
                PlacedDoor it = run.plan.doors.get(d.door);
                pendingScale.add(behind(run, it.roomA, it.roomB));
            }
        }
    }

    private static int behind(Run run, int a, int b) {
        PlacedRoom entrance = run.plan.entrance();
        if (entrance == null) return b;
        int start = entrance.index;
        HashMap<Integer, Integer> dist = new HashMap<>();
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(start); dist.put(start, 0);
        while (!q.isEmpty()) {
            int r = q.removeFirst();
            for (PlacedDoor d : run.plan.doors) {
                if (!d.kind.connected()) continue;
                int o;
                if (d.roomA == r) o = d.roomB;
                else if (d.roomB == r) o = d.roomA;
                else continue;
                if (!dist.containsKey(o)) { dist.put(o, dist.get(r) + 1); q.add(o); }
            }
        }
        return dist.getOrDefault(a, Integer.MAX_VALUE) > dist.getOrDefault(b, Integer.MAX_VALUE) ? a : b;
    }

    private static void scaleRoom(Run run, int room) {
        if (!scaledRooms.add(room)) return;
        BiFunction<Run, Integer, Double> ds = doorScale;
        Double fromDoor = ds != null ? ds.apply(run, room) : null;
        double f = fromDoor != null ? fromDoor : scale;
        if (f == 1.0) return;
        for (MobEntry e : byRoom.getOrDefault(room, Collections.emptyList())) {
            MobRecord r = e.record;
            if (!r.alive) continue;
            r.maxHp = (double) Math.round(r.maxHp * f); r.hp = (double) Math.round(r.hp * f);
            if (e.live) stage.refresh(e);
        }
    }

    @Override
    public void onTick(Run run) {
        if (!pendingScale.isEmpty()) { List<Integer> q = new ArrayList<>(pendingScale); pendingScale.clear(); for (int r : q) scaleRoom(run, r); }
        if (!pendingSpawned.isEmpty()) { List<MobRef> q = new ArrayList<>(pendingSpawned); pendingSpawned.clear(); for (MobRef m : q) run.bus.post(new MobSpawned(run.t(), m)); }
        if (run.t() < 0) return;
        stage.tick();
        if (stage == RealStage.INSTANCE && run.stage.inDungeon() && run.stage != RunStage.BOSS_ENTRY && run.stage != RunStage.BOSS) Ec.safely("ads fairies", () -> { FairyRoom.tick(run); RoomDecor.tick(run); });
        if (entries.isEmpty() || !stage.available() || run.stage == RunStage.BOSS_ENTRY || run.stage == RunStage.BOSS || run.stage == RunStage.END) return;
        MobPack pack = run.packs.mobs;
        if (pack == null) return;
        List<PlayerRecord> players = run.state.alivePlayers();
        int t = run.serverTick;
        AiParams params = params();
        double maxDist = spawnDistance();
        for (MobEntry e : new ArrayList<>(entries.values())) {
            if (!e.alive()) continue;
            if (e.live && stage.isGone(e)) {
                if (e.record.entityId != null) byEntityId.remove(e.record.entityId);
                stage.release(e); e.record.entityId = null;
                if (t - e.builtAt < QUICK_VANISH_TICKS) {
                    e.retryAt = t + RETRY_AFTER_VANISH_TICKS;
                    if (++e.vanishes == 3) Ec.LOG.warn("[ads] the " + e.arch.id + " body vanished right after being built 3 times; the world may be on peaceful difficulty");
                }
            }
            if (!e.live) {
                if (t >= e.retryAt && (t + e.plan.id.hashCode()) % 5 == 0 && near(players, e, maxDist) && stage.isLoaded((int) Math.floor(e.x), (int) Math.floor(e.z))) {
                    if (stage.materialise(e)) {
                        e.builtAt = t; e.buildFailures = 0;
                        if (e.record.entityId != null) byEntityId.put(e.record.entityId, e);
                    } else {
                        e.buildFailures++;
                        e.retryAt = t + (BUILD_BACKOFF_TICKS << Math.min(e.buildFailures - 1, 4));
                        if (e.buildFailures == 3 && !e.plainBody) { e.plainBody = true; Ec.LOG.warn("[ads] the " + e.arch.id + " body failed to build 3 times; trying a plain body"); }
                    }
                }
                continue;
            }
            stage.rescue(e);
            double[] pos = stage.position(e);
            if (pos == null) continue;
            Function<UUID, double[]> rp = replayPose;
            double[] rec = rp != null ? rp.apply(e.record.id) : null;
            if (rec != null) {
                if (e.asleep) { e.asleep = false; stage.wake(e); }
                stage.place(e, rec[0], rec[1], rec[2]);
                double[] now = stage.position(e);
                if (now != null) { e.record.x = now[0]; e.record.y = now[1]; e.record.z = now[2]; }
                continue;
            }
            if (e.asleep) {
                double r = wakeRadius(e.arch);
                boolean any = false;
                for (PlayerRecord it : players) if (dist3(it, pos) <= r) { any = true; break; }
                if (any) { e.asleep = false; stage.wake(e); } else continue;
            }
            runAi(run, e, pos, pack, players, params);
            double[] now = stage.position(e);
            if (now != null) { e.record.x = now[0]; e.record.y = now[1]; e.record.z = now[2]; }
        }
        if (stage == RealStage.INSTANCE) Ec.safely("ads mob chatter", () -> chatter.tick(t, entries.values(), chatterRng));
    }

    private static boolean near(List<PlayerRecord> players, MobEntry e, double d) {
        double d2 = d * d;
        for (PlayerRecord it : players) {
            double dx = it.x - e.x, dz = it.z - e.z;
            if (dx * dx + dz * dz <= d2) return true;
        }
        return false;
    }

    private static double dist3(PlayerRecord p, double[] pos) {
        double dx = p.x - pos[0], dy = p.y - pos[1], dz = p.z - pos[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- AI v0 ----

    private static void runAi(Run run, MobEntry e, double[] pos, MobPack pack, List<PlayerRecord> targets, AiParams p) {
        AiKind kind = MobPlanner.aiKind(e.arch, p.assassinTeleportEvery > 0);
        PlayerRecord best = null;
        double bestD = Double.MAX_VALUE;
        for (PlayerRecord h : targets) {
            double dx = h.x - pos[0], dz = h.z - pos[2];
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(h.y - pos[1]) > 3.0) d += 1000.0;
            if (d < bestD) { bestD = d; best = h; }
        }
        double reach = e.plan.mover ? Math.max(p.aggroRadius, p.loseRadius) : Math.max(Math.max(p.rangedRange, p.staticReach), p.strolls ? 20.0 : 0.0);
        boolean seen = best != null && bestD <= reach && stage.canSee(e, best.x, best.y + 1.62, best.z);
        AiTarget target = best != null ? new AiTarget(best.x, best.z, bestD, seen) : null;
        double speed = MobAi.speedFor(e.arch.id, e.arch.entity, e.modifier != null && "Speedy".equals(e.modifier.id), p);
        AiAction action = MobAi.step(kind, e.plan.mover, pos[0], pos[2], speed, target, e.ai, e.plan.loop, p, aiRng.nextDouble() < p.searchChance, aiRng);
        if (e.hidden && e.ai.chasing) { e.hidden = false; stage.reveal(e); }
        stage.act(e, action, best != null ? best.y : Double.NaN, best != null ? best.x : Double.NaN, best != null ? best.z : Double.NaN);
        if (!attacks() || best == null || best.isBot) return;
        if (action == AiAction.Melee.INSTANCE) {
            damage.get().meleeHit(run, e.record.id, best.uuid);
        } else if (action == AiAction.Shoot.INSTANCE) {
            HitQuantiles table = null;
            if (pack.playerDamage != null && pack.playerDamage.projectile != null) {
                Map<String, HitQuantiles> it = pack.playerDamage.projectile;
                table = it.get("minecraft:wither_skeleton".equals(e.arch.entity) ? "wither_skull" : "arrow");
                if (table == null) table = it.get("arrow");
            }
            if (table != null) damage.get().hurtPlayer(run, best.uuid, MobRolls.sampleHit(table, run.subRandom("mobs.shot." + run.serverTick + e.plan.id)), DamageSource.PROJECTILE, e.record.id, false);
        }
    }

    // ---- MobService ----

    @Override
    public List<MobRecord> spawnRoom(Run run, int roomIndex) {
        if (!rolled.contains(roomIndex)) {
            rolled.add(roomIndex);
            MobPack pack = run.packs.mobs;
            PlacedRoom room = roomIndex >= 0 && roomIndex < run.plan.rooms.size() ? run.plan.rooms.get(roomIndex) : null;
            com.teslamaps.adm.sim.pack.rooms.Placement placement = room != null ? room.placement : null;
            if (pack != null && room != null && placement != null && enabled()) {
                RoomPack rp = run.packs.rooms;
                Function<String, String> alias = rp != null ? (String n) -> rp.familyOf(n) : null;
                PlanParams params = new PlanParams(run.config.floor, 1.0, sReplay != null && !sReplay.get() ? SpawnMode.CELLS : SpawnMode.LAYOUTS,
                        sPartial != null ? sPartial.get() : MobRolls.PARTIAL_LAYOUT_WEIGHT, (sMoverShare != null ? sMoverShare.getInt() : 0) / 100.0,
                        PlanParams.defaultDeferred(), (sMinibossChance != null ? sMinibossChance.getInt() : 65) / 100.0);
                BiFunction<Run, Integer, List<PlannedMob>> rm = roomMobs;
                List<PlannedMob> plans = rm != null ? rm.apply(run, roomIndex) : null;
                if (plans == null) plans = MobPlanner.planRoom(pack, room.name, alias, placement, params, run.subRandom("mobs.room." + roomIndex));
                for (PlannedMob pm : plans) register(run, pack, pm, roomIndex);
            }
        }
        return inRoom(roomIndex);
    }

    private static MobArchetype archOf(MobPack pack, int index) {
        MobArchetype a = pack.archetype(index);
        if (a == null) a = BloodLooks.archetype(index);
        if (a == null) a = LevelLooks.archetype(index);
        return a;
    }

    private static MobEntry register(Run run, MobPack pack, PlannedMob pm, Integer roomIndex) {
        MobArchetype arch = archOf(pack, pm.arch);
        if (arch == null) return null;
        MobRecord rec = new MobRecord(pm.id, arch.id, arch.name, roomIndex, pm.star, pm.required, "miniboss".equals(arch.cls), arch.disguise && fakePlayersOn());
        rec.hp = pm.maxHp; rec.maxHp = pm.maxHp; rec.x = pm.x; rec.y = pm.y; rec.z = pm.z;
        MobModifier mod = pack.modifier(pm.modifier);
        MobEntry e = new MobEntry(rec, pm, arch, !pm.modifier.isEmpty() ? mod : null);
        entries.put(pm.id, e);
        if (roomIndex != null) {
            byRoom.computeIfAbsent(roomIndex, k -> new ArrayList<>()).add(e);
            if (pm.required) {
                RoomRecord rr = run.state.room(roomIndex);
                if (rr != null) { rr.mobsRequired++; rr.mobsRequiredAlive++; }
            }
        }
        if (deferSpawnEvents) pendingSpawned.add(rec.ref()); else run.bus.post(new MobSpawned(run.t(), rec.ref()));
        return e;
    }

    private static final HashSet<String> standInSaid = new HashSet<>();
    public static final String STAND_IN_KIND = "zombie_soldier";

    @Override
    public MobRecord spawn(Run run, MobSpec spec) {
        MobPack pack = run.packs.mobs;
        if (pack == null) return null;
        MobArchetype arch = "blood".equals(spec.extras.get("camp")) ? BloodLooks.forName(spec.extras.get("name")) : null;
        if (arch == null) arch = pack.archetypeById(spec.kind);
        if (arch == null) {
            String standIn = spec.extras.get("standIn");
            if (standIn != null) arch = pack.archetypeById(standIn);
        }
        if (arch == null) {
            arch = pack.archetypeById(STAND_IN_KIND);
            if (arch != null && standInSaid.add(spec.kind)) Ec.LOG.info("[ads] the mob pack has no '" + spec.kind + "'; spawning a " + STAND_IN_KIND + " in its place");
        }
        if (arch == null) {
            Ec.LOG.warn("[ads] the mob pack has no '" + spec.kind + "', so it cannot be spawned");
            return null;
        }
        String w = spec.extras.get("modifier");
        String word = w != null && !w.isEmpty() ? w : null;
        MobModifier mod = word != null ? pack.modifier(word) : null;
        double roomScale = scale;
        if (spec.roomIndex != null) {
            int r = spec.roomIndex;
            PlacedRoom room = r >= 0 && r < run.plan.rooms.size() ? run.plan.rooms.get(r) : null;
            if (room != null && room.type != RoomType.BLOOD) {
                if (scaledRooms.contains(room.index)) {
                    BiFunction<Run, Integer, Double> ds = doorScale;
                    Double fromDoor = ds != null ? ds.apply(run, room.index) : null;
                    roomScale = fromDoor != null ? fromDoor : scale;
                } else roomScale = 1.0;
            }
        }
        double hp;
        if (spec.hp != null) hp = spec.hp;
        else {
            Long base = arch.hpFor(run.config.floor);
            if (base == null) return null;
            hp = MobRolls.spawnHp(base, roomScale, mod);
        }
        KRandom rng = run.subRandom("mobs.spec." + entries.size() + spec.kind);
        UUID id = arch.disguise ? MobRolls.uuidV2(rng) : MobRolls.uuidV4(rng);
        String modifier = BloodLooks.isBlood(arch) ? (word != null ? word : "") : (mod != null ? mod.id : "");
        PlannedMob pm = new PlannedMob(id, arch.index, spec.starred, spec.required, modifier, hp, spec.x, spec.y, spec.z, rng.nextFloat() * 360f - 180f, !"static".equals(arch.movement), null);
        MobEntry e = register(run, pack, pm, spec.roomIndex);
        return e != null ? e.record : null;
    }

    @Override
    public MobRecord mob(UUID id) {
        MobEntry e = entries.get(id);
        return e != null ? e.record : null;
    }

    @Override
    public List<MobRecord> inRoom(int roomIndex) {
        List<MobEntry> list = byRoom.get(roomIndex);
        List<MobRecord> out = new ArrayList<>();
        if (list != null) for (MobEntry e : list) out.add(e.record);
        return out;
    }

    @Override
    public Collection<MobRecord> all() {
        List<MobRecord> out = new ArrayList<>(entries.size());
        for (MobEntry e : entries.values()) out.add(e.record);
        return out;
    }

    @Override
    public int requiredAlive(int roomIndex) {
        List<MobEntry> list = byRoom.get(roomIndex);
        if (list == null) return 0;
        int n = 0;
        for (MobEntry e : list) if (e.alive() && e.record.required) n++;
        return n;
    }

    @Override
    public MobRecord recordOfEntity(int entityId) {
        MobEntry e = byEntityId.get(entityId);
        return e != null ? e.record : null;
    }

    @Override
    public boolean isAsleep(UUID id) {
        MobEntry e = entries.get(id);
        return e != null && e.asleep;
    }

    @Override
    public MobRecord setHp(Run run, UUID id, double newHp, UUID by) {
        MobEntry e = entries.get(id);
        if (e == null) return null;
        MobRecord r = e.record;
        if (!r.alive) return r;
        if (e.asleep && newHp < r.hp) {
            PlayerRecord pr = by != null ? run.state.players.get(by) : null;
            if (pr != null && pr.isBot) { e.asleep = false; if (e.live) stage.wake(e); } else return r;
        }
        double old = r.hp;
        if (newHp <= 0.0) { kill(run, e, by); return r; }
        r.hp = Math.min(newHp, Math.max(r.maxHp, newHp));
        if (e.live) stage.refresh(e);
        if (r.hp < old) {
            if (e.live) stage.hurtFlash(e);
            run.bus.post(new MobDamaged(run.t(), r.ref(), old - r.hp, r.hp, by));
        }
        return r;
    }

    private static void kill(Run run, MobEntry e, UUID by) {
        MobRecord r = e.record;
        r.hp = 0.0; r.alive = false;
        double[] pos = e.live ? stage.position(e) : null;
        double x = pos != null ? pos[0] : e.x;
        double z = pos != null ? pos[2] : e.z;
        if (e.live) stage.die(e);
        stage.retract(e);
        if (r.entityId != null) byEntityId.remove(r.entityId);
        r.entityId = null;
        Integer room = r.roomIndex;
        boolean lastRequired = false;
        if (r.required && room != null) {
            RoomRecord rr = run.state.room(room);
            if (rr != null) { rr.mobsRequiredAlive = Math.max(0, rr.mobsRequiredAlive - 1); lastRequired = rr.mobsRequiredAlive == 0; }
        }
        run.bus.post(new MobKilled(run.t(), r.ref(), by));
        PlayerRecord killer = by != null ? run.state.players.get(by) : null;
        if (by != null && killer != null && !killer.isBot && !"special".equals(e.arch.cls)) {
            double gain = Math.round(run.subRandom("mobs.xp." + e.plan.id).nextDouble(COMBAT_XP_MIN, COMBAT_XP_MAX) * 10.0) / 10.0;
            Double prev = combatTotals.get(by);
            double total = (prev != null ? prev : COMBAT_XP_START) + gain;
            combatTotals.put(by, total);
            bar.accept(by, combatBarText(gain, total));
        }
        if (lastRequired && room != null && !com.teslamaps.adm.sim.clear.state.StateController.keysFromRecording && doors.get().keyForRoom(run, room) != null) doors.get().dropKey(run, room, x, z);
    }

    @Override
    public void remove(Run run, UUID id) {
        MobEntry e = entries.remove(id);
        if (e == null) return;
        MobRecord r = e.record;
        stage.release(e); stage.retract(e);
        if (r.entityId != null) byEntityId.remove(r.entityId);
        Integer room = r.roomIndex;
        if (room != null) {
            List<MobEntry> list = byRoom.get(room);
            if (list != null) list.remove(e);
            if (r.required) {
                RoomRecord rr = run.state.room(room);
                if (rr != null) {
                    if (r.alive) rr.mobsRequiredAlive = Math.max(0, rr.mobsRequiredAlive - 1);
                    rr.mobsRequired = Math.max(0, rr.mobsRequired - 1);
                }
            }
        }
        r.alive = false;
    }

    @Override
    public void clearAll(Run run) {
        FairyRoom.clear(); RoomDecor.clear();
        discardAll();
        reset();
    }

    private static void discardAll() {
        chatter.clear();
        stage.flush();
        for (MobEntry e : entries.values()) { stage.release(e); stage.retract(e); }
        for (MobEntry e : spikes) { stage.release(e); stage.retract(e); }
        spikes.clear();
    }

    // ---- savepoints ----

    @Override
    public JsonObject snapshot(Run run) {
        JsonObject o = new JsonObject();
        o.addProperty("scale", scale);
        JsonArray a = new JsonArray();
        for (MobEntry e : entries.values()) {
            PlannedMob p = e.plan;
            JsonObject m = new JsonObject();
            m.addProperty("id", p.id.toString()); m.addProperty("arch", p.arch); m.addProperty("star", p.star); m.addProperty("req", p.required);
            m.addProperty("mod", p.modifier); m.addProperty("max", p.maxHp); m.addProperty("rmax", e.record.maxHp); m.addProperty("hp", e.record.hp); m.addProperty("alive", e.record.alive);
            m.addProperty("room", e.record.roomIndex != null ? e.record.roomIndex : -1);
            m.addProperty("px", p.x); m.addProperty("py", p.y); m.addProperty("pz", p.z); m.addProperty("yaw", p.yaw);
            m.addProperty("x", e.x); m.addProperty("y", e.y); m.addProperty("z", e.z);
            m.addProperty("mover", p.mover); m.addProperty("asleep", e.asleep); m.addProperty("hidden", e.hidden);
            if (p.loop != null) {
                JsonArray arr = new JsonArray();
                for (double[] pt : p.loop) { JsonArray it = new JsonArray(); it.add(pt[0]); it.add(pt[1]); arr.add(it); }
                m.add("loop", arr);
            }
            a.add(m);
        }
        o.add("mobs", a);
        JsonArray rolledArr = new JsonArray();
        List<Integer> rs = new ArrayList<>(rolled); Collections.sort(rs);
        for (int r : rs) rolledArr.add(r);
        o.add("rolled", rolledArr);
        JsonArray scaledArr = new JsonArray();
        List<Integer> ss = new ArrayList<>(scaledRooms); Collections.sort(ss);
        for (int r : ss) scaledArr.add(r);
        o.add("scaled", scaledArr);
        return o;
    }

    @Override
    public void restore(Run run, JsonObject data) {
        discardAll();
        reset();
        MobPack pack = run.packs.mobs;
        if (pack == null) return;
        JsonElement sc = data.get("scale");
        scale = sc != null ? sc.getAsDouble() : 1.0;
        JsonArray ra = data.getAsJsonArray("rolled");
        if (ra != null) for (JsonElement it : ra) rolled.add(it.getAsInt());
        JsonArray sa = data.getAsJsonArray("scaled");
        if (sa != null) for (JsonElement it : sa) scaledRooms.add(it.getAsInt());
        JsonArray mobs = data.getAsJsonArray("mobs");
        if (mobs == null) mobs = new JsonArray();
        for (JsonElement el : mobs) {
            JsonObject m = el.getAsJsonObject();
            MobArchetype arch = archOf(pack, m.get("arch").getAsInt());
            if (arch == null) continue;
            JsonArray la = m.getAsJsonArray("loop");
            List<double[]> loop = null;
            if (la != null) {
                loop = new ArrayList<>();
                for (JsonElement p : la) loop.add(new double[]{p.getAsJsonArray().get(0).getAsDouble(), p.getAsJsonArray().get(1).getAsDouble()});
            }
            PlannedMob pm = new PlannedMob(UUID.fromString(m.get("id").getAsString()), arch.index, m.get("star").getAsBoolean(), m.get("req").getAsBoolean(), m.get("mod").getAsString(), m.get("max").getAsDouble(),
                    m.get("px").getAsDouble(), m.get("py").getAsDouble(), m.get("pz").getAsDouble(), m.get("yaw").getAsFloat(), m.get("mover").getAsBoolean(), loop);
            int roomRaw = m.get("room").getAsInt();
            Integer room = roomRaw >= 0 ? roomRaw : null;
            MobRecord rec = new MobRecord(pm.id, arch.id, arch.name, room, pm.star, pm.required, "miniboss".equals(arch.cls), arch.disguise && fakePlayersOn());
            JsonElement rmax = m.get("rmax");
            rec.maxHp = rmax != null ? rmax.getAsDouble() : pm.maxHp; rec.hp = m.get("hp").getAsDouble(); rec.alive = m.get("alive").getAsBoolean();
            rec.x = m.get("x").getAsDouble(); rec.y = m.get("y").getAsDouble(); rec.z = m.get("z").getAsDouble();
            MobModifier mod = pack.modifier(pm.modifier);
            MobEntry e = new MobEntry(rec, pm, arch, !pm.modifier.isEmpty() ? mod : null);
            e.x = rec.x; e.y = rec.y; e.z = rec.z;
            e.asleep = m.get("asleep").getAsBoolean(); e.hidden = m.get("hidden").getAsBoolean();
            entries.put(pm.id, e);
            if (room != null) byRoom.computeIfAbsent(room, k -> new ArrayList<>()).add(e);
        }
        for (RoomRecord r : run.state.rooms) { r.mobsRequired = 0; r.mobsRequiredAlive = 0; }
        for (MobEntry e : entries.values()) {
            Integer room = e.record.roomIndex;
            if (room == null) continue;
            if (!e.record.required) continue;
            RoomRecord rr = run.state.room(room);
            if (rr != null) { rr.mobsRequired++; if (e.record.alive) rr.mobsRequiredAlive++; }
        }
    }

    // ---- the spike ----

    @Override public void onInit() { DisguiseSpike.register(); }

    @Override public List<AdsCommand> devCommands() { return List.of(DisguiseSpike.command(this), mobsCommand, aiCommand); }

    private static final AdsCommand mobsCommand = new AdsCommand("mobs", "/ads dev mobs", "Lists the mobs of the run room by room: required alive of total, bodies built, sleepers.", true, null, (AdsServerContext ctx) -> {
        Run run = ctx.run();
        if (run == null) ctx.reply("No run is on.");
        else for (String line : summaryLines(run)) ctx.reply(line);
    });

    private static String f1(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }

    private static final AdsCommand aiCommand = new AdsCommand("mobai", "/ads dev mobai", "The AI state of the ten mobs nearest you: kind, mover, chasing, distance, sight.", true, null, (AdsServerContext ctx) -> {
        Run run = ctx.run();
        PlayerRecord me = null;
        if (run != null) for (PlayerRecord it : run.state.alivePlayers()) if (!it.isBot) { me = it; break; }
        if (run == null || me == null) { ctx.reply("No run or no player."); return; }
        int humans = 0;
        for (PlayerRecord it : run.state.alivePlayers()) if (!it.isBot) humans++;
        ctx.reply("t=" + run.t() + " stage=" + run.stage + " available=" + stage.available() + " humans=" + humans);
        AiParams p = params();
        final PlayerRecord meF = me;
        List<MobEntry> alive = new ArrayList<>();
        for (MobEntry e : entries.values()) if (e.alive()) alive.add(e);
        alive.sort((a, b) -> Double.compare(Math.hypot(a.x - meF.x, a.z - meF.z), Math.hypot(b.x - meF.x, b.z - meF.z)));
        for (MobEntry e : alive.subList(0, Math.min(10, alive.size()))) {
            double[] pos = stage.position(e);
            double d = Math.hypot(e.x - meF.x, e.z - meF.z);
            String nav;
            if (e.body instanceof Mob m) {
                net.minecraft.world.level.pathfinder.Path pa = m.getNavigation().getPath();
                nav = "done=" + m.getNavigation().isDone() + " path=" + (pa != null ? "" + pa.getNodeCount() + "@" + pa.getTarget() : "none") + " ai=" + !m.isNoAi() + " spd=" + m.getSpeed();
            } else nav = e.body != null ? e.body.getClass().getSimpleName() : null;
            String posText = null;
            if (pos != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pos.length; i++) { if (i > 0) sb.append(","); sb.append(f1(pos[i])); }
                posText = sb.toString();
            }
            ctx.reply(e.arch.id + " kind=" + MobPlanner.aiKind(e.arch, p.assassinTeleportEvery > 0) + " mover=" + e.plan.mover + " live=" + e.live + " asleep=" + e.asleep + " chasing=" + e.ai.chasing
                    + " unseen=" + e.ai.unseen + " d=" + f1(d) + " see=" + stage.canSee(e, meF.x, meF.y + 1.62, meF.z) + " nav=" + nav
                    + " lastNav=" + f1(e.lastNavX) + "," + f1(e.lastNavZ) + " pos=" + posText);
        }
    });

    static List<String> summaryLines(Run run) {
        if (entries.isEmpty()) return List.of("The module holds no mobs.");
        List<String> lines = new ArrayList<>();
        int built = 0;
        for (MobEntry e : entries.values()) if (e.live) built++;
        lines.add(entries.size() + " mobs, HP scale " + String.format(java.util.Locale.ROOT, "%.2f", scale) + ", " + built + " bodies built.");
        for (Map.Entry<Integer, List<MobEntry>> en : new TreeMap<>(byRoom).entrySet()) {
            int room = en.getKey();
            List<MobEntry> list = en.getValue();
            String name = room >= 0 && room < run.plan.rooms.size() ? run.plan.rooms.get(room).name : "room " + room;
            int req = 0, reqAlive = 0, live = 0, alive = 0, asleep = 0;
            for (MobEntry it : list) {
                if (it.record.required) { req++; if (it.alive()) reqAlive++; }
                if (it.live) live++;
                if (it.alive()) alive++;
                if (it.asleep) asleep++;
            }
            lines.add(name + ": " + reqAlive + "/" + req + " required alive, " + live + "/" + alive + " bodies built" + (asleep > 0 ? ", " + asleep + " asleep" : ""));
        }
        List<String> stuck = new ArrayList<>();
        for (PlacedRoom r : run.plan.rooms) {
            if (doors.get().keyForRoom(run, r.index) == null) continue;
            List<MobEntry> list = byRoom.get(r.index);
            int req = 0;
            if (list != null) for (MobEntry it : list) if (it.record.required) req++;
            if (req == 0) stuck.add(r.name);
        }
        if (!stuck.isEmpty()) lines.add("§cNo required mob, so no key will drop in: " + String.join(", ", stuck));
        return lines;
    }

    static String spikeClear() {
        int n = spikes.size();
        for (MobEntry e : spikes) { stage.release(e); stage.retract(e); }
        spikes.clear();
        return n == 0 ? "No spike mob is standing." : "Removed the spike mob.";
    }

    static String spikeSpawn(ServerPlayer player, MobArchetype arch, double dist) {
        ServerLevel level = SimServer.level();
        if (level == null) return "The sim world is not running.";
        for (MobEntry e : spikes) { stage.release(e); stage.retract(e); }
        spikes.clear();
        Vec3 look = player.getLookAngle();
        double x = player.getX() + look.x * dist, z = player.getZ() + look.z * dist;
        KRandom rng = new KRandom(System.nanoTime());
        Long hpBase = arch.hpFor("F7");
        double hp = (double) (hpBase != null ? hpBase : 840_000L);
        UUID id = arch.disguise ? MobRolls.uuidV2(rng) : MobRolls.uuidV4(rng);
        PlannedMob pm = new PlannedMob(id, arch.index, true, false, "", hp, x, player.getY(), z, player.getYRot() + 180f, false, null);
        MobRecord rec = new MobRecord(id, arch.id, arch.name, null, true, false, "miniboss".equals(arch.cls), arch.disguise);
        rec.hp = hp; rec.maxHp = hp; rec.x = x; rec.y = player.getY(); rec.z = z;
        MobEntry e = new MobEntry(rec, pm, arch, null);
        e.asleep = false; e.hidden = false;
        if (!MobWorld.materialise(e, level, true, healthDataOn())) return "The mob could not be made (see the log).";
        spikes.add(e);
        return "A " + arch.name + " stands " + String.format(java.util.Locale.ROOT, "%.0f", dist) + " blocks ahead: body id " + (e.body != null ? e.body.getId() : null)
                + ", tag id " + (e.stand != null ? e.stand.getId() : null) + ", uuid version " + id.version() + ", body " + (e.body != null ? e.body.getClass().getSimpleName() : null) + ".";
    }
}
