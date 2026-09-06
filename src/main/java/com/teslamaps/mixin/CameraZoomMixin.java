package com.teslamaps.mixin;

import com.teslamaps.features.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void teslamaps$zoomFov(CallbackInfoReturnable<Float> cir) {
        if (Zoom.transitioning()) cir.setReturnValue(Zoom.apply(cir.getReturnValueF()));
    }
}
