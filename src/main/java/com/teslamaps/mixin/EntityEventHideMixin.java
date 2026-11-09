package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 60 = death poof
@Mixin(LivingEntity.class)
public class EntityEventHideMixin {

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideEventParticles(byte id, CallbackInfo ci) {
        if (id == 60 && TeslaMapsConfig.get().hideDeathParticles) ci.cancel();
    }
}
