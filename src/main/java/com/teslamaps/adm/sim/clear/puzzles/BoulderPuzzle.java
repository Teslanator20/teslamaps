// Ported from ADM's p3sim/clear/puzzles/BoulderPuzzle.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PuzzleStatus;
import com.teslamaps.adm.sim.run.api.PuzzleContext;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;

/**
 * Boulder in the world: the sim reads Odin's grid, takes Odin's list of clicks and finishes when every one has been made on its wall
 * sign or stone button (y = 65). NOT simulated: the boulders sliding, and what a wrong press does to the room.
 */
public class BoulderPuzzle extends WorldPuzzle {
    private BoulderLogic logic = null;
    private WorldPos chest = null;

    public BoulderPuzzle(PuzzleContext context) { super(PuzzleKinds.BOULDER, context); }

    @Override public List<WorldPos> rewardSpots() { return chest == null ? List.of() : List.of(chest); }

    @Override protected void build() {
        List<WorldPos> a = context().anchor("chest");
        chest = a.isEmpty() ? null : a.get(0);
        if (chest != null) setWorld(chest, Blocks.CHEST.defaultBlockState());
        String key = BoulderLogic.key((x, z) -> isAirAt(x, 66, z));
        List<int[]> solution = BoulderLogic.solutionFor(key);
        if (solution == null) {
            String variant = context().room.variantId != null ? "/" + context().room.variantId : "";
            Ec.LOG.warn("[ads] Boulder in " + context().room.designId + variant + ": pattern " + key + " is not in Odin's table, so Odin cannot solve it");
        }
        logic = new BoulderLogic(solution != null ? solution : List.of());
    }

    @Override public boolean onInteract(UUID player, int x, int y, int z) {
        BoulderLogic l = logic;
        if (l == null) return false;
        if (y != 65) return false;
        Pair<Integer, Integer> loc = local(x, z);
        if (loc == null) return false;
        if (status() != PuzzleStatus.UNSOLVED) return true;
        l.click(loc.first, loc.second);
        if (l.solved()) solve(player);
        return true;
    }

    @Override public void onReset() { if (logic != null) logic.reset(); }
}
