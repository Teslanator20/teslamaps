package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/WaterBoardPuzzle.kt

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * The Water Board in the world: levers move the pistons in the maze wall, water runs, a wool pit opens when its drain gets wet. Odin's
 * solver reads exactly these blocks. The pits only arm the reward chest; opening it solves the puzzle.
 */
public class WaterBoardPuzzle implements Puzzle {
    private final PuzzleContext context;

    private WaterBoardModel model = null;
    private UUID lastClicker = null;
    private final ChestReward chest;

    public WaterBoardPuzzle(PuzzleContext context) {
        this.context = context;
        this.chest = new ChestReward(context, ChestReward.WATER, this);
    }

    @Override public String kind() { return "WATER_BOARD"; }

    @Override public PuzzleContext context() { return context; }

    private Placement placement() { return context.room.placement; }

    @Override
    public void onRunStart() {
        chest.reset();
        RoomDesign design = context.room.design;
        if (design == null) return;
        if (context.run.packs.rooms == null) return;
        RoomVolume volume = context.run.packs.rooms.volumeFor(design, context.room.variantId);
        if (volume == null) return;
        WaterBoardModel m = WaterBoardModel.create(volume);
        if (m == null) return;
        if (m.closedPits.size() != 3) {
            m.closedPits.clear();
            m.closedPits.addAll(WaterBoardModel.randomClosedPits(context.run.subRandom("waterboard:" + context.room.index)));
        }
        model = m;
        Placement pl = placement();
        if (pl == null) return;
        List<BlockEdit> edits = new ArrayList<>();
        for (int k = 0; k <= 4; k++) edits.addAll(WaterBoardModel.pitEdits(k, m.closedPits.contains(k)));
        edits.add(chest.placeEdit());
        HardBlocks.apply(pl, edits);
    }

    @Override
    public boolean onInteract(UUID player, int x, int y, int z) {
        WaterBoardModel m = model;
        if (m == null) return false;
        Placement pl = placement();
        if (pl == null) return false;
        if (chest.onInteract(player, x, y, z)) return true;
        Pair<Integer, Integer> c = pl.toCanonical(x, z);
        if (c == null) return false;
        WaterLever lever = WaterLever.at(c.first, y, c.second);
        if (lever == null) return false;
        lastClicker = player;
        HardBlocks.apply(pl, List.of(m.click(lever)));
        Sim.sound(SoundEvents.LEVER_CLICK, 0.3f, m.isLeverOn(lever) ? LEVER_PITCH_ON : LEVER_PITCH_OFF, new Vec3(x + 0.5, y + 0.5, z + 0.5));
        return true;
    }

    @Override
    public void onTick() {
        WaterBoardModel m = model;
        if (m == null) return;
        Placement pl = placement();
        if (pl == null) return;
        int before = m.openedPits.size();
        HardBlocks.apply(pl, m.tick());
        if (m.openedPits.size() > before) Sim.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, ORB_PITCH);
        if (m.solved) chest.objectiveDone = true;
        chest.onTick();
    }

    @Override
    public JsonObject snapshot() {
        if (model == null) return null;
        JsonObject o = model.snapshot();
        chest.snapshot(o);
        return o;
    }

    @Override
    public void restore(JsonObject data) {
        WaterBoardModel m = model;
        if (m == null) return;
        Placement pl = placement();
        if (pl == null) return;
        m.restore(data);
        chest.restore(data);
        if (m.solved) chest.objectiveDone = true;
        List<BlockEdit> edits = new ArrayList<>(m.fullEdits());
        edits.add(chest.placeEdit());
        HardBlocks.apply(pl, edits);
    }

    /** Lever click pitches on Hypixel (on / off). */
    public static final float LEVER_PITCH_ON = 0.5873016f;
    public static final float LEVER_PITCH_OFF = 0.4920635f;
    public static final float ORB_PITCH = 1.4920635f;

    /** For `/ads dev`: the model, or null when the room's volume could not be read. */
    WaterBoardModel boardModel() { return model; }
}
