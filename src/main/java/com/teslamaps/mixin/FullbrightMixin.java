package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class FullbrightMixin {

    private static boolean teslamaps$wasFullbright = false;

    @Inject(method = "extract", at = @At("TAIL"))
    private void onExtract(LightmapRenderState state, float tickDelta, CallbackInfo ci) {
        boolean on = TeslaMapsConfig.get().fullbright;
        if (on) {
            state.brightness = 15.0f;
            state.darknessEffectScale = 0.0f;
        }
        if (on != teslamaps$wasFullbright) {
            state.needsUpdate = true;
            teslamaps$wasFullbright = on;
        }
    }
}
