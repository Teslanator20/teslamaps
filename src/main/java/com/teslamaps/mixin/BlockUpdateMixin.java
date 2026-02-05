package com.teslamaps.mixin;

import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.puzzle.SimonSaysSolver;
import com.teslamaps.dungeon.puzzle.SpiritBearTimer;
import com.teslamaps.dungeon.puzzle.TerracottaTimer;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept block state changes for puzzle solvers.
 */
@Mixin(World.class)
public class BlockUpdateMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("HEAD"))
    private void onSetBlockState(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (!DungeonManager.isInDungeon()) return;

        World world = (World)(Object)this;
        if (!world.isClient()) return;

        BlockState oldState = world.getBlockState(pos);

        // Forward to puzzle solvers
        SimonSaysSolver.onBlockUpdate(pos, oldState, newState);
        TerracottaTimer.onBlockUpdate(pos, oldState, newState);
        SpiritBearTimer.onBlockUpdate(pos, oldState, newState);
    }
}
