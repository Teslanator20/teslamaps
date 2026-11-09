package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Blocks.kt (CarriedBlock)

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CarriedBlock extends FallingBlockEntity {
    public CarriedBlock(Level level, BlockState state) {
        super(EntityTypes.FALLING_BLOCK, level);
        Debris.setState(this, state);
        setNoGravity(true);
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public void tick() {}
}
