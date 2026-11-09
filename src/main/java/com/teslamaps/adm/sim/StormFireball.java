package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/StormEnd.kt

import java.util.function.Consumer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class StormFireball extends LargeFireball {
    private final Consumer<Vec3> onImpact;

    public StormFireball(Level level, Consumer<Vec3> onImpact) {
        super(EntityTypes.FIREBALL, level);
        this.onImpact = onImpact;
    }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide() || isRemoved()) return;
        onImpact.accept(result.getLocation());
        discard();
    }

    @Override
    public void tick() { super.tick(); if (tickCount > 200) discard(); }
}
