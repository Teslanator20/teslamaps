package com.teslamaps.mixin;

import com.teslamaps.features.ChangeCrouch;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraCrouchMixin {

    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    @Unique private static final float[] teslamaps$heights = new float[2];

    @Inject(method = "tick", at = @At("TAIL"))
    private void teslamaps$crouch(CallbackInfo ci) {
        float[] h = teslamaps$heights;
        h[0] = eyeHeightOld;
        h[1] = eyeHeight;
        if (ChangeCrouch.apply(entity, h)) {
            eyeHeightOld = h[0];
            eyeHeight = h[1];
        }
    }
}
