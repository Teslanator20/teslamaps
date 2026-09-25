package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.EndermanTeleportHider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnderMan.class)
public class EnderManParticleMixin {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void teslamaps$hidePortal(Level level, ParticleOptions p, double x, double y, double z,
                                      double dx, double dy, double dz, Operation<Void> original) {
        if (level.isClientSide() && EndermanTeleportHider.active()) return;
        original.call(level, p, x, y, z, dx, dy, dz);
    }
}
