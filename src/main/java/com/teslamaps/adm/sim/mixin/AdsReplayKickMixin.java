package com.teslamaps.adm.sim.mixin;
// Ported from ADM's mixin/AdsReplayKickMixin.java

import com.teslamaps.adm.sim.clear.replay.ReplaySession;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A kick raised while an ADS replay hands the server one of its recorded packets is noted in the report instead of dropping you. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class AdsReplayKickMixin {
    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"), cancellable = true)
    private void ec$replayKick(DisconnectionDetails details, CallbackInfo ci) {
        if (ReplaySession.swallowKick(details.reason().getString())) ci.cancel();
    }
}
