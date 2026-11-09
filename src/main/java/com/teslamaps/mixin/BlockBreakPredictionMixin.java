package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
public class BlockBreakPredictionMixin {
    @WrapOperation(method = "extractBlockDestroyAnimation", at = @At(value = "NEW",
            target = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/client/renderer/state/level/BlockBreakingRenderState;"))
    private BlockBreakingRenderState teslamaps$predictBreak(BlockPos pos, BlockState state, int progress,
                                                            Operation<BlockBreakingRenderState> original) {
        return original.call(pos, state, com.teslamaps.features.mining.BlockBreakPrediction.stage(pos, state, progress));
    }
}
