package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/SimPearl.kt

import com.teslamaps.adm.sim.Sim;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SimPearl extends ThrownEnderpearl {

    public SimPearl(Level level, LivingEntity owner, ItemStack stack) {
        super(level, owner, stack);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        // vanilla hurts the target for 0; this just lands
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level)) return;
        if (isRemoved()) return;
        for (int i = 0; i < ClearItems.PEARL_PUFFS; i++) {
            level.sendParticles(ParticleTypes.PORTAL, false, true, getX(), getY() + random.nextDouble() * 2.0, getZ(), 1, random.nextGaussian(), 0.0, random.nextGaussian(), 0.0);
        }
        Entity owner = getOwner();
        ServerPlayer thrower = owner instanceof ServerPlayer sp ? sp : null;
        if (thrower != null && thrower.isAlive() && !thrower.isSleeping() && thrower.level() == level) {
            Vec3 to = oldPosition();
            thrower.teleportTo(level, to.x, to.y, to.z, Relative.ROTATION, 0f, 0f, false);
            thrower.setDeltaMovement(Vec3.ZERO);
            thrower.fallDistance = 0.0;
        }
        Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f, position(), SoundSource.HOSTILE);
        discard();
    }
}
