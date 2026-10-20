package com.teslamaps.adm.misc.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AdmArrowInGroundInvoker {
    @Invoker("isInGround")
    boolean teslamaps$admIsInGround();
}
