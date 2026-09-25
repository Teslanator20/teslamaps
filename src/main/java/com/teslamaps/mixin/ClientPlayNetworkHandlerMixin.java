package com.teslamaps.mixin;

import com.teslamaps.dungeon.BloodCamp;
import com.teslamaps.dungeon.WitherDragons;
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
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
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

    // The termsim's fake menu shares container id 0 with the real inventory, so
    // server slot updates are routed to the real inventory and kept out of the sim.
    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"), cancellable = true)
    private void teslamaps$guardTermSimSlots(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.screen instanceof com.teslamaps.dungeon.termsim.TermSimGUI sim)) return;
        int slot = packet.getSlot();
        if (slot < 0 || slot >= sim.size) return;
        mc.execute(() -> {
            if (mc.player != null) mc.player.inventoryMenu.setItem(slot, packet.getStateId(), packet.getItem());
        });
        ci.cancel();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        com.teslamaps.utils.ContainerState.onSetSlot(packet);
        com.teslamaps.features.MelodyMessage.onSlotUpdate(packet.getSlot(), packet.getItem());
    }

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void teslamaps$onOpenScreen(net.minecraft.network.protocol.game.ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        com.teslamaps.utils.ContainerState.onOpen(packet);
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void teslamaps$onContainerContent(net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        com.teslamaps.utils.ContainerState.onContent(packet);
    }

    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void teslamaps$onContainerClose(net.minecraft.network.protocol.game.ClientboundContainerClosePacket packet, CallbackInfo ci) {
        com.teslamaps.utils.ContainerState.onClose();
    }

    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void onPongResponse(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PingMeter.onPong(packet.time());
    }

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void onSetTime(net.minecraft.network.protocol.game.ClientboundSetTimePacket packet, CallbackInfo ci) {
        com.teslamaps.features.PerformanceHud.onTimePacket();
        com.teslamaps.utils.ServerTicks.onTimePacket(packet.gameTime());
        com.teslamaps.features.TimerTriggers.onTimePacket(packet.gameTime());
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        WitherDragons.onParticlePacket(packet);
        com.teslamaps.safari.SafariTracker.onParticlePacket(packet);
        com.teslamaps.features.HotspotRadar.onParticlePacket(packet);
        com.teslamaps.features.SecretTrackerLocator.onParticle(packet);
        com.teslamaps.features.HotspotWatcher.onParticlePacket(packet);
        com.teslamaps.features.foraging.FloorDrops.onParticle(packet);
        com.teslamaps.features.GlowingMushrooms.onParticle(packet);
        com.teslamaps.features.EventTracker.onParticle(packet);
        com.teslamaps.features.mobvisuals.MobVisuals.onParticle(packet);
        com.teslamaps.features.mining.TreasureChestHelper.onParticle(packet);
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "handleParticleEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V"))
    private net.minecraft.core.particles.ParticleOptions teslamaps$colorParticleFix(net.minecraft.core.particles.ParticleOptions opt,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ClientboundLevelParticlesPacket packet) {
        return com.teslamaps.features.ColorParticleFix.fix(opt, packet);
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void onParticleHide(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()
                && (com.teslamaps.features.SoulweaverHider.hideParticle(packet)
                    || com.teslamaps.features.Deployables.hideParticle(packet)
                    || com.teslamaps.features.SadanFeatures.hideParticle()
                    || com.teslamaps.features.HideWitherKing.hideParticle(packet)
                    || com.teslamaps.features.DungeonCleanEnd.hideParticle()
                    || com.teslamaps.features.MageBeam.onParticle(packet)
                    || com.teslamaps.features.ParticleHiders.hideParticle(packet)
                    || com.teslamaps.features.AbilityRadius.hideParticle(packet)
                    || com.teslamaps.features.EndermanTeleportHider.hideParticle(packet))) ci.cancel();
    }

    @Inject(method = "handleInitializeBorder", at = @At("HEAD"))
    private void teslamaps$onInitBorder(net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket packet, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            com.teslamaps.features.ShadowAssassinAlert.onBorder(packet.getWarningTime());
        }
    }

    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void onTabList(ClientboundTabListPacket packet, CallbackInfo ci) {
        WitherDragons.onTabFooter(packet.footer().getString());
        com.teslamaps.dungeon.DungeonInfoHud.onTabFooter(packet.footer().getString());
        com.teslamaps.features.DailyTimers.onTabFooter(packet.footer().getString());
    }

    @Inject(method = "handleMoveEntity", at = @At("HEAD"))
    private void onMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        BloodCamp.onMoveEntityPacket(packet);
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void onMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        com.teslamaps.dungeon.puzzle.TPMazeSolver.onPositionPacket(
            packet.change().position(), packet.change().yRot(), packet.change().xRot());
        com.teslamaps.dungeon.DungeonWaypoints.onTeleport(packet.change().position());
    }

    @Inject(method = "handleSetEquipment", at = @At("HEAD"))
    private void onSetEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        BloodCamp.onSetEquipmentPacket(packet);
        com.teslamaps.features.DeadMobHider.onSetEquipment(packet);
        com.teslamaps.debug.DeathProbe.onSetEquipment(packet);
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) com.teslamaps.features.KingRelics.onSetEquipment(packet);
        if (com.teslamaps.config.TeslaMapsConfig.get().witherDragons) com.teslamaps.dungeon.WitherDragons.onSetEquipment(packet);
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true)
    private void onAddEntity(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci) {
        // dropping the spawn outright, so the entity is never created or ticked
        if (com.teslamaps.features.PacketHiders.shouldCancelSpawn(packet.getType())
                || com.teslamaps.features.HideWitherKing.shouldCancelSpawn(packet)) {
            ci.cancel();
            return;
        }
        if (com.teslamaps.config.TeslaMapsConfig.get().witherDragons) com.teslamaps.dungeon.WitherDragons.onAddEntity(packet);
        com.teslamaps.features.DragonRecolor.onAddEntity(packet);
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "handleSetEntityData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundSetEntityDataPacket;packedItems()Ljava/util/List;"))
    private java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> teslamaps$doubleSneakFix(
            java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet) {
        return com.teslamaps.features.CameraTweaks.filterEntityData(values, packet);
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void onSetEntityData(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (com.teslamaps.config.TeslaMapsConfig.get().witherDragons) com.teslamaps.dungeon.WitherDragons.onSetEntityData(packet);
        com.teslamaps.features.DeadMobHider.onSetEntityData(packet);
        com.teslamaps.debug.DeathProbe.onSetEntityData(packet);
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void onRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            for (int id : packet.getEntityIds()) com.teslamaps.features.SecretItemPickup.onPacketItem(id, true);
        }
        BloodCamp.onRemoveEntitiesPacket(packet);
        for (int id : packet.getEntityIds()) com.teslamaps.features.SecretClickHighlight.onItemGone(id);
        com.teslamaps.features.DeadMobHider.onRemoveEntities(packet);
        com.teslamaps.debug.DeathProbe.onRemoveEntities(packet);
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void onTakeItemEntityHead(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            com.teslamaps.features.SecretItemPickup.onPacketItem(packet.getItemId(), false);
            com.teslamaps.features.SecretChime.onTakeItem(packet.getItemId(), packet.getPlayerId());
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void onSoundEventSecrets(net.minecraft.network.protocol.game.ClientboundSoundPacket packet, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) {
            com.teslamaps.features.SecretWaypoints.onSound(packet);
            com.teslamaps.features.mining.TreasureChestHelper.onSound(packet);
            if (com.teslamaps.features.SecretSounds.onSoundPacket(packet)
                    | com.teslamaps.features.SecretChime.onSoundPacket(packet)) ci.cancel();
        }
    }

    @Inject(method = "handleTakeItemEntity", at = @At("TAIL"))
    private void onTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        com.teslamaps.features.SecretItemPickup.onTakeItem(packet.getItemId());
        com.teslamaps.features.SecretClickHighlight.onItemGone(packet.getItemId());
    }

    @Inject(method = "handleHurtAnimation", at = @At("HEAD"))
    private void onHurtAnimation(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
        ThornStunTimer.onHurtAnimation(packet.id());
        com.teslamaps.features.M4Highlight.onHurtAnimation(packet.id());
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onEntityEvent(net.minecraft.network.protocol.game.ClientboundEntityEventPacket packet, CallbackInfo ci) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        com.teslamaps.features.OverkillCounter.onEntityEvent(packet.getEntity(mc.level), packet.getEventId());
        net.minecraft.world.entity.Entity target = packet.getEntity(mc.level);
        if (target != null) com.teslamaps.features.DeadMobHider.onEntityEvent(target.getId(), packet.getEventId());
        if (target != null && mc.isSameThread()) com.teslamaps.dungeon.MimicDetector.onEntityEvent(target, packet.getEventId());
        if (target != null && mc.isSameThread()) com.teslamaps.features.SadanFeatures.onEntityEvent(target, packet.getEventId());
        com.teslamaps.debug.DeathProbe.onEntityEvent(packet, packet.getEventId());
    }

    @Inject(method = "setTitleText", at = @At("HEAD"), cancellable = true)
    private void onSetTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (CustomTitles.shouldHideDefaultTitle()) { ci.cancel(); return; }
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) return;
        if (com.teslamaps.features.TerminalTitles.onTitle(packet.text().getString(), false)) { ci.cancel(); return; }
        if (com.teslamaps.features.TimerTriggers.onTitle(packet.text().getString())) ci.cancel();
    }

    @Inject(method = "setSubtitleText", at = @At("HEAD"), cancellable = true)
    private void onSetSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (CustomTitles.shouldHideDefaultTitle()) { ci.cancel(); return; }
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()
                && (com.teslamaps.features.TerminalTitles.onTitle(packet.text().getString(), true)
                    || com.teslamaps.features.F7Titles.onSubtitle(packet.text().getString()))) ci.cancel();
    }

    @Inject(method = "setTitlesAnimation", at = @At("HEAD"), cancellable = true)
    private void onSetTitlesAnimation(ClientboundSetTitlesAnimationPacket packet, CallbackInfo ci) {
        if (CustomTitles.shouldHideDefaultTitle()) { ci.cancel(); return; }
        if (net.minecraft.client.Minecraft.getInstance().isSameThread()) CustomTitles.serverTimes = true;
    }

    @Inject(method = "setTitlesAnimation", at = @At("RETURN"))
    private void teslamaps$titlesAnimationDone(ClientboundSetTitlesAnimationPacket packet, CallbackInfo ci) {
        CustomTitles.serverTimes = false;
    }

    @Inject(method = "handleBlockEvent", at = @At("TAIL"))
    private void teslamaps$onBlockEvent(net.minecraft.network.protocol.game.ClientboundBlockEventPacket packet, CallbackInfo ci) {
        com.teslamaps.dungeon.puzzle.PuzzleTimers.onBlockEvent(packet.getPos(), packet.getBlock());
    }
}
