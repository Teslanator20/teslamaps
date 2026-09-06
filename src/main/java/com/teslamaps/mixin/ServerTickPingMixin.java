package com.teslamaps.mixin;

import com.teslamaps.dungeon.BloodCamp;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ServerTickPingMixin {

    @Inject(method = "handlePing", at = @At("HEAD"))
    private void teslamaps$onServerPing(ClientboundPingPacket packet, CallbackInfo ci) {
        BloodCamp.onServerPing(packet.getId());
        if (packet.getId() != 0) com.teslamaps.dungeon.terminals.TerminalUtils.onServerTick();
    }
}
