package com.teslamaps.mixin;

import com.teslamaps.features.theend.EndermanQol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class EndermanTeleportMixin {

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"), cancellable = true)
    private void teslamaps$stopEndermanTp(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && packet.relatives().isEmpty()
                && EndermanQol.dropTeleport(packet.id(), packet.change().position())) ci.cancel();
    }

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"), cancellable = true)
    private void teslamaps$stopEndermanSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()
                && EndermanQol.dropTeleport(packet.id(), packet.values().position())) ci.cancel();
    }
}
