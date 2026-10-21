// Ported from ADM's mixin/AdsEngineNoSaveMixin.java
package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ADS world is never saved: it is rebuilt on every open. */
@Mixin(MinecraftServer.class)
public class AdsEngineNoSaveMixin {
    @Inject(method = "saveAllChunks(ZZZ)Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsNoChunkSave(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (SimServer.server == (Object) this) cir.setReturnValue(true);
    }

    @Inject(method = "saveEverything(ZZZ)Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsNoSave(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (SimServer.server == (Object) this) cir.setReturnValue(true);
    }
}
