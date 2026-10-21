// Replaces the Odin PacketEvent.Send/Receive listeners of ADM's p3sim/run/HypixelModApi.kt
package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.run.HypixelModApi;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the sim: Hypixel mod API payloads never reach the sim server, and no advancement packets arrive. */
@Mixin(Connection.class)
public class AdsEngineHypixelPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsHypixelSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (HypixelModApi.cancelSend(packet)) ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsHypixelReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (HypixelModApi.cancelReceive(packet)) ci.cancel();
    }
}
