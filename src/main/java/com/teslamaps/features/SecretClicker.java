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
package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class SecretClicker {

    private static final Map<BlockPos, Long> clickedPositions = new HashMap<>();
    private static long lastClickTime = 0;

    private static final String[] EXCLUDED_ROOMS = {"Water Board", "Three Weirdos"};

    public static void tick() {
        if (true) return;

        if (!DungeonManager.isInDungeon()) return;
        if (DungeonManager.isInBoss()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // Don't click while in a GUI

        long currentTime = System.currentTimeMillis();
        int delay = TeslaMapsConfig.get().secretClickerDelay;
        int randomization = TeslaMapsConfig.get().secretClickerRandomization;
        int totalDelay = delay + ThreadLocalRandom.current().nextInt(randomization + 1);

        if (currentTime - lastClickTime < totalDelay) return;

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        clickedPositions.entrySet().removeIf(entry -> currentTime - entry.getValue() > 1000);

        if (clickedPositions.containsKey(pos)) return;

        if (!isSecretBlock(state)) return;

        int[] gridPos = ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ());
        if (gridPos != null) {
            DungeonRoom room = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
            if (room != null && room.getName() != null) {
                for (String excluded : EXCLUDED_ROOMS) {
                    if (room.getName().contains(excluded)) return;
                }
            }
        }

        if (mc.gameMode != null) {
            mc.gameMode.useItemOn(
                mc.player,
                mc.player.getUsedItemHand(),
                blockHit
            );

            lastClickTime = currentTime;
            clickedPositions.put(pos, currentTime);

            TeslaMaps.LOGGER.info("[SecretClicker] Clicked {} at {}",
                state.getBlock().getName().getString(), pos);
        }
    }

    private static boolean isSecretBlock(BlockState state) {
        Block block = state.getBlock();

        if (block instanceof LeverBlock) {
            return TeslaMapsConfig.get().secretClickerLevers;
        }

        if (block instanceof ButtonBlock) {
            return TeslaMapsConfig.get().secretClickerButtons;
        }

        if (block instanceof SkullBlock || block instanceof WallSkullBlock) {
            return TeslaMapsConfig.get().secretClickerSkulls;
        }

        if (block instanceof ChestBlock) {
            if (block instanceof TrappedChestBlock) {
                return TeslaMapsConfig.get().secretClickerTrappedChests;
            }
            return TeslaMapsConfig.get().secretClickerChests;
        }

        return false;
    }

    public static void reset() {
        clickedPositions.clear();
        lastClickTime = 0;
    }
}
