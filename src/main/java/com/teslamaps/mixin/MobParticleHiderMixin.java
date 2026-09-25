package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.teslamaps.features.ParticleHider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({Blaze.class, EnderMan.class})
public class MobParticleHiderMixin {
    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private boolean teslamaps$mobParticles(Level level, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        Object self = this;
        return self instanceof Blaze ? !ParticleHider.hideBlaze()
                : !ParticleHider.hideEnderman() && !(level.isClientSide() && com.teslamaps.features.EndermanTeleportHider.active());
    }
}
