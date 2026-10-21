package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class P3SimLevelInfoMixin {
    @Inject(method = "isFlat", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsNotFlat(CallbackInfoReturnable<Boolean> cir) {
        if (SimServer.isSimLevel((ServerLevel) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "getSeaLevel", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsSeaLevel(CallbackInfoReturnable<Integer> cir) {
        if (SimServer.isSimLevel((ServerLevel) (Object) this)) cir.setReturnValue(63);
    }
}
