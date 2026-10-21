package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Blocks.kt (Debris)

import java.lang.reflect.Field;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class Debris extends FallingBlockEntity {
    public Debris(Level level, BlockState state) {
        super(EntityType.FALLING_BLOCK, level);
        setState(this, state);
    }

    static void setState(FallingBlockEntity e, BlockState state) {
        try {
            Field f = FallingBlockEntity.class.getDeclaredField("blockState");
            f.setAccessible(true);
            f.set(e, state);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public void tick() {
        if (++time > 200) {
            discard();
            return;
        }
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) {
            discard();
            return;
        }
        setDeltaMovement(getDeltaMovement().scale(0.98));
    }
}
