package com.teslamaps.mixin;

import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockStateModelSet.class)
public class BlockStateModelSetMixin {

    @ModifyVariable(method = "get", at = @At("HEAD"), argsOnly = true)
    private BlockState teslamaps$commissionColor(BlockState state) {
        return com.teslamaps.features.mining.CommissionBlockColor.process(state);
    }
}
