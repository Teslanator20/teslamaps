package com.teslamaps.mixin;

import com.teslamaps.debug.PacketLogger;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class PacketLogMixin {

    @Inject(method = "genericsFtw", at = @At("HEAD"))
    private static void teslamaps$logIn(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        PacketLogger.onPacket(packet, true);
    }

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void teslamaps$logOut(Packet<?> packet, ChannelFutureListener l, boolean flush, CallbackInfo ci) {
        PacketLogger.onPacket(packet, false);
    }
}
