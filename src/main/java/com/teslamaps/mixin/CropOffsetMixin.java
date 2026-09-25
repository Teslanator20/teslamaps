package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.teslamaps.features.NoCropRotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CropOffsetMixin {

    @ModifyReturnValue(method = "getSeed", at = @At("RETURN"))
    private long teslamaps$cropSeed(long seed) {
        return NoCropRotation.seed((BlockState) (Object) this, seed);
    }

    @ModifyReturnValue(method = "getOffset", at = @At("RETURN"))
    private Vec3 teslamaps$cropOffset(Vec3 offset) {
        return NoCropRotation.offset((BlockState) (Object) this, offset);
    }
}
