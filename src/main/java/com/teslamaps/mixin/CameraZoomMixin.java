package com.teslamaps.mixin;

import com.teslamaps.features.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraZoomMixin {
    // update() builds the projection from the fov FIELD (calculateFov result), never via
    // getFov(), so the hook has to sit on calculateFov's return.
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void teslamaps$zoomFov(CallbackInfoReturnable<Float> cir) {
        float base = cir.getReturnValueF();
        float fov = base;
        if (Zoom.transitioning()) fov = Zoom.apply(fov);
        if (fov != base) cir.setReturnValue(fov);
    }
}
