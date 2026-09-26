package com.teslamaps.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Ported from FrankyAddons' BlockInteractionShapeMixin
@Mixin(ClipContext.Block.class)
public class NoGrassRaycastMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noGrass(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx,
                                   CallbackInfoReturnable<VoxelShape> cir) {
        if (com.teslamaps.features.diana.NoGrassRaycast.active
                && (Object) this == ClipContext.Block.OUTLINE
                && com.teslamaps.features.diana.NoGrassRaycast.passThrough(state)) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
