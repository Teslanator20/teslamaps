package com.teslamaps.dungeon.puzzle;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Arrow Align Solver for F7/M7 Phase 3.
 * Calculates how many clicks needed on each arrow frame to solve.
 */
public class ArrowAlignSolver {

    private static final BlockPos FRAME_GRID_CORNER = new BlockPos(-2, 120, 75);
    private static final BlockPos CENTER_BLOCK = new BlockPos(0, 120, 77);

    // Known solutions (9 possible patterns)
    // -1 = empty frame (no arrow)
    // 0-7 = rotation value
    private static final List<int[]> SOLUTIONS = List.of(
        new int[]{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, -1, -1, 7, 1},
        new int[]{-1, -1, 7, 7, 5, -1, 7, 1, -1, 5, -1, -1, -1, -1, -1, -1, 7, 5, -1, 1, -1, -1, 7, 7, 1},
        new int[]{7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, -1, 7, 5, -1, -1, -1, -1, 5, -1, -1, -1, 3, 3},
        new int[]{5, 3, 3, 3, -1, 5, -1, -1, -1, -1, 7, 7, -1, -1, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, -1},
        new int[]{5, 3, 3, 3, 3, 5, -1, -1, -1, 1, 7, 7, -1, -1, 1, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
        new int[]{7, 7, 7, 7, -1, 1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, -1, 7, 7, 7, 1},
        new int[]{-1, -1, -1, -1, -1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1},
        new int[]{-1, -1, -1, -1, -1, 1, 3, 3, 3, 3, -1, -1, -1, -1, 1, 7, 7, 7, 7, 1, -1, -1, -1, -1, -1},
        new int[]{-1, -1, -1, -1, -1, -1, 1, -1, 1, -1, 7, 1, 7, 1, 3, 1, -1, 1, -1, 1, -1, -1, -1, -1, -1}
    );

    private static int[] currentRotations = null;
    private static int[] targetSolution = null;
    private static final Map<Integer, Integer> clicksNeeded = new HashMap<>();
    private static final Map<Integer, Long> recentClicks = new HashMap<>();
    private static long lastScanTime = 0;

    public static void tick() {
        if (!TeslaMapsConfig.get().solveArrowAlign) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) {
            reset();
            return;
        }

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F7") && !floor.contains("M7"))) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Check if player is near the device
        BlockPos playerPos = mc.player.getBlockPos();
        if (playerPos.getSquaredDistance(CENTER_BLOCK) > 200) {
            currentRotations = null;
            targetSolution = null;
            clicksNeeded.clear();
            return;
        }

        // Scan every 250ms
        long now = System.currentTimeMillis();
        if (now - lastScanTime < 250) return;
        lastScanTime = now;

        scanFrames();
    }

    private static void scanFrames() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // Get all item frames with arrows
        List<ItemFrameEntity> frames = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemFrameEntity frame) {
                if (frame.getHeldItemStack().getItem() == Items.ARROW) {
                    frames.add(frame);
                }
            }
        }

        if (frames.isEmpty()) {
            currentRotations = null;
            targetSolution = null;
            clicksNeeded.clear();
            return;
        }

        // Build current rotation array (5x5 grid = 25 slots)
        int[] rotations = new int[25];
        Arrays.fill(rotations, -1);

        long now = System.currentTimeMillis();
        for (int index = 0; index < 25; index++) {
            BlockPos framePos = getFramePosition(index);

            // If recently clicked, use predicted rotation
            if (recentClicks.containsKey(index) && now - recentClicks.get(index) < 1000) {
                if (currentRotations != null && currentRotations[index] != -1) {
                    rotations[index] = currentRotations[index];
                    continue;
                }
            }

            // Find frame at this position
            for (ItemFrameEntity frame : frames) {
                if (frame.getBlockPos().equals(framePos)) {
                    rotations[index] = frame.getRotation();
                    break;
                }
            }
        }

        currentRotations = rotations;

        // Find matching solution
        clicksNeeded.clear();
        for (int[] solution : SOLUTIONS) {
            boolean matches = true;
            for (int i = 0; i < 25; i++) {
                if ((solution[i] == -1 || rotations[i] == -1) && solution[i] != rotations[i]) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                targetSolution = solution;
                for (int i = 0; i < 25; i++) {
                    if (solution[i] != -1 && rotations[i] != -1) {
                        int clicks = calculateClicks(rotations[i], solution[i]);
                        if (clicks != 0) {
                            clicksNeeded.put(i, clicks);
                        }
                    }
                }
                return;
            }
        }

        targetSolution = null;
    }

    private static int calculateClicks(int current, int target) {
        return (8 - current + target) % 8;
    }

    private static BlockPos getFramePosition(int index) {
        return FRAME_GRID_CORNER.add(0, index % 5, index / 5);
    }

    public static void onFrameClick(int index) {
        if (currentRotations != null && index >= 0 && index < 25 && currentRotations[index] != -1) {
            currentRotations[index] = (currentRotations[index] + 1) % 8;
            recentClicks.put(index, System.currentTimeMillis());

            // Recalculate clicks needed
            if (targetSolution != null && targetSolution[index] != -1) {
                int clicks = calculateClicks(currentRotations[index], targetSolution[index]);
                if (clicks == 0) {
                    clicksNeeded.remove(index);
                } else {
                    clicksNeeded.put(index, clicks);
                }
            }
        }
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveArrowAlign) return;
        if (clicksNeeded.isEmpty()) return;

        for (Map.Entry<Integer, Integer> entry : clicksNeeded.entrySet()) {
            int index = entry.getKey();
            int clicks = entry.getValue();
            if (clicks == 0) continue;

            BlockPos framePos = getFramePosition(index);
            Vec3d textPos = Vec3d.ofCenter(framePos).add(0, 0.1, -0.3);

            char colorCode;
            if (clicks < 3) colorCode = 'a'; // Green
            else if (clicks < 5) colorCode = '6'; // Orange
            else colorCode = 'c'; // Red

            String text = "§" + colorCode + clicks;
            ESPRenderer.drawText(matrices, text, textPos, 1f, cameraPos);
        }
    }

    public static void reset() {
        currentRotations = null;
        targetSolution = null;
        clicksNeeded.clear();
        recentClicks.clear();
    }
}
