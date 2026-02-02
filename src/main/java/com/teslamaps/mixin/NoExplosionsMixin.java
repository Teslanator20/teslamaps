package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to hide explosion particles when noExplosions is enabled.
 */
@Mixin(ParticleManager.class)
public class NoExplosionsMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfoReturnable<?> cir) {
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
