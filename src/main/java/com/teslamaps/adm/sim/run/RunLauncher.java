// Ported from ADM's p3sim/run/RunLauncher.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.SimWorld;
import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.Pair;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * How a full run is started from the client. The world is made fresh on every open and the dungeon is written while its chunks
 * generate, so: plan, register the regions, THEN open (or rebuild) the world; pending holds the config across that gap.
 * 1. CLIENT: launch. 2. SERVER, once the player has joined: takePending + Runs.start.
 */
public final class RunLauncher {
    private RunLauncher() {}

    public static volatile RunConfig pending = null;

    /** The plan that goes with pending. */
    public static volatile DungeonPlan pendingPlan = null;

    /** The pack set the pending plan was made from, pinned until the run takes over or the launch is dropped. */
    private static PackSet pendingPacks = null;

    /** Set while a launch waits for the packs. */
    private static volatile boolean launching = false;

    /** The dungeon region registered for the pending launch. */
    private static String dungeonRegion = null;

    /** The layout and seed of the last run launched (client side). */
    public static volatile Pair<String, Long> lastLaunch = null;

    /** Starts a full run (client thread). True when accepted; every later step reports through reply. */
    public static boolean launch(RunConfig config, Consumer<String> reply) {
        Minecraft mc = Minecraft.getInstance();
        if (launching || pending != null) { reply.accept("A run is already starting."); return false; }
        if (P3Sim.inSim() && !P3Sim.localSim()) { reply.accept("Only the host can start a run."); return false; }
        launching = true;
        reply.accept("Loading the data packs...");
        Packs.loadAsync().whenComplete((set, err) -> mc.execute(() -> {
            try {
                if (err != null || set == null) {
                    reply.accept("The data packs could not be read: " + (err != null && err.getMessage() != null ? err.getMessage() : "unknown error"));
                    return;
                }
                String problem = begin(config, set, reply);
                if (problem != null) reply.accept(problem);
            } catch (Throwable t) {
                Ec.LOG.error("[ads] launching a run failed", t);
                reply.accept("Starting the run failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()) + ". The log has the details.");
                abort();
            } finally {
                launching = false;
            }
        }));
        return true;
    }

    /** Plans, registers the regions, remembers the launch and opens the world; a problem, or null. Client thread. */
    private static String begin(RunConfig config, PackSet set, Consumer<String> reply) {
        if (!set.canBuildDungeon()) {
            String joined = String.join(" ", set.userLines());
            return joined.isBlank() ? Packs.missingMessage(set.dir) : joined;
        }
        // lines about packs that loaded with problems, once
        set.userLines().forEach(reply);
        DungeonPlan.Result r = Services.layout().plan(config, set);
        DungeonPlan plan;
        if (r instanceof DungeonPlan.Result.Ok ok) plan = ok.plan;
        else if (r instanceof DungeonPlan.Result.Failed f) return f.failure.message;
        else return null;
        WorldRegion region = Services.layout().region(plan, set);
        if (region == null) return "The dungeon builder is not installed in this build, so a full run cannot be built.";
        registerDungeon(region);
        setPending(config, plan, set);
        reply.accept("Building " + plan.layoutId + " (seed " + config.seed + ")...");
        launchWorld();
        return null;
    }

    /** Registers the dungeon's region and the clipped arena region for the world about to open. */
    public static void registerDungeon(WorldRegion region) {
        WorldRegions.register(region);
        dungeonRegion = region.name();
        ArenaRegion.register();
    }

    /** A rebuild when you are in ADS already, else a plain open. */
    public static void launchWorld() {
        if (P3Sim.inSim()) SimWorld.rebuild(true); else SimWorld.open(true);
    }

    /** Drops a launch that will not happen: the pending config and plan, and its regions. */
    public static void abort() {
        // nothing was waiting: the regions in use belong to the running world
        if (pending == null && dungeonRegion == null) return;
        cancel();
        WorldRegions.unregister(ArenaRegion.NAME);
        if (dungeonRegion != null) WorldRegions.unregister(dungeonRegion);
        dungeonRegion = null;
    }

    public static void setPending(RunConfig config, DungeonPlan plan) { setPending(config, plan, null); }

    /** Engine: remembers what to start once the world is up. */
    public static synchronized void setPending(RunConfig config, DungeonPlan plan, PackSet packs) {
        dropPendingPacks();
        pending = config;
        pendingPlan = plan;
        if (packs != null) packs.pin();
        pendingPacks = packs;
        lastLaunch = Pair.of(plan.layoutId, config.seed);
    }

    /** Server thread, with takePending: the pack set, still pinned for the caller. */
    public static synchronized PackSet takePendingPacks() {
        PackSet p = pendingPacks;
        pendingPacks = null;
        return p;
    }

    private static synchronized void dropPendingPacks() {
        if (pendingPacks != null) pendingPacks.unpin();
        pendingPacks = null;
    }

    /** Engine, server thread: the config to start, once; null when the world was opened for the boss practice. */
    public static Pair<RunConfig, DungeonPlan> takePending() {
        RunConfig c = pending;
        DungeonPlan p = pendingPlan;
        pending = null;
        pendingPlan = null;
        // the world uses the regions now
        dungeonRegion = null;
        return c != null && p != null ? Pair.of(c, p) : null;
    }

    /** Forgets a pending launch. */
    public static synchronized void cancel() {
        pending = null;
        pendingPlan = null;
        dropPendingPacks();
    }
}
