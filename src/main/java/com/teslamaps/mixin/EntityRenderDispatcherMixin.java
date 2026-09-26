package com.teslamaps.mixin;

import com.teslamaps.features.HidePlayers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hidePlayers(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        com.teslamaps.debug.Perf.begin("mixin:shouldRender");
        try {
            teslamaps$hideCheck(entity, cir);
        } finally {
            com.teslamaps.debug.Perf.end();
        }
    }

    private void teslamaps$hideCheck(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.GlobalRender.off()) return;
        com.teslamaps.config.TeslaMapsConfig cfg = com.teslamaps.config.TeslaMapsConfig.get();
        boolean noLightning = cfg.noLightning && entity instanceof net.minecraft.world.entity.LightningBolt;
        boolean noFalling = cfg.noFallingBlocks && entity instanceof net.minecraft.world.entity.item.FallingBlockEntity;
        boolean noXpOrb = cfg.noXpOrbs && entity instanceof net.minecraft.world.entity.ExperienceOrb;
        boolean annoying = cfg.hideFireworks && entity instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity
                || cfg.hideHorses && entity instanceof net.minecraft.world.entity.animal.equine.Horse;
        boolean dyingDragon = cfg.hideDyingDragons
                && entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                && dragon.getHealth() <= 0;
        if (noLightning || noFalling || noXpOrb || annoying || dyingDragon
                || com.teslamaps.features.DeadMobHider.shouldHide(entity)
                || com.teslamaps.features.NonStarredNametags.shouldHide(entity)
                || com.teslamaps.features.HideCheapCoins.shouldHide(entity)
                || HidePlayers.shouldHide(entity)
                || com.teslamaps.features.SoulweaverHider.shouldHide(entity)
                || com.teslamaps.features.M4Highlight.shouldHideEntity(entity)
                || com.teslamaps.features.SadanFeatures.shouldHide(entity)
                || com.teslamaps.features.LividSolver.shouldHideNametag(entity)
                || com.teslamaps.dungeon.puzzle.DungeonBlaze.shouldHideBlaze(entity)
                || com.teslamaps.features.foraging.ClearTreeLogs.shouldHide(entity)
                || com.teslamaps.features.FishingHookDisplay.shouldHide(entity)
                || com.teslamaps.features.fishing.FishingTweaks.shouldHide(entity)
                || com.teslamaps.features.DungeonCleanEnd.shouldHide(entity)
                || com.teslamaps.features.SecretPickupHud.shouldHide(entity)
                || com.teslamaps.features.MinionFeatures.shouldHide(entity)
                || com.teslamaps.features.RenderHiders.shouldHide(entity)
                || com.teslamaps.features.SkeletonHelmet.shouldHide(entity)
                || com.teslamaps.features.CommonSeaCreatureHider.shouldHide(entity)
                || com.teslamaps.features.ChumBucketHider.shouldHide(entity)
                || com.teslamaps.features.ArachneTagHider.shouldHide(entity)
                || com.teslamaps.features.GiftNametagCleaner.shouldHide(entity)
                || com.teslamaps.features.WitherCloakShields.shouldHide(entity)
                || com.teslamaps.features.VisualTweaks.shouldHide(entity)
                || com.teslamaps.features.ZeroHpNametags.shouldHide(entity)
                || com.teslamaps.features.HideGlitchMobs.shouldHide(entity)
                || com.teslamaps.slayer.SlayerNametag.shouldHide(entity)
                || com.teslamaps.features.HideFarEntities.shouldHide(entity)
                || com.teslamaps.slayer.InfernoHelper.shouldHide(entity)
                || com.teslamaps.slayer.SlayerHiders.shouldHide(entity)
                || com.teslamaps.slayer.SlayerSpawnHiders.shouldHide(entity)
                || com.teslamaps.slayer.SeraphHelper.shouldHide(entity)
                || com.teslamaps.features.KeeperWaypoints.shouldHide(entity)
                || com.teslamaps.features.kuudra.KuudraHider.shouldHide(entity)
                || com.teslamaps.features.rift.HorsezookaHider.shouldHide(entity)
                || com.teslamaps.features.SummoningMobs.shouldHide(entity)
                || com.teslamaps.features.rift.CraftRoomHolograms.shouldHide(entity)
                || com.teslamaps.features.kuudra.KuudraQol.shouldHide(entity)
                || com.teslamaps.features.events.JerryPresentHider.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}
