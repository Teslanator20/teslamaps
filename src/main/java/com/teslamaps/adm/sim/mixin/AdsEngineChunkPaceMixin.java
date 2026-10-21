// Ported from ADM's mixin/AdsEngineChunkPaceMixin.java
package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.run.ViewPin;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A full run's chunks arrive at Hypixel's 5 a tick, nearest first. */
@Mixin(PlayerChunkSender.class)
public abstract class AdsEngineChunkPaceMixin {
    @Shadow @Final @Mutable private boolean memoryConnection;
    @Shadow private float desiredChunksPerTick;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void teslamaps$adsPaceAtBuild(boolean memoryConnection, CallbackInfo ci) {
        if (!ViewPin.pacingChunks()) return;
        this.memoryConnection = false;
        this.desiredChunksPerTick = ViewPin.CHUNKS_PER_TICK;
    }

    @ModifyVariable(method = "onChunkBatchReceivedByClient", at = @At("HEAD"), argsOnly = true)
    private float teslamaps$adsPaceRate(float desired) {
        return ViewPin.pacingChunks() ? ViewPin.CHUNKS_PER_TICK : desired;
    }
}
