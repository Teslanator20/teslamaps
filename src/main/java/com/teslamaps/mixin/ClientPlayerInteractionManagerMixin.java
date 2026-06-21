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

import com.teslamaps.dungeon.puzzle.ThreeWeirdos;
import com.teslamaps.dungeon.puzzle.WaterBoardSolver;
import com.teslamaps.features.SecretWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void onInteractBlock(net.minecraft.client.player.LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();

        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            ThreeWeirdos.onChestClick(pos);
            SecretWaypoints.onChestOpened(pos);
            SecretWaypoints.onSecretInteract("chest");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (state.is(Blocks.SEA_LANTERN) && com.teslamaps.config.TeslaMapsConfig.get().creeperBeamsDing
                && com.teslamaps.dungeon.puzzle.CreeperBeamsSolver.isActive() && mc.player != null) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
        }

        if (state.is(Blocks.LEVER)) {
            WaterBoardSolver.onLeverClick(pos);
            SecretWaypoints.onSecretInteract("lever");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (block instanceof ButtonBlock) {
            SecretWaypoints.onSecretInteract("button");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }

        if (block instanceof SkullBlock || block instanceof WallSkullBlock) {
            SecretWaypoints.onSecretInteract("skull");
            com.teslamaps.features.SecretClickHighlight.onSecretClick(pos);
        }
    }
}
