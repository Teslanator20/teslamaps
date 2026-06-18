package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to hide explosion particles when noExplosions is enabled.
 */
@Mixin(ParticleEngine.class)
public class NoExplosionsMixin {
    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void onAddParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<?> cir) {
        if (!TeslaMapsConfig.get().noExplosions) return;

        // Check for explosion-related particles
        if (parameters.getType() == ParticleTypes.EXPLOSION ||
            parameters.getType() == ParticleTypes.EXPLOSION_EMITTER ||
            parameters.getType() == ParticleTypes.SMOKE ||
            parameters.getType() == ParticleTypes.LARGE_SMOKE) {
            cir.setReturnValue(null);
        }
    }
}
