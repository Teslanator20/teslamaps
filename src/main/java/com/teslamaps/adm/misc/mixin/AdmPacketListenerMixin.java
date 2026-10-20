package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import com.teslamaps.adm.misc.Witherborn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// TAIL injects only run on the main thread (the netty pass bails out at ensureRunningOnSameThread)
@Mixin(ClientPacketListener.class)
public abstract class AdmPacketListenerMixin {
    @Inject(method = "startWaitingForNewLevel", at = @At("TAIL"))
    private void teslamaps$admSkipLoadingScreen(LocalPlayer player, ClientLevel level, LevelLoadingScreen.Reason reason, CallbackInfo ci) {
        if (!RandomStuff.skipsLoadingScreen()) return;
        if (Minecraft.getInstance().screen instanceof LevelLoadingScreen screen) screen.onClose();
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void teslamaps$admWitherSpawn(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (packet.getType() == EntityType.WITHER) Witherborn.onSpawn(packet.getId(), packet.getX(), packet.getY(), packet.getZ());
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void teslamaps$admRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        RandomStuff.onRespawn();
    }
}
