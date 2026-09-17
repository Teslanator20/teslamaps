package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class NoFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void teslamaps$noFog(Camera camera, int viewDistance, DeltaTracker deltaTracker, float fogScale,
                                 ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.noFog) {
            // ported from skyblocker's extendNetherFog: crimson isle only
            if (!cfg.noNetherFog || !com.teslamaps.utils.SkyblockUtils.isInArea("Crimson Isle")) return;
        }
        FogData data = cir.getReturnValue();
        if (data == null) return;
        data.environmentalStart = 1.0E8f;
        data.environmentalEnd = 1.0E9f;
        data.renderDistanceStart = 1.0E8f;
        data.renderDistanceEnd = 1.0E9f;
    }
}
