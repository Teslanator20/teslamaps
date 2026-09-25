package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.esp.StarredMobESP;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class NoArrowsMixin {
    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;

        if (TeslaMapsConfig.get().noArrows && self instanceof AbstractArrow
                && (!TeslaMapsConfig.get().noArrowsGroundedOnly || ((AbstractArrowInvoker) self).teslamaps$isInGround())) {
            cir.setReturnValue(true);
            return;
        }

        if (TeslaMapsConfig.get().lividFinder && StarredMobESP.shouldBeInvisible(self)) {
            cir.setReturnValue(true);
        }
    }
}
