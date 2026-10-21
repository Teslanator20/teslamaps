package com.teslamaps.adm.sim.mixin;
// Ported from ADM's mixin/SimBotsGhostMixin.java

import com.teslamaps.adm.sim.P3Sim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class SimBotsGhostMixin {
    private boolean ec$simBot() {
        return (Object) this instanceof Mannequin && P3Sim.inSim();
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void ec$notPushable(CallbackInfoReturnable<Boolean> cir) {
        if (ec$simBot()) cir.setReturnValue(false);
    }

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void ec$notPickable(CallbackInfoReturnable<Boolean> cir) {
        if (ec$simBot()) cir.setReturnValue(false);
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void ec$noPushing(CallbackInfo ci) {
        if (ec$simBot()) ci.cancel();
    }
}
