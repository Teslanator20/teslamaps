// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.Level;

public class AdsEnderman extends Enderman implements AdsMobBody {
    public AdsEnderman(Level level) { super(EntityTypes.ENDERMAN, level); }

    @Override protected void registerGoals() {}
    @Override protected boolean teleport() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
}
