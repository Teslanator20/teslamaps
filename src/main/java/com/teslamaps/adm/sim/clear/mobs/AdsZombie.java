// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

public class AdsZombie extends Zombie implements AdsMobBody {
    public AdsZombie(Level level) { super(EntityType.ZOMBIE, level); }

    @Override protected void registerGoals() {}
    @Override protected boolean isSunSensitive() { return false; }
    @Override protected boolean convertsInWater() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean fireImmune() { return true; }
}
