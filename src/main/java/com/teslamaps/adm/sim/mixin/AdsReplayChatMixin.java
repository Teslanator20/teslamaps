package com.teslamaps.adm.sim.mixin;
// Ported from ADM's mixin/AdsReplayChatMixin.java

import com.teslamaps.adm.sim.clear.replay.ReplayClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An ADS replay's report reads ADS's chat as the packets arrive, before any mod's chat filter can hide a line. Client thread only. */
@Mixin(ClientPacketListener.class)
public abstract class AdsReplayChatMixin {
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void ec$replayChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (ReplayClient.active && !packet.overlay() && Minecraft.getInstance().isSameThread()) ReplayClient.chat(packet.content().getString());
    }
}
