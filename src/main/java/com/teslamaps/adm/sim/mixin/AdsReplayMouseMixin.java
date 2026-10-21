package com.teslamaps.adm.sim.mixin;
// Ported from ADM's mixin/AdsReplayMouseMixin.java

import com.teslamaps.adm.sim.clear.replay.ReplayClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While an ADS replay drives your player the mouse turns nothing: the recorded look is eased in each frame instead. */
@Mixin(MouseHandler.class)
public abstract class AdsReplayMouseMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void ec$replayLook(double d, CallbackInfo ci) {
        if (!ReplayClient.active) return;
        ReplayClient.look();
        ci.cancel();
    }
}
