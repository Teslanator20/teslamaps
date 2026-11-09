// Ported from ADM's p3sim/clear/puzzles/BlazePuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomSecret;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Higher Or Lower in the world, as the recorded Hypixel rooms build it. A few ticks after the first player steps in, ten blazes appear in
 * a spiral (BlazeLayout), each with a name stand Odin's blaze solver reads, and a chest on the pillar. The first correct kill sets the
 * chest moving (BlazeRail); the room turns green 244 ticks after that kill and counts as Completed at the last kill. A kill out of order
 * fails it until the Architect's First Draft brings the same ten back. The room's reward chest is the room's own secret chest.
 */
public class BlazePuzzle extends WorldPuzzle {
    private static final class Foe {
        final int hp;
        final Blaze body;
        final ArmorStand tag;
        boolean dead;

        Foe(int hp, Blaze body, ArmorStand tag) {
            this.hp = hp;
            this.body = body;
            this.tag = tag;
            this.dead = false;
        }
    }

    private BlazeLogic logic = null;
    private List<Foe> foes = new ArrayList<>();
    private List<Integer> hps = List.of();
    private UUID lastKiller = null;
    private boolean higher = false;
    private BlazeRail rail = new BlazeRail(false);

    /** The server tick of the first correct kill, or -1 while the chest has not started to move. */
    private int rideStart = -1;

    /** How many moves the chest has made. */
    private int railStep = 0;

    /** The room's real chest (the pack's one secret) is where the reward is; null until the room is built. */
    private WorldPos rewardChest = null;

    public BlazePuzzle(PuzzleContext context) { super(PuzzleKinds.BLAZE, context); }

    @Override public int wakeDelayTicks() { return WAKE_DELAY_TICKS; }

    @Override public boolean rewardSignalAtGreen() { return false; }

    @Override public boolean countsRewardAsSecret() { return false; }

    @Override public List<WorldPos> rewardSpots() { return rewardChest == null ? List.of() : List.of(rewardChest); }

    /** Completed at the last kill; the green mark follows when the ride is over (at once if it already ended). */
    @Override public SolveTiming solveTiming() {
        int green = rideStart >= 0 ? Math.max(rail.greenTick(rideStart) - now(), 0) : 0;
        return new SolveTiming(0, green, 0);
    }

    /** For tests: the chest set off on tick, as if the first correct kill had just happened (no world is touched). */
    public void rideStartedAt(int tick) { rideStart = tick; }

    @Override protected void build() {
        RoomDesign design = context().room.design;
        if (design == null) return;
        higher = "Blaze_Upper".equals(design.family);
        rail = new BlazeRail(higher);
        rewardChest = null;
        for (RoomSecret s : design.secrets) {
            if ("chest".equals(s.type)) { rewardChest = world(s.pos.x, s.pos.y, s.pos.z); break; }
        }
        KRandom rnd = run().subRandom("blaze:" + context().room.index);
        List<Integer> rec = recordedHps();
        hps = rec != null ? rec : BlazeLogic.drawHps(rnd, BlazeLogic.COUNT);
        logic = new BlazeLogic(hps, higher);
    }

    /** A replay's blazes: the HP of the recorded blaze on each cell, or null when the recording does not have one on every cell. */
    private List<Integer> recordedHps() {
        Function<Run, List<double[]>> f = replayBlazes;
        if (f == null) return null;
        List<double[]> rec = f.apply(run());
        if (rec == null) return null;
        List<int[]> cells = new ArrayList<>();
        for (int k = 0; k < BlazeLogic.COUNT; k++) {
            BlazeLayout.Cell c = BlazeLayout.cell(k, higher);
            WorldPos w = world(c.x, c.y, c.z);
            if (w == null) return null;
            cells.add(new int[]{w.x, w.y, w.z});
        }
        return BlazeLogic.recordedHps(cells, rec);
    }

    /** The room is entered: the blazes and the chest appear. A failed room (restored from a savepoint) waits for the draft. */
    @Override protected void wake() {
        if (status() == PuzzleStatus.FAILED) return;
        railReset();
        spawnAll();
    }

    // ---- the blazes

    private void spawnAll() {
        for (Foe f : foes) { forget(f.tag); forget(f.body); }
        foes.clear();
        for (int k = 0; k < hps.size(); k++) {
            BlazeLayout.Cell c = BlazeLayout.cell(k, higher);
            WorldPos w = world(c.x, c.y, c.z);
            if (w == null) continue;
            Blaze body = new Blaze(EntityTypes.BLAZE, Sim.level());
            body.setNoAi(true);
            body.setNoGravity(true);
            body.setPersistenceRequired();
            AttributeInstance maxHp = body.getAttribute(Attributes.MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(1.0);
            body.setHealth(1f);
            // Hypixel puts a blaze on the block corner, not the block centre.
            body.snapTo((double) w.x, (double) w.y, (double) w.z, 0f, 0f);
            track(body);
            Sim.spawn(body);
            ArmorStand tag = spawnStand(BlazeLogic.nameOf(hps.get(k)), (double) w.x, w.y + BlazeLayout.TAG_HEIGHT, (double) w.z);
            foes.add(new Foe(hps.get(k), body, tag));
        }
    }

    private void process(Foe foe, UUID by) {
        if (foe.dead) return;
        foe.dead = true;
        // The body plays its death; it and its name stand go 2 ticks later.
        after(DEATH_TICKS, TAG_FOE, () -> { forget(foe.tag); forget(foe.body); });
        BlazeLogic l = logic;
        if (l == null) return;
        if (status() != PuzzleStatus.UNSOLVED) return;
        boolean ok = l.kill(foe.hp);
        UUID who = by;
        if (who == null) who = lastKiller;
        if (who == null) {
            List<ServerPlayer> in = playersInRoom();
            if (!in.isEmpty()) who = in.get(0).getUUID();
        }
        if (!ok) { failRide(); fail(who, "killed a Blaze in the wrong order!"); }
        else {
            if (rideStart < 0) startRide();
            if (l.solved()) solve(who);
        }
    }

    @Override public void onTick() {
        for (Foe f : new ArrayList<>(foes)) if (!f.dead && (!f.body.isAlive() || f.body.isRemoved())) process(f, null);
    }

    @Override public boolean onAttack(UUID player, Entity entity) {
        Foe f = null;
        for (Foe it : foes) if (it.body == entity || it.tag == entity) { f = it; break; }
        if (f == null) return false;
        if (f.dead) return true;
        kill(f, player);
        return true;
    }

    private void kill(Foe f, UUID by) {
        lastKiller = by;
        // Let the blaze take the hit so it plays its hurt and death; whatever happens, the kill counts.
        ServerLevel level = Sim.level();
        try { f.body.hurtServer(level, level.damageSources().generic(), 1000f); } catch (Throwable ignored) {}
        if (f.body.isAlive()) {
            try { f.body.kill(level); } catch (Throwable ignored) {}
        }
        process(f, by);
    }

    /** Whether the blaze of hp is standing (false before the room woke, and once it died). */
    public boolean standing(int hp) {
        for (Foe it : foes) if (!it.dead && it.hp == hp) return true;
        return false;
    }

    /** A replay: a teammate killed the blaze of hp (the room wakes first if nobody has stepped in yet). False when it is not standing. */
    public boolean killRecorded(int hp, UUID by) {
        if (!awake) wakeNow();
        Foe f = null;
        for (Foe it : foes) if (!it.dead && it.hp == hp) { f = it; break; }
        if (f == null) return false;
        kill(f, by);
        return true;
    }

    // ---- the rail

    private BlockState railState(BlazeRail.Cell c) {
        return switch (c) {
            case CHEST -> PuzzleBlocks.parse(CHEST_STATE);
            case BARS -> PuzzleBlocks.parse(BARS_STATE);
            case AIR -> Blocks.AIR.defaultBlockState();
        };
    }

    private void setRailCell(int y, BlazeRail.Cell c) {
        BlockState s = railState(c);
        if (s == null) throw new NullPointerException("rail state " + c);
        setAt(BlazeLayout.CENTRE, y, BlazeLayout.CENTRE, s);
    }

    /** The whole rail as it stands after step moves. */
    private void railPaint(int step) { for (int y : rail.cells()) setRailCell(y, rail.cellAt(step, y)); }

    /** The rail as a fresh room has it: the chest at its first cell, the bars of a Higher rail above it, no trail. */
    private void railReset() { cancelTimers(TAG_RAIL); rideStart = -1; railStep = 0; railPaint(0); }

    /** The first correct kill: the chest starts to move, one block every 5 ticks, the first move on this tick. */
    private void startRide() {
        rideStart = now();
        railStep = 0;
        for (int i = 0; i < BlazeRail.STEPS; i++) {
            int step = i;
            after(BlazeRail.STEP_TICKS * i, TAG_RAIL, () -> moveRail(step));
        }
    }

    /** Move i: the chest leaves its cell (a bar behind it for Lower, air for Higher) and enters the next. */
    private void moveRail(int i) {
        setRailCell(rail.yAt(i), rail.cellAt(i + 1, rail.yAt(i)));
        setRailCell(rail.yAt(i + 1), BlazeRail.Cell.CHEST);
        railStep = i + 1;
    }

    /** A wrong kill: the chest is taken off the rail where it stands and stops. */
    private void failRide() {
        cancelTimers(TAG_RAIL);
        setRailCell(rail.yAt(railStep), BlazeRail.Cell.AIR);
        // Every blaze that is left vanishes in one tick, 2 ticks after the fail.
        after(WIPE_TICKS, TAG_FOE, () -> {
            for (Foe f : foes) { forget(f.tag); forget(f.body); }
            foes.clear();
        });
    }

    // ---- secrets

    /** A player opened the room's chest: Odin's chest-open signal, then the five harp notes of the jingle. */
    @Override public void onSecretFound(int x, int y, int z, UUID by) {
        WorldPos chest = rewardChest;
        if (chest == null) return;
        if (x != chest.x || y != chest.y || z != chest.z) return;
        // Odin takes the signal as the end of the puzzle, so it waits for the last kill.
        if (status() != PuzzleStatus.SOLVED) return;
        lidSignal(chest);
        Vec3 at = new Vec3(chest.x + 0.5, chest.y + 0.5, chest.z + 0.5);
        for (int i = 0; i < ChestJingle.PITCHES.length; i++) {
            float pitch = ChestJingle.PITCHES[i];
            after(ChestJingle.FIRST_NOTE + ChestJingle.NOTE_GAP * i, TAG_JINGLE, () -> Sim.sound(SoundEvents.NOTE_BLOCK_HARP, 1.0f, pitch, at));
        }
    }

    // ---- solved and reset

    /** The room as it stands once solved: the chest at the end of the rail. */
    @Override public void applySolved() { railPaint(BlazeRail.STEPS); }

    @Override public void onReset() {
        cancelTimers(TAG_FOE);
        for (Foe f : foes) { forget(f.tag); forget(f.body); }
        foes.clear();
        if (logic != null) logic.reset();
        // A fresh set of the same ten appears one tick after the item is used, and the rail goes back to its start in that same tick.
        after(RESET_TICKS, TAG_FOE, () -> { railReset(); spawnAll(); });
    }

    /** Set by a replay: the recorded blazes of the run as (x, y, z, HP) of each body, matched to the cells by position. */
    public static volatile Function<Run, List<double[]>> replayBlazes = null;

    /** Measured: the blazes and the chest appear 7 ticks (median) after the first step into the room. */
    public static final int WAKE_DELAY_TICKS = 7;

    /** A blaze and its name stand go this long after it dies. */
    public static final int DEATH_TICKS = 2;

    /** The rest of the blazes go this long after a wrong kill. */
    public static final int WIPE_TICKS = 2;

    /** A reset brings the blazes back this long after the item is used. */
    public static final int RESET_TICKS = 1;

    /** The rail chest faces south in the world whichever way the room is turned (101 of 101 recorded placements). */
    private static final String CHEST_STATE = "minecraft:chest[facing=south,type=single,waterlogged=false]";
    private static final String BARS_STATE = "minecraft:iron_bars[east=true,north=true,south=true,waterlogged=false,west=true]";

    private static final String TAG_FOE = "foe";
    private static final String TAG_RAIL = "rail";
    private static final String TAG_JINGLE = "jingle";
}
