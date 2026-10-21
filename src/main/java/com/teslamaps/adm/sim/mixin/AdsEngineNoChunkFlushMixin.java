// Ported from ADM's mixin/AdsEngineNoChunkFlushMixin.java
package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The ADS world's chunks are not written when its level closes either. */
@Mixin(ServerChunkCache.class)
public class AdsEngineNoChunkFlushMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "save(Z)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsNoFlush(boolean flush, CallbackInfo ci) {
        if (SimServer.isSimLevel(level)) ci.cancel();
    }
}
