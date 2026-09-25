package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Ported from WpcMod's BlindnessFogEnvironmentMixin
@Mixin(BlindnessFogEnvironment.class)
public class BlindnessFogMixin {
    @Inject(method = "setupFog", at = @At("TAIL"))
    private void teslamaps$fogOpacity(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noBlind || c.blindnessFogOpacity <= 0f || c.blindnessFogOpacity >= 100f) return;
        fog.color.w = fog.color.w * (c.blindnessFogOpacity / 100f);
    }
}
