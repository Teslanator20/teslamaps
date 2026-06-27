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
        com.teslamaps.config.TeslaMapsConfig cfg = com.teslamaps.config.TeslaMapsConfig.get();
        boolean noLightning = cfg.noLightning && entity instanceof net.minecraft.world.entity.LightningBolt;
        boolean noFalling = cfg.noFallingBlocks && entity instanceof net.minecraft.world.entity.item.FallingBlockEntity;
        boolean noXpOrb = cfg.noXpOrbs && entity instanceof net.minecraft.world.entity.ExperienceOrb;
        boolean dyingDragon = cfg.hideDyingDragons
                && entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                && dragon.getHealth() <= 0;
        boolean deadMob = cfg.hideDeadMobs
                && entity instanceof net.minecraft.world.entity.LivingEntity le
                && !(entity instanceof net.minecraft.world.entity.player.Player)
                && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && (le.isDeadOrDying() || le.deathTime > 0 || le.getHealth() <= 0.0f); // Hypixel keeps dying mobs at 0 HP (red) for ~0.5s without a client death animation
        if (noLightning || noFalling || noXpOrb || dyingDragon || deadMob
                || com.teslamaps.features.HideCheapCoins.shouldHide(entity)
                || HidePlayers.shouldHide(entity)
                || com.teslamaps.features.SoulweaverHider.shouldHide(entity)
                || com.teslamaps.dungeon.puzzle.DungeonBlaze.shouldHideBlaze(entity)) {
            cir.setReturnValue(false);
        }
    }
}
