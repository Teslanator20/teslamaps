package com.teslamaps.mixin;

import com.teslamaps.dungeon.puzzle.TerminalManager;
import com.teslamaps.features.PingMeter;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to intercept slot update packets for event-driven terminal solving.
 * Instead of polling every 100ms, we react immediately when slots change.
 */
@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        // Notify terminal manager of slot update
        TerminalManager.onSlotUpdate(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }

    // Real RTT ping: time the echoed pong from our /ping request (tab latency is faked by Hypixel).
    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void onPongResponse(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PingMeter.onPong(packet.time());
    }
}
