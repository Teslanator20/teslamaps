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
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.noHurtCam && c.hurtCamStrength <= 0f) ci.cancel();
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "bobHurt", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"))
    private double teslamaps$hurtCamStrength(double v) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.noHurtCam ? v * c.hurtCamStrength : v;
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noViewBobbing(CameraRenderState state, PoseStack pose, CallbackInfo ci) {
        if (TeslaMapsConfig.get().noViewBobbing) ci.cancel();
    }
}
