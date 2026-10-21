package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/ChestReward.kt

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.api.Puzzle;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.Pair;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A hard puzzle's reward chest: the objective only arms it, opening it solves the puzzle. */
public class ChestReward {
    private final PuzzleContext context;
    private final ChestSpot spot;
    private final Puzzle puzzle;

    /** The puzzle's objective is met; the chest now solves it. */
    public boolean objectiveDone = false;

    /** The chest has been opened (kept in savepoints). */
    public boolean opened = false;

    private int clickedAt = Integer.MIN_VALUE;
    private UUID clicker = null;
    private final HashSet<Integer> done = new HashSet<>();

    public ChestReward(PuzzleContext context, ChestSpot spot, Puzzle puzzle) {
        this.context = context;
        this.spot = spot;
        this.puzzle = puzzle;
    }

    private Placement placement() { return context.room.placement; }

    /** The chest block, to be written with the room's other blocks. */
    public BlockEdit placeEdit() { return spot.edit(); }

    public void reset() { objectiveDone = false; opened = false; clickedAt = Integer.MIN_VALUE; done.clear(); }

    /** A right click on the world block (x, y, z). True when it was the chest (consumed either way). */
    public boolean onInteract(UUID player, int x, int y, int z) {
        Placement pl = placement();
        if (pl == null) return false;
        Pair<Integer, Integer> c = pl.toCanonical(x, z);
        if (c == null) return false;
        int cx = c.first, cz = c.second;
        if (cx != spot.x || y != spot.y || cz != spot.z) return false;
        if (objectiveDone && !opened && clickedAt == Integer.MIN_VALUE) { clickedAt = context.run.serverTick; clicker = player; done.clear(); }
        return true;
    }

    public void onTick() {
        if (clickedAt == Integer.MIN_VALUE || done.size() >= ChestFinale.schedule().size()) return;
        Placement pl = placement();
        if (pl == null) return;
        WorldPos w = pl.toWorld(spot.x, spot.y, spot.z);
        Vec3 at = new Vec3(w.x + 0.5, w.y + 0.5, w.z + 0.5);
        int since = context.run.serverTick - clickedAt;
        List<ChestFinale.Item> schedule = ChestFinale.schedule();
        for (int i = 0; i < schedule.size(); i++) {
            ChestFinale.Item item = schedule.get(i);
            if (since < item.at || !done.add(i)) continue;
            switch (item.step) {
                case OPEN_SOUND -> Sim.sound(SoundEvents.CHEST_OPEN, 0.5f, item.pitch, new Vec3((double) w.x, (double) w.y, (double) w.z));
                case OPEN_SOUND_CENTRE -> Sim.sound(SoundEvents.CHEST_OPEN, 0.5f, item.pitch, at);
                case LID_EVENT -> {
                    opened = true;
                    ClientboundBlockEventPacket packet = new ClientboundBlockEventPacket(new BlockPos(w.x, w.y, w.z), Blocks.CHERRY_LOG, 1, 1);
                    for (ServerPlayer p : Sim.players()) p.connection.send(packet);
                    Services.puzzles().reportSolved(puzzle, clicker);
                }
                case HARP -> Sim.sound(SoundEvents.NOTE_BLOCK_HARP, 1.0f, item.pitch, at);
            }
        }
    }

    public void snapshot(JsonObject o) { o.addProperty("chestOpened", opened); o.addProperty("objective", objectiveDone); }

    public void restore(JsonObject o) {
        JsonElement co = o.get("chestOpened");
        opened = co != null && co.getAsBoolean();
        JsonElement ob = o.get("objective");
        objectiveDone = opened || (ob != null && ob.getAsBoolean());
        clickedAt = Integer.MIN_VALUE;
        done.clear();
    }

    /** The canonical chest cells measured in the recordings. */
    public static final ChestSpot ICE = new ChestSpot(15, 67, 28, "north");
    public static final ChestSpot TP_MAZE = new ChestSpot(15, 70, 20, "south");
    public static final ChestSpot WATER = new ChestSpot(15, 56, 22, "south");
}
