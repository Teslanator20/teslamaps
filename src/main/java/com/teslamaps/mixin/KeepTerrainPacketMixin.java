package com.teslamaps.mixin;

import com.teslamaps.features.KeepTerrainLoaded;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class KeepTerrainPacketMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "handleForgetLevelChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void teslamaps$keepTerrain(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (KeepTerrainLoaded.didRetain(level, packet.pos())) ci.cancel();
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER))
    private void teslamaps$cacheTerrain(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        KeepTerrainLoaded.onServerChunk(level, packet);
    }
}
