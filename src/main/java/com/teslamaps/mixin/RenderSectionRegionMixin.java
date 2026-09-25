package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderSectionRegion.class)
public class RenderSectionRegionMixin {

    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState teslamaps$recolor(BlockState original, BlockPos pos) {
        return com.teslamaps.features.mining.MinesRecolor.swap(pos, original);
    }
}
