// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;

public class AdsEnderman extends EnderMan implements AdsMobBody {
    public AdsEnderman(Level level) { super(EntityType.ENDERMAN, level); }

    @Override protected void registerGoals() {}
    @Override protected boolean teleport() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
}
