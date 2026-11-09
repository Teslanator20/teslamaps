package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class NoWaterOverlayMixin {
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void teslamaps$noWaterOverlay(CallbackInfo ci) {
        if (TeslaMapsConfig.get().noWaterOverlay) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void teslamaps$noInBlockOverlay(CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.moreOverlayHiders && c.hideInBlockOverlay) ci.cancel();
    }
}
