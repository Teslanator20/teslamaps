package com.teslamaps.mixin;

import com.teslamaps.features.TranslucentLava;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterFogEnvironment.class)
public class WaterFogLavaMixin {
    @Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
    private void applyWaterFogForLava(FogType submersionType, Entity cameraEntity, CallbackInfoReturnable<Boolean> cir) {
        if (TranslucentLava.isEnabled() && submersionType == FogType.LAVA) {
            cir.setReturnValue(true);
        }
    }
}
