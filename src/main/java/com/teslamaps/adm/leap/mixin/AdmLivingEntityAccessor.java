package com.teslamaps.adm.leap.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface AdmLivingEntityAccessor {
    @Accessor("lerpYHeadRot")
    double adm$getLerpYHeadRot();

    @Accessor("lerpHeadSteps")
    int adm$getLerpHeadSteps();
}
