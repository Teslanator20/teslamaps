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
        com.teslamaps.config.TeslaMapsConfig cfg = com.teslamaps.config.TeslaMapsConfig.get();
        boolean noLightning = cfg.noLightning && entity instanceof net.minecraft.world.entity.LightningBolt;
        boolean noFalling = cfg.noFallingBlocks && entity instanceof net.minecraft.world.entity.item.FallingBlockEntity;
        boolean noXpOrb = cfg.noXpOrbs && entity instanceof net.minecraft.world.entity.ExperienceOrb;
        boolean dyingDragon = cfg.hideDyingDragons
                && entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                && dragon.getHealth() <= 0;
        if (noLightning || noFalling || noXpOrb || dyingDragon
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
                || com.teslamaps.features.DungeonCleanEnd.shouldHide(entity)
                || com.teslamaps.features.SecretPickupHud.shouldHide(entity)
                || com.teslamaps.features.MinionFeatures.shouldHide(entity)
                || com.teslamaps.features.RenderHiders.shouldHide(entity)
                || com.teslamaps.features.SkeletonHelmet.shouldHide(entity)
                || com.teslamaps.features.CommonSeaCreatureHider.shouldHide(entity)
                || com.teslamaps.features.ChumBucketHider.shouldHide(entity)
                || com.teslamaps.features.ArachneTagHider.shouldHide(entity)
                || com.teslamaps.features.WitherCloakShields.shouldHide(entity)
                || com.teslamaps.features.HideGlitchMobs.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}
