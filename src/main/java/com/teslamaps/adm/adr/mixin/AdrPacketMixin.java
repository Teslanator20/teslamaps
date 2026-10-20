package com.teslamaps.adm.adr.mixin;

import com.teslamaps.adm.adr.Adr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ADR packet hooks; all but the chat stamp run on the main thread
@Mixin(value = ClientPacketListener.class, priority = 500)
public class AdrPacketMixin {

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adrChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) { Adr.stampChat(packet); return; }
        if (Adr.onSystemChat(packet)) ci.cancel();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void teslamaps$adrTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) Adr.onTeleport(packet);
    }

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void teslamaps$adrSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) Adr.onSound(packet);
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void teslamaps$adrPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) Adr.onPickup(packet);
    }

    @Inject(method = "handleBlockEvent", at = @At("HEAD"))
    private void teslamaps$adrBlockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) Adr.onBlockEvent(packet);
    }
}
