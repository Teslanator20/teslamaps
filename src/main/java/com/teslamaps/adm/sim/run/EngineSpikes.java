// Ported from ADM's p3sim/run/EngineSpikes.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Arena;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class EngineSpikes {
    private EngineSpikes() {}

    private static String f(String fmt, double v) { return String.format(Locale.ROOT, fmt, v); }

    public static void say(String text) {
        Ec.LOG.info("[ads spike] " + text);
        MinecraftServer s = SimServer.server;
        if (s == null) return;
        s.execute(() -> {
            if (SimServer.server == s) Ec.safely("ads spike say", () -> Sim.asPlayer(null, () -> { Sim.everyone(() -> { Sim.note(text); }); }));
        });
    }

    // ---- marks (time to playable) ----

    private static final LinkedHashMap<String, Long> marks = new LinkedHashMap<>();
    private static volatile boolean markingWorld = false;

    public static void beginWorldClock() { synchronized (marks) { marks.clear(); marks.put("command", System.nanoTime()); markingWorld = true; } }

    public static void mark(String name) { if (markingWorld) synchronized (marks) { marks.putIfAbsent(name, System.nanoTime()); } }

    public static List<String> markLines() {
        synchronized (marks) {
            List<String> keys = new ArrayList<>(marks.keySet());
            List<String> out = new ArrayList<>();
            for (int i = 0; i + 1 < keys.size(); i++) {
                String a = keys.get(i), b = keys.get(i + 1);
                out.add("  " + a + " -> " + b + ": " + (marks.get(b) - marks.get(a)) / 1_000_000 + " ms");
            }
            if (keys.size() >= 2) {
                String last = keys.get(keys.size() - 1);
                out.add("  total, command -> " + last + ": " + (marks.get(last) - marks.get(keys.get(0))) / 1_000_000 + " ms");
            }
            return out;
        }
    }

    public static void endWorldClock() { markingWorld = false; }

    // ---- the per-tick driver ----

    private static DifficultyAudit difficultyAudit = null;
    private static CostRun cost = null;
    private static LagWatch lagWatch = null;

    public static volatile CostOptions costOnJoin = null;

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        DifficultyAudit d = difficultyAudit;
        if (d != null) { if (d.tick(level)) difficultyAudit = null; }
        CostRun c = cost;
        if (c != null) { if (c.tick(server, level)) cost = null; }
        LagWatch w = lagWatch;
        if (w != null) { if (w.tick()) lagWatch = null; }
    }

    public static void joined(ServerPlayer player) {
        mark("join");
        CostOptions opts = costOnJoin;
        if (opts == null) return;
        costOnJoin = null;
        cost = new CostRun(opts, 100);
        Ec.LOG.info("[ads spike] cost: the world is up for " + player.getGameProfile().name() + "; measuring in 5 s");
    }

    // ---- client-side readings ----

    private static volatile int fpsWindow = 0;
    private static final ArrayList<Integer> fpsSamples = new ArrayList<>();
    private static boolean waitingForPlayable = false;

    public static void expectPlayable() { waitingForPlayable = true; }

    public static void clientTick(Minecraft mc) {
        if (waitingForPlayable && mc.level != null && mc.player != null && mc.screen == null && markingWorld) { waitingForPlayable = false; mark("playable"); }
        if (fpsWindow > 0) { synchronized (fpsSamples) { fpsSamples.add(mc.getFps()); } fpsWindow--; }
    }

    public static void collectFps(int ticks) { synchronized (fpsSamples) { fpsSamples.clear(); } fpsWindow = ticks; }

    public static boolean fpsDone() { return fpsWindow <= 0; }

    public static String fpsStats() {
        synchronized (fpsSamples) {
            if (fpsSamples.isEmpty()) return "no frames sampled";
            List<Integer> s = new ArrayList<>(fpsSamples);
            Collections.sort(s);
            double sum = 0.0;
            for (int v : s) sum += v;
            return "mean " + f("%.0f", sum / s.size()) + " fps, 1% low " + s.get((int) (s.size() * 0.01)) + " fps, min " + s.get(0) + " fps over " + s.size() + " ticks";
        }
    }

    // ---- difficulty ----

    public static void difficulty(MinecraftServer server, Difficulty to) { difficulty(server, to, 400); }

    public static void difficulty(MinecraftServer server, Difficulty to, int ticks) {
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        server.setDifficulty(to, true);
        say("Difficulty " + before.name() + " -> " + to.name() + ". Watching the sim's mobs for " + ticks / 20 + " s.");
        DifficultyAudit a = new DifficultyAudit(ticks);
        a.begin(level);
        difficultyAudit = a;
    }

    private static final class DifficultyAudit {
        private final int total;
        private int n = 0;
        private final HashMap<UUID, String> seen = new HashMap<>();
        private final HashSet<UUID> targeted = new HashSet<>();
        private float healthStart = 0f;
        private float healthMin = Float.MAX_VALUE;

        DifficultyAudit(int total) { this.total = total; }

        private List<Entity> simEntities(ServerLevel level) {
            ArrayList<Entity> out = new ArrayList<>();
            for (Entity it : level.getAllEntities()) if (it.entityTags().contains(Sim.TAG)) out.add(it);
            return out;
        }

        void begin(ServerLevel level) {
            for (Entity e : simEntities(level)) seen.put(e.getUUID(), e.getType().toShortString());
            ServerPlayer host = Sim.host();
            healthStart = host != null ? host.getHealth() : 0f; healthMin = healthStart;
        }

        boolean tick(ServerLevel level) {
            n++;
            List<Entity> now = simEntities(level);
            for (Entity e : now) {
                seen.putIfAbsent(e.getUUID(), e.getType().toShortString());
                if (e instanceof Mob m && m.getTarget() != null) targeted.add(e.getUUID());
            }
            ServerPlayer host = Sim.host();
            if (host != null) healthMin = Math.min(healthMin, host.getHealth());
            if (n < total) return false;
            Set<UUID> alive = new HashSet<>();
            for (Entity e : now) alive.add(e.getUUID());
            LinkedHashMap<String, Integer> gone = new LinkedHashMap<>();
            for (Map.Entry<UUID, String> en : seen.entrySet()) if (!alive.contains(en.getKey())) gone.merge(en.getValue(), 1, Integer::sum);
            int mobs = 0;
            for (Entity e : now) if (e instanceof Mob) mobs++;
            int goneSum = 0;
            for (int v : gone.values()) goneSum += v;
            StringBuilder list = new StringBuilder();
            for (Map.Entry<String, Integer> en : gone.entrySet()) {
                if (list.length() > 0) list.append(", ");
                list.append(en.getKey()).append(" x").append(en.getValue());
            }
            say("Difficulty audit, " + total / 20 + " s: " + seen.size() + " sim entities seen (" + mobs + " mobs now), " + goneSum + " gone" +
                    (gone.isEmpty() ? "" : " (" + list + ")") +
                    ", " + targeted.size() + " mobs had a target. Your health " + f("%.1f", healthStart) + " -> lowest " + f("%.1f", healthMin) + ".");
            if (seen.isEmpty()) say("Nothing of the sim was in the world to watch: start a boss phase (/ads p1) and run this again.");
            return true;
        }
    }

    // ---- cost ----

    public static class CostOptions {
        public final boolean pin;
        public final boolean quick;

        public CostOptions() { this(true); }

        public CostOptions(boolean pin) { this(pin, false); }

        public CostOptions(boolean pin, boolean quick) { this.pin = pin; this.quick = quick; }
    }

    public static void costNow(CostOptions opts) { cost = new CostRun(opts, 20); say("Cost measurements start in a second. Stand still in a room."); }

    private static final class CostRun {
        private enum Step { WAIT, LOAD, BASE, M300, M750, VIEW, RESYNC, DONE }

        private final CostOptions opts;
        private long loadStart = 0L;
        private Step step = Step.WAIT;
        private int left;
        private final ArrayList<Entity> spawned = new ArrayList<>();
        private final ArrayList<String> lines = new ArrayList<>();
        private final int window = 100;
        private final int settle;

        CostRun(CostOptions opts, int afterTicks) {
            this.opts = opts;
            this.left = afterTicks;
            this.settle = opts.quick ? 20 : 40;
        }

        private String mspt(MinecraftServer server) {
            List<Long> t = new ArrayList<>();
            for (long v : server.getTickTimesNanos()) if (v > 0) t.add(v);
            Collections.sort(t);
            if (t.isEmpty()) return "no samples";
            double sum = 0.0;
            for (long v : t) sum += v;
            double avg = sum / t.size();
            return "mean " + f("%.2f", avg / 1e6) + " ms, p95 " + f("%.2f", t.get(Math.min((int) (t.size() * 0.95), t.size() - 1)) / 1e6)
                    + " ms, max " + f("%.2f", t.get(t.size() - 1) / 1e6) + " ms (50 ms is a full tick)";
        }

        boolean tick(MinecraftServer server, ServerLevel level) {
            ServerPlayer player = Sim.host();
            if (player == null) return false;
            switch (step) {
                case WAIT -> {
                    if (--left <= 0) {
                        if (opts.pin) { ViewPin.pin(); server.getPlayerList().setViewDistance(ViewPin.DISTANCE); server.getPlayerList().setSimulationDistance(ViewPin.DISTANCE); }
                        boolean stub = false;
                        for (WorldRegion r : WorldRegions.regions()) if (r.name().equals(StubDungeon.NAME)) { stub = true; break; }
                        if (stub) {
                            double c = DungeonFrame.tileCentre(2) + 0.5;
                            Sim.asPlayer(player, () -> { Sim.tp(player, c, StubDungeon.FLOOR_Y + 1.0, c, 0f, 0f); });
                        }
                        loadStart = System.nanoTime(); left = 20 * 60; step = Step.LOAD;
                    }
                }
                case LOAD -> {
                    int r = 70;
                    BlockPos here = BlockPos.containing(player.getX(), player.getY(), player.getZ());
                    int[][] offs = {{-r, -r}, {-r, r}, {r, -r}, {r, r}, {0, 0}};
                    boolean ready = true;
                    for (int[] o : offs) if (!level.isLoaded(here.offset(o[0], 0, o[1]))) { ready = false; break; }
                    if (ready || --left <= 0) {
                        lines.add("Chunks round you: " + (ready ? "loaded" : "NOT all loaded after 60 s") + " in " + (System.nanoTime() - loadStart) / 1_000_000 + " ms after the move.");
                        lines.add("View distance " + server.getPlayerList().getViewDistance() + ", simulation distance " + server.getPlayerList().getSimulationDistance() + ", "
                                + level.getChunkSource().getLoadedChunksCount() + " chunks loaded, difficulty " + server.getWorldData().getDifficulty().name() + ".");
                        left = settle + window; step = Step.BASE;
                    }
                }
                case BASE -> {
                    if (--left <= 0) {
                        lines.add("0 mobs: " + mspt(server) + ".");
                        spawnStatic(level, player, 300); lines.add("(spawned " + spawned.size() + " static mobs)");
                        left = settle + window; step = Step.M300;
                    }
                }
                case M300 -> {
                    if (--left <= 0) {
                        lines.add("300 static mobs: " + mspt(server) + ".");
                        spawnStatic(level, player, 450); lines.add("(now " + spawned.size() + " static mobs)");
                        left = settle + window; step = Step.M750;
                    }
                }
                case M750 -> {
                    if (--left <= 0) {
                        lines.add("750 static mobs: " + mspt(server) + ".");
                        for (Entity e : spawned) e.discard();
                        spawned.clear();
                        spawnView(level, player, 330, 100);
                        EngineSpikes.collectFps(opts.quick ? 100 : 200);
                        left = 1; step = Step.VIEW;
                    }
                }
                case VIEW -> {
                    if (EngineSpikes.fpsDone()) {
                        lines.add("430 entities in view (330 mobs, 100 armor stands): client " + EngineSpikes.fpsStats() + ". Server " + mspt(server) + ".");
                        for (Entity e : spawned) e.discard();
                        spawned.clear();
                        step = Step.RESYNC;
                    }
                }
                case RESYNC -> {
                    long t0 = System.nanoTime();
                    int fixed = Arena.resync(level);
                    lines.add("Arena.resync: " + (System.nanoTime() - t0) / 1_000_000 + " ms (" + fixed + " blocks fixed).");
                    step = Step.DONE;
                }
                case DONE -> {
                    String head = "Cost report (" + (opts.pin ? "distance pinned to " + ViewPin.DISTANCE : "the player's own distances") + "):";
                    EngineSpikes.say(head);
                    for (String l : lines) EngineSpikes.say("  " + l);
                    List<String> ml = EngineSpikes.markLines();
                    if (!ml.isEmpty()) { EngineSpikes.say("Time to playable:"); for (String l : ml) EngineSpikes.say(l); }
                    EngineSpikes.endWorldClock();
                    return true;
                }
            }
            return false;
        }

        private void spawnStatic(ServerLevel level, ServerPlayer around, int n) {
            int made = 0, tries = 0;
            while (made < n && tries < n * 30) {
                tries++;
                double x = around.getX() + KRandom.DEFAULT.nextDouble(-64.0, 64.0); double z = around.getZ() + KRandom.DEFAULT.nextDouble(-64.0, 64.0);
                BlockPos pos = BlockPos.containing(x, around.getY(), z);
                if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) continue;
                Zombie e = new Zombie(EntityType.ZOMBIE, level);
                e.setNoAi(true); e.setPersistenceRequired(); e.setSilent(true);
                e.snapTo(pos.getX() + 0.5, (double) pos.getY(), pos.getZ() + 0.5, KRandom.DEFAULT.nextFloat() * 360f, 0f);
                spawned.add(Sim.spawn(e)); made++;
            }
        }

        private void spawnView(ServerLevel level, ServerPlayer around, int mobs, int stands) {
            int made = 0, tries = 0;
            while (made < mobs + stands && tries < (mobs + stands) * 30) {
                tries++;
                BlockPos pos = BlockPos.containing(around.getX() + KRandom.DEFAULT.nextDouble(-13.0, 13.0), around.getY(), around.getZ() + KRandom.DEFAULT.nextDouble(-13.0, 13.0));
                if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) continue;
                Entity e;
                if (made < mobs) {
                    Zombie z = new Zombie(EntityType.ZOMBIE, level);
                    z.setNoAi(true); z.setPersistenceRequired(); z.setSilent(true);
                    e = z;
                } else {
                    ArmorStand a = new ArmorStand(EntityType.ARMOR_STAND, level);
                    a.setSilent(true); a.setNoGravity(true);
                    e = a;
                }
                e.snapTo(pos.getX() + 0.5, (double) pos.getY(), pos.getZ() + 0.5, KRandom.DEFAULT.nextFloat() * 360f, 0f);
                spawned.add(Sim.spawn(e)); made++;
            }
        }
    }

    // ---- lag ----

    public static void lag(LagPreset preset, int seconds) {
        LagRuntime.devPreset = preset;
        LagRuntime.stats.clear();
        PackSet set = Packs.current;
        double loss = LagPresets.lossPerMinute(preset, set != null && set.runstate != null && set.runstate.lag != null ? set.runstate.lag.tiers : null);
        say(preset == LagPreset.OFF ? "Lag layer off." : "Lag layer: " + preset.label + ", target " + f("%.1f", loss) + " ticks lost a minute. Collecting " + seconds + " s; the report follows. "
                + (LagRuntime.canForget() ? "" : "(The server's tick clock cannot be reached here, so lost ticks will be caught up in full.)"));
        lagWatch = preset != LagPreset.OFF && seconds > 0 ? new LagWatch(System.nanoTime() + seconds * 1_000_000_000L, loss) : null;
    }

    public static void lagReport(int seconds) { lagReport(seconds, null); }

    public static void lagReport(int seconds, Double target) {
        LagStats.Report r = LagRuntime.stats.report((double) seconds);
        if (r == null) { say("No ticks recorded yet."); return; }
        say("Ticks, last " + f("%.0f", r.seconds) + " s: " + r.ticks + " ran, " + f("%.1f", r.lostTicks) + " lost = " + f("%.1f", r.lostPerMinute()) + " a minute"
                + (target != null ? " (target " + f("%.1f", target) + ")" : "") + ".");
        say("Freezes of 100 ms or more: " + r.freezes.size() + ", longest " + f("%.0f", r.longestMs()) + " ms. Of the ticks they missed, " + f("%.0f", r.recoveredShare() * 100) + "% ran again in the bursts.");
        List<LagStats.Freeze> sorted = new ArrayList<>(r.freezes);
        sorted.sort((a, b) -> Double.compare(b.gapMs, a.gapMs));
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            LagStats.Freeze fr = sorted.get(i);
            say("  gap " + f("%.0f", fr.gapMs) + " ms (missed " + f("%.1f", fr.missedTicks) + "), then " + fr.burstTicks + " ticks "
                    + (fr.burstTicks > 0 ? f("%.1f", fr.burstMeanMs) + " ms apart" : "at the normal pace"));
        }
        List<LagStats.Freeze> big = new ArrayList<>();
        for (LagStats.Freeze fr : r.freezes) if (fr.gapMs >= 500) big.add(fr);
        if (big.isEmpty()) say("Freeze-then-burst: no freeze of 500 ms or more yet; run longer.");
        else {
            boolean all = true;
            int none = 0;
            for (LagStats.Freeze fr : big) { if (fr.burstTicks <= 0) all = false; if (fr.burstTicks == 0) none++; }
            say("Freeze-then-burst: " + (all ? "yes, every long freeze was followed by a burst" : "NO: " + none + " of " + big.size() + " long freezes had no burst") + ".");
        }
    }

    private static final class LagWatch {
        private final long atNs;
        private final double target;

        LagWatch(long atNs, double target) { this.atNs = atNs; this.target = target; }

        boolean tick() {
            if (System.nanoTime() < atNs) return false;
            lagReport(60, target);
            return true;
        }
    }
}
