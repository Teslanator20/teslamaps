package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class NoHurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noHurtCam(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (TeslaMapsConfig.get().noHurtCam) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noViewBobbing(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (TeslaMapsConfig.get().noViewBobbing) ci.cancel();
    }
}
