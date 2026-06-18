package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to hide arrows stuck in entities.
 */
@Mixin(LivingEntity.class)
public class NoStuckArrowsMixin {
    @Inject(method = "getArrowCount", at = @At("HEAD"), cancellable = true)
    private void onGetStuckArrowCount(CallbackInfoReturnable<Integer> cir) {
        if (TeslaMapsConfig.get().noStuckArrows) {
            cir.setReturnValue(0);
        }
    }
}
