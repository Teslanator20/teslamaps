package com.teslamaps.mixin;

import com.teslamaps.features.TranslucentLava;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LavaFogEnvironment.class)
public class LavaFogMixin {
    @Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
    private void disableLavaFog(FogType type, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (TranslucentLava.isEnabled() && type == FogType.LAVA) {
            cir.setReturnValue(false);
        }
    }
}
