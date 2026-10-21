// Ported from ADM's p3sim/clear/puzzles/BeamsPuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creeper Beams in the world. An arrow near either end of a pair turns both lanterns to prismarine on the same tick. After the last
 * pair the chest appears CHEST_DELAY_TICKS later and the room turns green just after it. Not simulated: the guardian beam, the
 * creeper and the centre pedestal.
 */
public class BeamsPuzzle extends WorldPuzzle {
    private BeamsLogic logic = null;
    private WorldPos chest = null;
    private int chestAt = -1;
    private UUID solver = null;

    /** The room turns green this long after the chest appears (measured 1 to 4). */
    private final int greenAfterChest = 2;

    public BeamsPuzzle(PuzzleContext context) { super(PuzzleKinds.BEAMS, context); }

    @Override protected void build() {
        List<WorldPos> a = context().anchor("chest");
        chest = a.isEmpty() ? null : a.get(0);
        List<Pair<BeamsLogic.Cell, BeamsLogic.Cell>> pairs = BeamsLogic.pairsIn(c -> {
            BlockState s = stateAt(c.x, c.y, c.z);
            return s != null && s.getBlock() == Blocks.SEA_LANTERN;
        });
        logic = new BeamsLogic(pairs);
        chestAt = -1;
        if (pairs.isEmpty()) Ec.LOG.warn("[ads] Creeper Beams in " + context().room.designId + ": none of Odin's lantern pairs is in the room, so Odin cannot solve it");
    }

    private void apply(BeamsLogic.Change c) {
        for (BeamsLogic.Cell it : c.toPrismarine) setAt(it.x, it.y, it.z, Blocks.PRISMARINE.defaultBlockState());
        for (BeamsLogic.Cell it : c.toLantern) setAt(it.x, it.y, it.z, Blocks.SEA_LANTERN.defaultBlockState());
    }

    @Override public void onArrowHit(int x, int y, int z, UUID by) {
        BeamsLogic l = logic;
        if (l == null) return;
        if (status() != PuzzleStatus.UNSOLVED || chestAt >= 0) return;
        Pair<Integer, Integer> loc = local(x, z);
        if (loc == null) return;
        BeamsLogic.Cell cell = l.lanternNear(loc.first + 0.5, y + 0.5, loc.second + 0.5);
        if (cell == null) return;
        apply(l.hit(cell));
        if (l.solved()) { chestAt = now() + CHEST_DELAY_TICKS; solver = by; }
    }

    @Override public void onTick() {
        if (chestAt >= 0 && chestAt <= now()) {
            chestAt = -1;
            applySolved();
            solve(solver);
        }
    }

    @Override public int greenLeadTicks() { return -greenAfterChest; }

    @Override public void applySolved() {
        BeamsLogic l = logic;
        if (l != null) for (BeamsLogic.Cell it : l.lanterns()) setAt(it.x, it.y, it.z, Blocks.PRISMARINE.defaultBlockState());
        if (chest != null) setWorld(chest, Blocks.CHEST.defaultBlockState());
    }

    @Override public void onReset() {
        BeamsLogic l = logic;
        if (l != null) {
            for (BeamsLogic.Cell it : l.lanterns()) setAt(it.x, it.y, it.z, Blocks.SEA_LANTERN.defaultBlockState());
            if (chest != null) setWorld(chest, Blocks.AIR.defaultBlockState());
            l.reset();
        }
        chestAt = -1;
    }

    /** The chest appears this long after the last pair (measured 28 to 32). */
    public static final int CHEST_DELAY_TICKS = 30;
}
