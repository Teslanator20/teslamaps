package com.teslamaps.mixin;

import com.teslamaps.features.MotionBlur;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The world is on the render target here and nothing else is yet, so the blur
// never touches the hud, the chat or an open screen.
@Mixin(GameRenderer.class)
public class MotionBlurMixin {

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void teslamaps$motionBlur(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        com.teslamaps.debug.Perf.begin("MotionBlur");
        try {
            MotionBlur.apply();
        } finally {
            com.teslamaps.debug.Perf.end();
        }
    }
}
