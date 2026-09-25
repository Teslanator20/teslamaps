package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// height/opacity ported from Skyblocker's ScreenEffectRendererMixin
@Mixin(ScreenEffectRenderer.class)
public class FireOverlayMixin {

    @Unique private static boolean teslamaps$pushed;

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void teslamaps$fireHead(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noFire) return;
        if (!c.fireOverlayAdjust || c.fireOverlayOpacity <= 0f) { ci.cancel(); return; }
        pose.pushPose();
        pose.translate(0f, -(0.5f - Math.max(0f, Math.min(100f, c.fireOverlayHeight)) / 200f), 0f);
        teslamaps$pushed = true;
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void teslamaps$fireReturn(PoseStack pose, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (!teslamaps$pushed) return;
        teslamaps$pushed = false;
        pose.popPose();
    }

    @ModifyConstant(method = "renderFire", constant = @Constant(floatValue = 0.9f))
    private static float teslamaps$fireAlpha(float alpha) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noFire || !c.fireOverlayAdjust) return alpha;
        return alpha * Math.max(0f, Math.min(100f, c.fireOverlayOpacity)) / 100f;
    }
}
