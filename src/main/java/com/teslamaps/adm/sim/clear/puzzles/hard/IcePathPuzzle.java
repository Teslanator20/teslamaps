package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/IcePathPuzzle.kt

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;

/**
 * The Ice Path in the world: one silverfish with no mind of its own, moved a step each tick by IcePathModel. A hit (arrow or left click)
 * sends it sliding. Spawned when somebody is in the room and put back on its start cell after a fail.
 */
public class IcePathPuzzle implements Puzzle {
    private final PuzzleContext context;

    private IcePathModel model = null;
    private int floorY = IcePathLayout.DEFAULT_FLOOR_Y;
    private Fish fish = null;
    private int respawnAt = Integer.MIN_VALUE;
    private UUID lastHitter = null;
    private boolean solved = false;
    private final ChestReward chest;

    /** Tick the fish starts to slide, after a hit. */
    private int slideFrom = Integer.MIN_VALUE;

    private int fishGoneAt = Integer.MIN_VALUE;

    public IcePathPuzzle(PuzzleContext context) {
        this.context = context;
        this.chest = new ChestReward(context, ChestReward.ICE, this);
    }

    @Override public String kind() { return "ICE_PATH"; }

    @Override public PuzzleContext context() { return context; }

    private Placement placement() { return context.room.placement; }

    /** A silverfish that stays where it is put: not saved with the world. */
    private static class Fish extends Silverfish {
        Fish(Level level) { super(EntityType.SILVERFISH, level); }

        @Override public boolean shouldBeSaved() { return false; }
    }

    @Override
    public void onRunStart() {
        solved = false;
        chest.reset();
        slideFrom = Integer.MIN_VALUE;
        discardFish();
        RoomDesign design = context.room.design;
        RoomVolume volume = design != null && context.run.packs.rooms != null ? context.run.packs.rooms.volumeFor(design, context.room.variantId) : null;
        if (volume == null) return;
        IcePathLayout layout = IcePathLayout.read(volume);
        floorY = layout.floorY;
        model = layout.model.get();
        Placement pl = placement();
        if (pl != null) HardBlocks.apply(pl, List.of(chest.placeEdit()));
    }

    @Override
    public boolean onInteract(UUID player, int x, int y, int z) { return chest.onInteract(player, x, y, z); }

    public boolean ownsFish(Entity e) { return fish != null && fish == e; }

    public boolean ownsFishId(int id) { return fish != null && fish.getId() == id; }

    /** A left click on the fish by player. */
    public void onFishHit(ServerPlayer player) { push(player, 0); }

    /** An arrow of player hit the fish: the slide starts SLIDE_DELAY_TICKS later. */
    public void onFishArrow(ServerPlayer player) { push(player, SLIDE_DELAY_TICKS); }

    private void push(ServerPlayer player, int delay) {
        IcePathModel m = model;
        if (m == null) return;
        Placement pl = placement();
        if (pl == null) return;
        lastHitter = player.getUUID();
        Pair<Double, Double> from = Frames.toCanonical(pl, player.getX(), player.getZ());
        if (from == null) return;
        String msg = switch (m.hit(from.first, from.second)) {
            case STARTED -> {
                slideFrom = context.run.serverTick + delay;
                Sim.sound(SoundEvents.SILVERFISH_HURT, 0.5f, 1f, fish != null ? fish.position() : null);
                yield null;
            }
            case WHILE_MOVING -> IcePathModel.MSG_MOVING;
            case BLOCKED -> IcePathModel.MSG_BLOCKED;
            case IGNORED -> null;
        };
        if (msg != null) Sim.asPlayer(player, () -> Sim.chat("§c" + msg));
    }

    @Override
    public void onTick() {
        IcePathModel m = model;
        if (m == null) return;
        Placement pl = placement();
        if (pl == null) return;
        if (solved) {
            chest.onTick();
            if (fishGoneAt != Integer.MIN_VALUE && context.run.t() >= fishGoneAt) { discardFish(); fishGoneAt = Integer.MIN_VALUE; }
            return;
        }
        int now = context.run.t();
        Fish f = fish;
        if (f == null || f.isRemoved()) {
            if (now < respawnAt) return;
            m.reset();
            f = new Fish(Sim.level());
            f.setNoAi(true);
            f.setNoGravity(true);
            f.setInvulnerable(true);
            f.setSilent(true);
            Pair<Double, Double> w0 = Frames.toWorld(pl, m.x(), m.z());
            f.snapTo(w0.first, (double) floorY, w0.second, Frames.yawToWorld(pl, 0f), 0f);
            fish = Sim.spawn(f);
        }
        IcePathModel.Phase before = m.phase;
        IcePathModel.Phase after = before == IcePathModel.Phase.MOVING && context.run.serverTick < slideFrom ? before : m.tick();
        Pair<Double, Double> w = Frames.toWorld(pl, m.x(), m.z());
        float yaw = m.headingX() != 0 || m.headingZ() != 0
                ? Frames.yawToWorld(pl, TpMazeModel.yawTo(m.headingX(), m.headingZ()))
                : f.getYRot();
        f.snapTo(w.first, (double) floorY, w.second, yaw, 0f);
        f.yHeadRot = yaw;
        if (before == IcePathModel.Phase.MOVING && after == IcePathModel.Phase.SOLVED) {
            solved = true;
            chest.objectiveDone = true;
            Placement p2 = placement();
            if (p2 != null) HardBlocks.apply(p2, wallEdits());
            fishGoneAt = now + 1;
        } else if (before == IcePathModel.Phase.MOVING && after == IcePathModel.Phase.TRAPPED) {
            discardFish();
            respawnAt = now + RESPAWN_TICKS;
            Services.puzzles().reportFailed(this, lastHitter, IcePathModel.MSG_FAIL);
        }
    }

    /** The 12 ice blocks (3 columns, 4 high) that close the exit corridor, as air. */
    private List<BlockEdit> wallEdits() {
        List<BlockEdit> out = new ArrayList<>();
        for (int x = IcePathLayout.CORRIDOR_X_MIN; x <= IcePathLayout.CORRIDOR_X_MAX; x++)
            for (int y = WALL_Y_MIN; y <= WALL_Y_MAX; y++) out.add(new BlockEdit(x, y, WALL_Z, "minecraft:air"));
        return out;
    }

    private void discardFish() {
        if (fish != null) fish.discard();
        fish = null;
    }

    @Override
    public void onReset() {
        discardFish();
        if (model != null) model.reset();
        respawnAt = Integer.MIN_VALUE;
        slideFrom = Integer.MIN_VALUE;
    }

    @Override
    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("solved", solved);
        chest.snapshot(o);
        return o;
    }

    @Override
    public void restore(JsonObject data) {
        JsonElement s = data.get("solved");
        solved = s != null && s.getAsBoolean();
        chest.restore(data);
        Placement pl = placement();
        if (pl != null) {
            List<BlockEdit> edits = new ArrayList<>();
            edits.add(chest.placeEdit());
            if (solved) edits.addAll(wallEdits());
            HardBlocks.apply(pl, edits);
        }
        discardFish();
        if (model != null) model.reset();
        respawnAt = Integer.MIN_VALUE;
    }

    IcePathModel iceModel() { return model; }

    /** Ticks after a fail before a fresh fish stands on the start cell. Unmeasured. */
    public static final int RESPAWN_TICKS = 40;

    /** Ticks from an arrow's hit to the fish moving (measured: 3). */
    public static final int SLIDE_DELAY_TICKS = 3;

    /** The ice wall that opens at the goal: canonical z 25, y 68 to 71. */
    public static final int WALL_Z = 25;
    public static final int WALL_Y_MIN = 68;
    public static final int WALL_Y_MAX = 71;
}
