package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/TpMazePuzzle.kt

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.Pair;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * The Teleport Maze in the world: standing on an end-portal frame sends you to the landing of another pad's patch, looking along the
 * clue; reaching pad 28's patch arms the chest. The maze is drawn from the run's seed.
 */
public class TpMazePuzzle implements Puzzle {
    private final PuzzleContext context;

    private TpMazeModel model = null;
    private final HashMap<UUID, Integer> cooldown = new HashMap<>();
    private final ChestReward chest;

    public TpMazePuzzle(PuzzleContext context) {
        this.context = context;
        this.chest = new ChestReward(context, ChestReward.TP_MAZE, this);
    }

    @Override public String kind() { return "TP_MAZE"; }

    @Override public PuzzleContext context() { return context; }

    private Placement placement() { return context.room.placement; }

    @Override
    public void onRunStart() {
        chest.reset();
        cooldown.clear();
        RoomDesign design = context.room.design;
        RoomVolume volume = design != null && context.run.packs.rooms != null ? context.run.packs.rooms.volumeFor(design, context.room.variantId) : null;
        int y = volume != null ? TpMazeModel.frameYOf(volume) : 69;
        int routeGroups = HardPuzzlesModule.tpRouteGroups();
        model = TpMazeModel.generate(TpMazeModel.standardPads(), y, context.run.config.seed ^ (0x7e11L * (context.room.index + 1)), routeGroups);
        Placement pl = placement();
        if (pl != null) HardBlocks.apply(pl, List.of(chest.placeEdit()));
    }

    @Override
    public boolean onInteract(UUID player, int x, int y, int z) { return chest.onInteract(player, x, y, z); }

    @Override
    public void onTick() {
        TpMazeModel m = model;
        if (m == null) return;
        Placement pl = placement();
        if (pl == null) return;
        int now = context.run.t();
        for (PlayerRecord rec : context.run.state.players.values()) {
            if (rec.isBot || rec.ghost) continue;
            Integer cd = cooldown.get(rec.uuid);
            if (now - (cd != null ? cd : Integer.MIN_VALUE / 2) < COOLDOWN_TICKS) continue;
            Pair<Integer, Integer> c = pl.toCanonical((int) Math.floor(rec.x), (int) Math.floor(rec.z));
            if (c == null) continue;
            Pad pad = m.padAtFrame(c.first, c.second);
            if (pad == null) continue;
            double top = m.frameY + FRAME_HEIGHT;
            if (rec.y < top - 0.15 || rec.y > top + 0.3) continue;
            Landing land = m.step(pad.index);
            if (land == null) continue;
            cooldown.put(rec.uuid, now);
            ServerPlayer player = null;
            for (ServerPlayer p : Sim.players()) if (p.getUUID().equals(rec.uuid)) { player = p; break; }
            if (player == null) continue;
            WorldPos w = pl.toWorld((int) Math.floor(land.x), m.frameY, (int) Math.floor(land.z));
            Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f, new Vec3(w.x + 0.5, land.y, w.z + 0.5));
            Sim.tp(player, w.x + 0.5, land.y, w.z + 0.5, land.yaw + 90f * pl.quarterTurns(), land.pitch);
        }
        if (m.solved) chest.objectiveDone = true;
        chest.onTick();
    }

    @Override
    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("solved", model != null && model.solved);
        chest.snapshot(o);
        return o;
    }

    @Override
    public void restore(JsonObject data) {
        chest.restore(data);
        Placement pl = placement();
        if (pl != null) HardBlocks.apply(pl, List.of(chest.placeEdit()));
    }

    TpMazeModel mazeModel() { return model; }

    /** The top of an end-portal frame above its block's floor. */
    public static final double FRAME_HEIGHT = 0.8125;

    /** Ticks before the same player can be sent again. Unmeasured. */
    public static final int COOLDOWN_TICKS = 20;
}
