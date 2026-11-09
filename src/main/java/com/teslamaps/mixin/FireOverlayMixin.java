package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
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

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void teslamaps$fireHead(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noFire) return;
        if (!c.fireOverlayAdjust || c.fireOverlayOpacity <= 0f) { ci.cancel(); return; }
        pose.pushPose();
        pose.translate(0f, -(0.5f - Math.max(0f, Math.min(100f, c.fireOverlayHeight)) / 200f), 0f);
        teslamaps$pushed = true;
    }

    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void teslamaps$fireReturn(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (!teslamaps$pushed) return;
        teslamaps$pushed = false;
        pose.popPose();
    }

    @ModifyConstant(method = "buildFireQuad", constant = @Constant(intValue = 0xE5FFFFFF))
    private static int teslamaps$fireAlpha(int color) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noFire || !c.fireOverlayAdjust) return color;
        int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(100f, c.fireOverlayOpacity)) / 100f);
        return (color & 0xFFFFFF) | (a << 24);
    }
}
