package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Fight.kt

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class Fight {
    private Fight() {}

    public abstract static class Phase {
        public final String name;
        public int t = 0;

        public Phase(String name) { this.name = name; }

        public void start() {}
        public void tick() {}
        public void stop() {}
        public abstract Start restart();
    }

    public enum Start {
        P1("P1 Maxor"), P2("P2 Storm"), P3("P3 Goldor"), S1("S1"), S2("S2"), S3("S3"), S4("S4"), CORE("Core"), P4("P4 Necron");

        public final String label;

        Start(String label) { this.label = label; }
    }

    public static Phase phase = null;

    public static Phase previous = null;

    public static Terminals.Type forcedTerminal() { return P3Sim.forcedTerminal(); }

    public static int refreshPhase = 18;

    public static int serverTick = 0;

    private static final int PING_BASE = 698 + KRandom.DEFAULT.nextInt(100);

    private static final class Later {
        final int at;
        final String what;
        final int epoch;
        final Runnable run;
        final ServerPlayer actor = Sim.acting();
        final boolean all = Sim.toAll();

        Later(int at, String what, int epoch, Runnable run) { this.at = at; this.what = what; this.epoch = epoch; this.run = run; }

        void go() { Sim.asPlayer(actor, all, run); }
    }

    private static int epoch = 0;
    private static final ArrayList<Later> later = new ArrayList<>();

    public static void later(int ticks, String what, Runnable run) {
        later.add(new Later(serverTick + Math.max(ticks, 0), what, epoch, run));
    }

    public static void later(int ticks, Runnable run) { later(ticks, "later", run); }

    public static int pingTicks() { return (P3Sim.ping() + 25) / 50; }

    private static final class Timed {
        final long dueNs;
        final String what;
        final int epoch;
        final Runnable run;
        final ServerPlayer actor = Sim.acting();
        final boolean all = Sim.toAll();

        Timed(long dueNs, String what, int epoch, Runnable run) { this.dueNs = dueNs; this.what = what; this.epoch = epoch; this.run = run; }

        void go() { Sim.asPlayer(actor, all, run); }
    }

    private static final ArrayList<Timed> timed = new ArrayList<>();

    public static double pingMs() {
        double base = (double) P3Sim.ping();
        if (base <= 0.0) return 0.0;
        if (!P3Sim.jitter()) return base;
        double u = KRandom.DEFAULT.nextDouble();
        double[][] q = JITTER_Q;
        double f = q[q.length - 1][1];
        for (int i = 1; i < q.length; i++) if (u <= q[i][0]) { double[] a = q[i - 1]; double[] b = q[i]; f = a[1] + (b[1] - a[1]) * (u - a[0]) / (b[0] - a[0]); break; }
        return Math.max(base + (f - 1.0) * 33.0, base * 0.5);
    }

    private static final double[][] JITTER_Q = {
        {0.0, 0.85}, {0.10, 0.91}, {0.50, 1.0}, {0.90, 1.9},
        {0.99, 2.2}, {0.998, 3.5}, {1.0, 8.0},
    };

    public static void afterPing(String what, Runnable run) { afterMs(pingMs(), what, run); }

    public static void afterMs(double ms, String what, Runnable run) {
        Phase p = phase;
        if (ms <= 0.0) run.run();
        else timed.add(new Timed(System.nanoTime() + (long) (ms * 1e6), what, epoch, () -> { if (phase == p) run.run(); }));
    }

    public static final PerPlayer<Pair<Float, Float>> lastRot = new PerPlayer<>(() -> Pair.of(0f, 0f));

    public static void noteUseItem(ServerPlayer p) {
        MinecraftServer s = p.level().getServer();
        if (s != SimServer.server || !s.isSameThread()) return;
        Sim.asPlayer(p, () -> { lastRot.set(Pair.of(p.getXRot(), p.getYRot())); });
    }

    private static final HashMap<UUID, ArrayDeque<Pair<Long, Vec3>>> posHistories = new HashMap<>();

    public static Vec3 seenPos(ServerPlayer p) {
        return Sim.asPlayer(p, Sim.toAll(), () -> {
            Vec3 live = p.position();
            double ms = P3Sim.ping() / 2.0;
            ArrayDeque<Pair<Long, Vec3>> posHistory = posHistories.get(p.getUUID());
            if (ms <= 0.0 || posHistory == null || posHistory.isEmpty()) return live;
            long target = System.nanoTime() - (long) (ms * 1e6);
            Pair<Long, Vec3> prev = null;
            for (Pair<Long, Vec3> e : posHistory) {
                if (e.first >= target) {
                    Pair<Long, Vec3> a = prev;
                    if (a == null) return e.second;
                    double t = (double) (target - a.first) / Math.max(e.first - a.first, 1L);
                    return a.second.lerp(e.second, Math.clamp(t, 0.0, 1.0));
                }
                prev = e;
            }
            Pair<Long, Vec3> a = prev;
            if (a == null) return live;
            double t = (double) (target - a.first) / Math.max(System.nanoTime() - a.first, 1L);
            return a.second.lerp(live, Math.clamp(t, 0.0, 1.0));
        });
    }

    public static void reset(MinecraftServer server) {
        PerPlayer.clearAll();
        com.teslamaps.adm.sim.run.LagRuntime.reset();
        serverTick = 0;
        epoch++;
        later.clear();
        timed.clear(); posHistories.clear();
        Practice.exit();
        Terminals.closeAll();
        Sim.forEachPlayer(pl -> SimItems.reset());
        SimItems.resetShared();
        Players.clear();
        PartyServer.reset();
        phase = null;
        BossBar.hide();
        Sim.clearEntities();
        Sim.command("time set noon");
        Sim.command("weather clear");
        Blocks.restoreAll();
        Devices.showIdle();
    }

    public static void stop() {
        Phase cur = phase;
        if (cur != null) Ec.safely("p3sim stop " + cur.name, cur::stop);
        for (ServerPlayer pl : Sim.players()) pl.connection.send(new ClientboundClearTitlesPacket(true));
        phase = null;
        epoch++;
        later.clear();
        timed.clear();
        Masks.stopped();
        Recorder.finish();
        Terminals.closeAll();
        Sim.forEachPlayer(pl -> SimItems.reset());
        SimItems.resetShared();
        BossBar.hide();
    }

    public static void join(ServerPlayer player) {
        Ec.safely("ads no advancements", () -> player.getAdvancements().stopListening());
        ServerPlayer host = SimServer.player();
        if (host == player) com.teslamaps.adm.sim.run.EngineSpikes.joined(player);
        if (host != null && host != player) {
            Sim.asPlayer(player, () -> {
                PartyServer.rosterDirty = true;
                setup(player);
                Sim.tp(player, host.getX(), host.getY(), host.getZ(), host.getYRot(), host.getXRot());
            });
            com.teslamaps.adm.sim.run.Runs.playerJoined(player);
            return;
        }
        if (com.teslamaps.adm.sim.run.RunLauncher.pending != null) {
            Sim.asPlayer(player, () -> { startPendingRun(player, RUN_START_WAIT); });
            return;
        }
        lobby(player);
    }

    private static final int RUN_START_WAIT = 40;

    private static void startPendingRun(ServerPlayer player, int triesLeft) {
        later(1, "run start", () -> {
            com.teslamaps.adm.sim.run.RunConfig pending = com.teslamaps.adm.sim.run.RunLauncher.pending;
            if (pending == null) return;
            boolean in = false;
            for (ServerPlayer pl : Sim.players()) if (pl == player) { in = true; break; }
            if (!in && triesLeft > 0) { startPendingRun(player, triesLeft - 1); return; }
            com.teslamaps.adm.sim.run.Runs.StartResult res = com.teslamaps.adm.sim.run.Runs.start(pending);
            if (res instanceof com.teslamaps.adm.sim.run.Runs.StartResult.Failed failed) { Sim.note(failed.message); lobby(player); }
        });
    }

    private static void lobby(ServerPlayer player) {
        setup(player);
        if (phase == null) {
            Sim.tp(player, Spots.LOBBY.x, Spots.LOBBY.y, Spots.LOBBY.z, Spots.LOBBY.yaw, Spots.LOBBY.pitch);
            later(20, "welcome", () -> {
                Sim.note("Welcome to ADS, the Advanced Dungeon Simulator. §fRight click the Nether Star§7 (or /ads) for the menu.");
                if (P3Sim.autoStart()) start(Start.P3);
            });
        }
    }

    public static void leave(ServerPlayer player) {
        if (phase instanceof P1Maxor m) m.playerLeft(player);
        Masks.leftOne(player);
        Players.forgetFiles(player);
        boolean joiner = !Sim.isHost(player);
        if (joiner) com.teslamaps.adm.sim.run.Runs.playerLeft(player);
        DungeonClass c = Players.classOf(player);
        Set<String> theirs = Players.jobsOf(player);
        Vec3 at = player.position();
        Players.left(player);
        if (joiner && phase instanceof GoldorPhase g) Party.takeOver(g, c, at, theirs);
        PartyServer.left(player);
        Party.rosterChanged();
        if (!Sim.isHost(player)) Sim.everyone(() -> { Sim.note("§e" + player.getGameProfile().name() + "§7 left the party."); });
    }

    public static void setup(ServerPlayer player) { setup(player, true); }

    public static void setup(ServerPlayer player, boolean giveHotbar) {
        Sim.asPlayer(player, () -> {
            if (player.gameMode() != GameType.CREATIVE) player.setGameMode(GameType.SURVIVAL);
            Sim.guardBlocks();
            applySpeed(player);
            AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1.0);
            AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
            if (step != null) step.setBaseValue(0.6);
            player.setInvulnerable(true);
            AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(40.0);
            player.setHealth(40f);
            AttributeInstance maxAbs = player.getAttribute(Attributes.MAX_ABSORPTION);
            if (maxAbs != null) { if (maxAbs.getBaseValue() < 16.0) maxAbs.setBaseValue(16.0); }
            player.setAbsorptionAmount(16f);
            player.removeEffect(MobEffects.SATURATION);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 1, true, true, false));
            player.getFoodData().setFoodLevel(20); player.getFoodData().setSaturation(20f);
            if (giveHotbar) SimItems.giveHotbar(player, !(phase instanceof P1Maxor) && !(phase instanceof P2Storm), true);
            if (player.gameMode() != GameType.CREATIVE) {
                player.removeEffect(MobEffects.INVISIBILITY);
                player.getAbilities().flying = false; player.getAbilities().mayfly = false; player.onUpdateAbilities();
            }
            SimItems.miningEffects(player, true);
        });
    }

    private static void resetI4Solver() { later(2, "i4 solver reset", () -> PartyClient.send("arrowsReset")); }

    public static int speedStat(ServerPlayer player) {
        return Sim.asPlayer(player, Sim.toAll(), () -> {
            boolean racing = "RACING_HELMET".equals(SimItems.idOf(player.getItemBySlot(EquipmentSlot.HEAD)));
            return P3Sim.speed() + (P3Sim.phoenix() ? 0 : 100) + (racing ? 100 : 0);
        });
    }

    public static void applySpeed(ServerPlayer player) {
        int speed = speedStat(player);
        AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null) ms.setBaseValue((double) Math.max(speed, 100) / 1000.0);
        player.getAbilities().setWalkingSpeed(Math.max(speed, 100) / 1000f);
        player.onUpdateAbilities();
    }

    public static volatile Start lastStart = Start.P3;

    public static void start(Start what) { start(what, false); }

    public static void start(Start what, boolean practice) {
        Sim.asPlayer((ServerPlayer) null, () -> { startAll(what, practice); });
    }

    public static void startRunBoss(com.teslamaps.adm.sim.run.BossEntryOptions options, Integer t0Tick) {
        Sim.asPlayer((ServerPlayer) null, () -> { startAll(Start.P1, false, options, t0Tick); });
    }

    private static void startAll(Start what, boolean practice) { startAll(what, practice, null, null); }

    private static void startAll(Start what, boolean practice, com.teslamaps.adm.sim.run.BossEntryOptions run, Integer runT0) {
        if (Sim.players().isEmpty()) return;
        if (run == null) com.teslamaps.adm.sim.run.Runs.stop();
        if (!practice) Practice.exit();
        lastStart = what;
        stop();
        PartyClient.send("termStart", 0);
        later.clear();
        Sim.clearEntities();
        Party.clear();
        if (run == null) Blocks.restoreAll();
        Blocks.prepare(what);
        if (what != Start.P4) Devices.showIdle();
        resetI4Solver();
        SimItems.rebreak();
        Sim.forEachPlayer(pl -> {
            setup(pl, run == null || run.giveHotbar);
            if (P3Sim.blackCatAtStart() && P3Sim.phoenix()) { P3Sim.setPhoenix(false); applySpeed(pl); }
            Bows.start();
            Lava.reset();
        });
        Masks.reset();
        refreshPhase = KRandom.DEFAULT.nextInt(36) < 21 ? 18 : 17;
        Stats.runStart = what == Start.P1 ? (runT0 != null ? runT0 : serverTick) : -1;
        previous = null;
        String split;
        int termsDone;
        switch (what) {
            case P1 -> { split = SimHooks.MAXOR; termsDone = 0; }
            case P2 -> { split = SimHooks.STORM; termsDone = 0; }
            case P3, S1 -> { split = SimHooks.TERMS; termsDone = 0; }
            case S2 -> { split = SimHooks.TERMS; termsDone = 1; }
            case S3 -> { split = SimHooks.TERMS; termsDone = 2; }
            case S4 -> { split = SimHooks.TERMS; termsDone = 3; }
            case CORE -> { split = SimHooks.GOLDOR; termsDone = 0; }
            case P4 -> { split = SimHooks.NECRON; termsDone = 0; }
            default -> throw new IllegalStateException();
        }
        if (practice) PartyClient.send("splitsStop"); else PartyClient.send("splitsStart", termsDone, split);
        Recorder.begin(what.label);
        Phase p = switch (what) {
            case P1 -> new P1Maxor(run);
            case P2 -> new P2Storm();
            case P3 -> new StormEnd();
            case S1 -> practice ? new GoldorPhase(1) : new StormEnd();
            case S2 -> new GoldorPhase(2);
            case S3 -> new GoldorPhase(3);
            case S4 -> new GoldorPhase(4);
            case CORE -> new GoldorPhase(5);
            case P4 -> new P4Necron();
        };
        begin(p);
    }

    public static void begin(Phase p) {
        Phase cur = phase;
        if (cur != null && cur != p) Ec.safely("p3sim stop " + cur.name, cur::stop);
        previous = phase;
        phase = p;
        p.t = 0;
        p.start();
    }

    public static void end() {
        com.teslamaps.adm.sim.run.Runs.stop();
        Practice.exit();
        stop();
        Sim.clearEntities();
        Blocks.restoreAll();
        Devices.showIdle();
        resetI4Solver();
        Party.clear();
        PartyClient.send("splitsStop");
        PartyClient.send("termStart", 0);
    }

    public static void tick(MinecraftServer server) {
        Sim.clearActor();
        serverTick++;
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) pl.connection.send(new ClientboundPingPacket(-(PING_BASE + serverTick)));
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            if (pl.getFoodData().getFoodLevel() != 20) { pl.getFoodData().setFoodLevel(20); pl.getFoodData().setSaturation(5f); }
        }
        if (!later.isEmpty()) {
            List<Later> due = new ArrayList<>();
            for (Later l : later) if (l.at <= serverTick) due.add(l);
            later.removeAll(due);
            for (Later l : due) if (l.epoch == epoch) Ec.safely("p3sim " + l.what, l::go);
        }
        if (!timed.isEmpty()) {
            long nowNs = System.nanoTime();
            List<Timed> due = new ArrayList<>();
            for (Timed tm : timed) if (tm.dueNs <= nowNs) due.add(tm);
            due.sort((a, b) -> Long.compare(a.dueNs, b.dueNs));
            timed.removeAll(due);
            for (Timed tm : due) if (tm.epoch == epoch) Ec.safely("p3sim " + tm.what, tm::go);
        }
        long now = System.nanoTime();
        for (ServerPlayer pl : Sim.players()) {
            ArrayDeque<Pair<Long, Vec3>> h = posHistories.computeIfAbsent(pl.getUUID(), k -> new ArrayDeque<>());
            h.addLast(Pair.of(now, pl.position()));
            while (h.size() > 40) h.removeFirst();
        }
        Ec.safely("ads run", () -> com.teslamaps.adm.sim.run.Runs.tick(serverTick));
        Ec.safely("p3sim party", PartyServer::tick);
        Ec.safely("p3sim blocks", Blocks::tick);
        Ec.safely("p3sim terminals", Terminals::tick);
        Ec.safely("p3sim items", SimItems::tick);
        Sim.forEachPlayer(pl -> Ec.safely("p3sim bows", Bows::tick));
        Sim.forEachPlayer(pl -> { if (P3Sim.lava()) Ec.safely("p3sim lava", () -> Lava.tick(pl)); });
        Phase p = phase;
        if (p == null) { Recorder.finish(); SimItems.flushMotion(); return; }
        if (p instanceof P4Necron) Recorder.finish();
        Ec.safely("p3sim " + p.name, p::tick);
        p.t++;
        if (!(p instanceof P4Necron)) Ec.safely("p3sim recorder", () -> Recorder.tick(serverTick));
        SimItems.flushMotion();
    }
}
