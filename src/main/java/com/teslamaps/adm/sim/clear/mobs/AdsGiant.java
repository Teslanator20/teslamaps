// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.level.Level;

public class AdsGiant extends Giant implements AdsMobBody {
    public AdsGiant(Level level) { super(EntityTypes.GIANT, level); }

    @Override protected void registerGoals() {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean fireImmune() { return true; }
}
