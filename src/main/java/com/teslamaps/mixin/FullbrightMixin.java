package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for fullbright - makes everything fully lit.
 * Uses gamma override approach for 1.21.10 compatibility.
 */
@Mixin(LightmapTextureManager.class)
public class FullbrightMixin {

    /**
     * Override getBrightness to return max brightness when fullbright is enabled.
     */
    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private static void onGetBrightness(DimensionType type, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (TeslaMapsConfig.get().fullbright) {
            cir.setReturnValue(1.0f);
        }
    }
}
