// Ported from ADM's p3sim/clear/puzzles/WorldPuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.Station;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.run.api.Audience;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.Pair;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The common part of the easy and medium puzzles: a puzzle read from, and built into, the room's blocks in the room's own frame.
 * activate reads the room and plans; wake makes what the room shows a few ticks after a player first steps in. Timed steps go through after.
 */
public abstract class WorldPuzzle implements Puzzle {
    private final String kind;
    private final PuzzleContext context;

    protected WorldPuzzle(String kind, PuzzleContext context) {
        this.kind = kind;
        this.context = context;
    }

    @Override public final String kind() { return kind; }

    @Override public final PuzzleContext context() { return context; }

    /** True once activate has built the room's puzzle state. */
    public boolean activated = false;

    private final List<Entity> entities = new ArrayList<>();

    /** True once wake has run (or the room was already solved when it was built). */
    public boolean awake = false;

    /** The server tick a player was first seen in the room, or -1 while nobody has been. */
    public int enteredAt = -1;

    private static final class Timer {
        final int at;
        final String tag;
        final Runnable what;

        Timer(int at, String tag, Runnable what) {
            this.at = at;
            this.tag = tag;
            this.what = what;
        }
    }

    private final List<Timer> timers = new ArrayList<>();

    protected Run run() { return context.run; }

    protected Placement placement() { return context.room.placement; }

    protected int now() { return run().serverTick; }

    /** The sentence after the player's name in `PUZZLE SOLVED!`, or null when Hypixel says nothing for this puzzle. */
    public String solvedPhrase() { return null; }

    /** Whether a failure prints `PUZZLE FAIL!` (the Quiz speaks through its statue instead). */
    public boolean printsFailLine() { return true; }

    /** Ticks the room turns GREEN before the puzzle counts as Completed; negative = the other way. */
    public int greenLeadTicks() { return 0; }

    /** Full control over when the solve's three effects land. Null derives them from greenLeadTicks. */
    public SolveTiming solveTiming() { return null; }

    /** Whether the module sends the reward chest's cherry-log signal when the room turns green. */
    public boolean rewardSignalAtGreen() { return true; }

    /** Whether the module counts the room's one secret (its reward chest) when the room turns green. */
    public boolean countsRewardAsSecret() { return true; }

    /** Ticks after a player first steps into the room until wake runs. */
    public int wakeDelayTicks() { return 0; }

    /** Makes what the room shows once it is entered. Called once; not for a room that is already solved. */
    protected void wake() {}

    /** A secret of this room was found (a chest opened by a player). */
    public void onSecretFound(int x, int y, int z, UUID by) {}

    /** The block positions whose chest-open signal Odin listens for. */
    public List<WorldPos> rewardSpots() { return List.of(); }

    /** The world as it is once the puzzle is solved. Called at the solve and again after a savepoint restore. */
    public void applySolved() {}

    /** Builds the puzzle into the world. Called once, when a player is near. */
    protected abstract void build();

    /** Removes what build made in the world that is not a block put back by the region (entities). */
    protected void teardown() {}

    /** An arrow landed at the block (x, y, z) of this puzzle's room (world coordinates). */
    public void onArrowHit(int x, int y, int z, UUID by) {}

    /** A left click on an entity of this puzzle (a blaze). True = consumed. */
    public boolean onAttack(UUID player, Entity entity) { return false; }

    @Override public void onRunStart() {}

    public void activate() {
        if (activated) return;
        activated = true;
        try {
            build();
            if (status() == PuzzleStatus.SOLVED) { awake = true; applySolved(); }
        } catch (Throwable t) {
            Ec.LOG.error("[ads] puzzle " + kind + " in room " + context.room.name + " failed to build", t);
        }
    }

    /** For the dev command: builds the room and wakes it at once, whatever the distance and the clock. */
    public void wakeNow() {
        activate();
        if (!awake && activated) {
            awake = true;
            try { wake(); } catch (Throwable t) { Ec.LOG.error("[ads] puzzle " + kind + " in room " + context.room.name + " failed to wake", t); }
        }
    }

    /** A player stands in the room at server tick; the first call starts the wake-up clock. */
    public void noteEntered(int tick) { if (enteredAt < 0) enteredAt = tick; }

    /** Called by the module every server tick for a built puzzle: wakes the room when its time has come, then runs the due steps. */
    public void pump(int tick) {
        if (!awake && enteredAt >= 0 && tick >= enteredAt + wakeDelayTicks()) {
            awake = true;
            try { wake(); } catch (Throwable t) { Ec.LOG.error("[ads] puzzle " + kind + " in room " + context.room.name + " failed to wake", t); }
        }
        if (timers.isEmpty()) return;
        List<Timer> due = new ArrayList<>();
        for (Timer t : timers) if (t.at <= tick) due.add(t);
        if (due.isEmpty()) return;
        timers.removeAll(new HashSet<>(due));
        due.sort((a, b) -> Integer.compare(a.at, b.at));
        for (Timer t : due) {
            try { t.what.run(); } catch (Throwable e) { Ec.LOG.error("[ads] puzzle " + kind + " timed step failed", e); }
        }
    }

    /** Runs what ticks server ticks from now (at once for 0 or less). tag lets cancelTimers drop a group of steps. */
    protected void after(int ticks, String tag, Runnable what) {
        if (ticks <= 0) { what.run(); return; }
        timers.add(new Timer(now() + ticks, tag, what));
    }

    protected void after(int ticks, Runnable what) { after(ticks, "", what); }

    /** Drops every waiting timed step with this tag. */
    protected void cancelTimers(String tag) { timers.removeIf(t -> t.tag.equals(tag)); }

    /** Forgets the built state and removes the puzzle's entities (end of run, savepoint restore). */
    public void deactivate() {
        if (!activated) return;
        try { teardown(); } catch (Throwable t) { Ec.LOG.error("[ads] puzzle " + kind + " teardown failed", t); }
        for (Entity e : entities) {
            try { e.discard(); } catch (Throwable ignored) {}
        }
        entities.clear();
        timers.clear();
        activated = false;
        awake = false;
        enteredAt = -1;
    }

    @Override public JsonObject snapshot() { return null; }

    @Override public void restore(JsonObject data) {}

    // ---- helpers

    /** The world position of the room-frame cell (x, y, z); null without a room pack. */
    protected WorldPos world(int x, int y, int z) {
        Placement p = placement();
        return p == null ? null : p.toWorld(x, y, z);
    }

    /** The room-frame (x, z) of a world column, or null outside the room's box. */
    protected Pair<Integer, Integer> local(int wx, int wz) {
        Placement p = placement();
        return p == null ? null : p.toCanonical(wx, wz);
    }

    protected BlockState stateAt(int x, int y, int z) {
        WorldPos w = world(x, y, z);
        return w == null ? null : RunBlocks.get(w.x, w.y, w.z);
    }

    /** Whether the room-frame cell is air (an unreadable cell counts as air). */
    protected boolean isAirAt(int x, int y, int z) {
        BlockState s = stateAt(x, y, z);
        return s == null || s.isAir();
    }

    protected boolean setAt(int x, int y, int z, BlockState state) {
        WorldPos p = world(x, y, z);
        if (p == null) return false;
        return RunBlocks.set(p.x, p.y, p.z, state);
    }

    protected boolean setWorld(WorldPos p, BlockState state) { return RunBlocks.set(p.x, p.y, p.z, state); }

    protected void solve(UUID by) { context.host.reportSolved(this, by); }

    protected void fail(UUID by, String reason) { context.host.reportFailed(this, by, reason); }

    protected String nameOf(UUID player) { return PuzzlesModule.nameOf(run(), player); }

    /** A server player by UUID. */
    protected ServerPlayer playerOf(UUID id) {
        for (ServerPlayer p : Sim.players()) if (p.getUUID().equals(id)) return p;
        return null;
    }

    /** The human players standing inside this room's tiles, as server players. */
    protected List<ServerPlayer> playersInRoom() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Sim.players()) {
            RoomRecord r = run().state.roomAt(p.getX(), p.getZ());
            if (r != null && r.index() == context.room.index) out.add(p);
        }
        return out;
    }

    protected void say(String text) { Services.channels().chat(text); }

    /** A chat line for one player only. */
    protected void sayTo(UUID player, String text) { Services.channels().chat(text, new Audience.Only(player)); }

    /** The chest-open signal Odin listens for, at a world position, to every player. */
    protected void lidSignal(WorldPos p) { PuzzlesModule.sendRewardSignal(new BlockPos(p.x, p.y, p.z)); }

    /** A block state written as in the room packs, turned for this room's placement. */
    protected BlockState roomState(String s) {
        Placement p = placement();
        return PuzzleBlocks.state(s, p != null ? p.quarterTurns() : 0);
    }

    /** A stand with a name above it; marker gives it no hitbox; tagged so the module can tell whose it is. */
    protected ArmorStand spawnStand(String name, double x, double y, double z, boolean invisible, boolean marker) {
        ArmorStand s = new ArmorStand(EntityTypes.ARMOR_STAND, Sim.level());
        s.setInvisible(invisible);
        s.setNoGravity(true);
        s.setPermanentlyInvulnerable(true);
        s.setSilent(true);
        if (marker) Station.setMarker(s);
        s.setCustomName(Sim.legacy(name));
        s.setCustomNameVisible(true);
        s.snapTo(x, y, z, 0f, 0f);
        track(s);
        return Sim.spawn(s);
    }

    protected ArmorStand spawnStand(String name, double x, double y, double z, boolean invisible) { return spawnStand(name, x, y, z, invisible, true); }

    protected ArmorStand spawnStand(String name, double x, double y, double z) { return spawnStand(name, x, y, z, true, true); }

    /** Remembers an entity this puzzle made so deactivate removes it, and marks whose it is. */
    protected <T extends Entity> T track(T e) {
        e.addTag(TAG);
        e.addTag(TAG_ROOM + context.room.index);
        entities.add(e);
        return e;
    }

    protected void forget(Entity e) {
        entities.remove(e);
        try { e.discard(); } catch (Throwable ignored) {}
    }

    protected BlockPos center(WorldPos p) { return new BlockPos(p.x, p.y, p.z); }

    /** On every entity a puzzle makes. */
    public static final String TAG = "adspuzzle";
    /** Followed by the room index. */
    public static final String TAG_ROOM = "adspuzzle:room:";
}
