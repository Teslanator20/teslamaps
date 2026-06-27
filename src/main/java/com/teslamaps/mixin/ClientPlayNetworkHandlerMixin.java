/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.mixin;

import com.teslamaps.dungeon.BloodCamp;
import com.teslamaps.dungeon.WitherDragons;
import com.teslamaps.dungeon.puzzle.TerminalManager;
import com.teslamaps.features.PingMeter;
import com.teslamaps.features.CustomTitles;
import com.teslamaps.features.ThornStunTimer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        TerminalManager.onSlotUpdate(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }

    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void onPongResponse(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PingMeter.onPong(packet.time());
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        WitherDragons.onParticlePacket(packet);
    }

    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void onTabList(ClientboundTabListPacket packet, CallbackInfo ci) {
        WitherDragons.onTabFooter(packet.footer().getString());
    }

    @Inject(method = "handleMoveEntity", at = @At("HEAD"))
    private void onMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        BloodCamp.onMoveEntityPacket(packet);
    }

    @Inject(method = "handleSetEquipment", at = @At("HEAD"))
    private void onSetEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        BloodCamp.onSetEquipmentPacket(packet);
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void onRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        BloodCamp.onRemoveEntitiesPacket(packet);
    }

    @Inject(method = "handleTakeItemEntity", at = @At("TAIL"))
    private void onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        com.teslamaps.features.SecretItemPickup.onTakeItem(packet.getItemId());
    }

    @Inject(method = "handleHurtAnimation", at = @At("HEAD"))
    private void onHurtAnimation(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
        ThornStunTimer.onHurtAnimation(packet.id());
    }

    @Inject(method = "setTitleText", at = @At("HEAD"), cancellable = true)
    private void onSetTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (CustomTitles.shouldHideDefaultTitle()) ci.cancel();
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"), cancellable = true)
    private void onSetSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (CustomTitles.shouldHideDefaultTitle()) ci.cancel();
    }

    @Inject(method = "setTitlesAnimation", at = @At("HEAD"), cancellable = true)
    private void onSetTitlesAnimation(ClientboundSetTitlesAnimationPacket packet, CallbackInfo ci) {
        if (CustomTitles.shouldHideDefaultTitle()) ci.cancel();
    }
}
