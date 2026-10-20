package com.teslamaps.adm.map.mixin;

import com.teslamaps.adm.map.clear.ClearBreakdown;
import com.teslamaps.adm.map.clear.Sightings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class AdmClearPacketsMixin {
    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void adm$pickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread() || !ClearBreakdown.enabled()) return;
        Sightings.onPickup(packet.getItemId(), packet.getPlayerId());
    }

    @Inject(method = "handleBlockEvent", at = @At("HEAD"))
    private void adm$chest(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread() || !ClearBreakdown.enabled()) return;
        Sightings.onBlockEvent(packet.getPos(), packet.getBlock(), packet.getB0(), packet.getB1());
    }

    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void adm$bat(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread() || !ClearBreakdown.enabled()) return;
        SoundEvent s = packet.getSound().value();
        if ((s != SoundEvents.BAT_HURT && s != SoundEvents.BAT_DEATH) || packet.getVolume() != 0.1f) return;
        Sightings.onBat(packet.getX(), packet.getY(), packet.getZ());
    }
}
