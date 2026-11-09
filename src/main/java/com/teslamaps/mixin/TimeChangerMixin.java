package com.teslamaps.mixin;

import com.teslamaps.features.NoammTweaks;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.ClientClockInstance.class)
public class TimeChangerMixin {
    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void teslamaps$fixedTime(CallbackInfoReturnable<Long> cir) {
        if (NoammTweaks.timeChangerOn()) cir.setReturnValue(NoammTweaks.time());
    }
}
