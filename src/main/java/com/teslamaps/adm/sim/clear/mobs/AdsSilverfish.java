// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.Level;

public class AdsSilverfish extends Silverfish implements AdsMobBody {
    public AdsSilverfish(Level level) { super(EntityTypes.SILVERFISH, level); }

    @Override protected void registerGoals() {}
    @Override public boolean shouldBeSaved() { return false; }
}
