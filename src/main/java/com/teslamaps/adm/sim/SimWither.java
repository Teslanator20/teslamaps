package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Phases.kt

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;

public class SimWither extends WitherBoss {
    public SimWither(Level level) { super(EntityTypes.WITHER, level); }

    @Override
    public void checkDespawn() {}
}
