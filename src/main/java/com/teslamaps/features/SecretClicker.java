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

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // Don't click while in a GUI

        // Check delay
        long currentTime = System.currentTimeMillis();
        int delay = TeslaMapsConfig.get().secretClickerDelay;
        int randomization = TeslaMapsConfig.get().secretClickerRandomization;
        int totalDelay = delay + ThreadLocalRandom.current().nextInt(randomization + 1);

        if (currentTime - lastClickTime < totalDelay) return;

        // Check if looking at a block
        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

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
