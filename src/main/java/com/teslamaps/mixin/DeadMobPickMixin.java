package com.teslamaps.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class DeadMobPickMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void teslamaps$deadNotPickable(CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.DeadMobHider.pickGuard
                && com.teslamaps.features.DeadMobHider.unpickable((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
