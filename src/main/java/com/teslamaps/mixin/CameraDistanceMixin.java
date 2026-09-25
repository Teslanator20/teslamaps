package com.teslamaps.mixin;

import com.teslamaps.features.CameraTweaks;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraDistanceMixin {

    @ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float teslamaps$distance(float distance) {
        return CameraTweaks.cameraDistance(distance);
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void teslamaps$clip(float distance, CallbackInfoReturnable<Float> cir) {
        if (CameraTweaks.cameraClip()) cir.setReturnValue(distance);
    }
}
