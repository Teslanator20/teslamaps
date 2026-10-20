package com.teslamaps.adm.leap.mixin;

import com.teslamaps.adm.leap.pov.PovCapture;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** POV previews around the world render: HEAD = Skip Own View, RETURN = the normal path. */
@Mixin(GameRenderer.class)
public class AdmPovGameRendererMixin {

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void adm$beforeLevelRender(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (PovCapture.beforeLevelRender(deltaTracker)) ci.cancel();
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void adm$afterLevelRender(DeltaTracker deltaTracker, CallbackInfo ci) {
        PovCapture.afterLevelRender(deltaTracker);
    }
}
