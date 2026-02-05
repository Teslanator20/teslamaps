package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.scanner.ComponentGrid;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Secret Clicker - Automatically clicks secret blocks when looking at them.
 *
 * Secrets in dungeons include:
 * - Levers
 * - Buttons (stone/wood)
 * - Skulls (wither skulls)
 * - Chests (regular and trapped)
 */
public class SecretClicker {

    // Track clicked positions to avoid spam clicking the same block
    private static final Map<BlockPos, Long> clickedPositions = new HashMap<>();
    private static long lastClickTime = 0;

    // Rooms to exclude (puzzles that shouldn't be auto-clicked)
    private static final String[] EXCLUDED_ROOMS = {"Water Board", "Three Weirdos"};

    public static void tick() {
        // DISABLED - Feature commented out for now
        if (true) return;

        /* DISABLED
        if (!TeslaMapsConfig.get().secretClicker) return;
        */
        if (!DungeonManager.isInDungeon()) return;
        if (DungeonManager.isInBoss()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return; // Don't click while in a GUI

        // Check delay
        long currentTime = System.currentTimeMillis();
        int delay = TeslaMapsConfig.get().secretClickerDelay;
        int randomization = TeslaMapsConfig.get().secretClickerRandomization;
        int totalDelay = delay + ThreadLocalRandom.current().nextInt(randomization + 1);

        if (currentTime - lastClickTime < totalDelay) return;

        // Check if looking at a block
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);

        // Clean up old clicked positions (older than 1 second)
        clickedPositions.entrySet().removeIf(entry -> currentTime - entry.getValue() > 1000);

        // Check if already clicked recently
        if (clickedPositions.containsKey(pos)) return;

        // Check if it's a secret block
        if (!isSecretBlock(state)) return;

        // Check if we're in an excluded room (Water Board, Three Weirdos)
        int[] gridPos = ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ());
        if (gridPos != null) {
            DungeonRoom room = DungeonManager.getRoomAt(gridPos[0], gridPos[1]);
            if (room != null && room.getName() != null) {
                for (String excluded : EXCLUDED_ROOMS) {
                    if (room.getName().contains(excluded)) return;
                }
            }
        }

        // Perform the click (right click)
        if (mc.interactionManager != null) {
            mc.interactionManager.interactBlock(
                mc.player,
                mc.player.getActiveHand(),
                blockHit
            );

            lastClickTime = currentTime;
            clickedPositions.put(pos, currentTime);

            TeslaMaps.LOGGER.info("[SecretClicker] Clicked {} at {}",
                state.getBlock().getName().getString(), pos);
        }
    }

    /**
     * Check if a block is a secret block (lever, button, skull, chest).
     */
    private static boolean isSecretBlock(BlockState state) {
        Block block = state.getBlock();

        // Levers
        if (block instanceof LeverBlock) {
            return TeslaMapsConfig.get().secretClickerLevers;
        }

        // Buttons
        if (block instanceof ButtonBlock) {
            return TeslaMapsConfig.get().secretClickerButtons;
        }

        // Skulls (wither skulls are secrets)
        if (block instanceof SkullBlock || block instanceof WallSkullBlock) {
            return TeslaMapsConfig.get().secretClickerSkulls;
        }

        // Chests
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
