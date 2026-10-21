package com.teslamaps.mixin;

import com.teslamaps.features.NoammTweaks;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public class TimeChangerMixin {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void teslamaps$fixedTime(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        if (NoammTweaks.timeChangerOn()) cir.setReturnValue(NoammTweaks.time());
    }
}
