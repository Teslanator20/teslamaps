// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.Level;

public class AdsSkeleton extends Skeleton implements AdsMobBody {
    public AdsSkeleton(Level level) { super(EntityType.SKELETON, level); }

    @Override protected void registerGoals() {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean fireImmune() { return true; }
}
