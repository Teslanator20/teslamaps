package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/StormEnd.kt

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class StormSkull extends WitherSkull {
    public StormSkull(Level level) { super(EntityType.WITHER_SKULL, level); }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide() || isRemoved()) return;
        StormFx.burst(result.getLocation());
        discard();
    }

    @Override
    public void tick() { super.tick(); if (tickCount > 200) discard(); }
}
