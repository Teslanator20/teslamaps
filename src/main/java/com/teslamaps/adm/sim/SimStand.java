package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Station.kt

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SimStand extends ArmorStand {
    public SimStand(Level level) { super(EntityType.ARMOR_STAND, level); }

    @Override
    public void travel(Vec3 travelVector) {}
}
