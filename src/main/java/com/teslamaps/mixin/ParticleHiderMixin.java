package com.teslamaps.mixin;

import com.teslamaps.features.ParticleHider;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleHiderMixin {
    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void teslamaps$particleHider(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<?> cir) {
        if (ParticleHider.hide(options, x, y, z)) cir.setReturnValue(null);
    }
}
