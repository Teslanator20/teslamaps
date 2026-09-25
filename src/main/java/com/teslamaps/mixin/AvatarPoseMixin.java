package com.teslamaps.mixin;

import com.teslamaps.features.CameraTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public abstract class AvatarPoseMixin {
    @Shadow @Final protected static EntityDimensions STANDING_DIMENSIONS;

    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void teslamaps$pose(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose != Pose.CROUCHING && pose != Pose.SWIMMING) return;
        if ((Object) this != Minecraft.getInstance().player) return;
        EntityDimensions d = CameraTweaks.poseDimensions(pose, cir.getReturnValue(), STANDING_DIMENSIONS);
        if (d != null) cir.setReturnValue(d);
    }
}
