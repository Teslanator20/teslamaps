package com.teslamaps.mixin;

import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.puzzle.SimonSaysSolver;
import com.teslamaps.dungeon.puzzle.SpiritBearTimer;
import com.teslamaps.dungeon.puzzle.TerracottaTimer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept block state changes for puzzle solvers.
 */
@Mixin(Level.class)
public class BlockUpdateMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void onSetBlockState(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!DungeonManager.isInDungeon()) return;

        Level world = (Level)(Object)this;
        if (!world.isClientSide()) return;

        BlockState oldState = world.getBlockState(pos);

        // Forward to puzzle solvers
        SimonSaysSolver.onBlockUpdate(pos, oldState, newState);
        TerracottaTimer.onBlockUpdate(pos, oldState, newState);
        SpiritBearTimer.onBlockUpdate(pos, oldState, newState);
        com.teslamaps.dungeon.WitherDragons.onBlockUpdate(pos, oldState, newState);
    }
}
