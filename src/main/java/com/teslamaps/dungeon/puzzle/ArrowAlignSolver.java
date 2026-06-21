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
package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ArrowAlignSolver {

    private static final BlockPos FRAME_GRID_CORNER = new BlockPos(-2, 120, 75);
    private static final BlockPos CENTER_BLOCK = new BlockPos(0, 120, 77);

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

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        if (playerPos.distSqr(CENTER_BLOCK) > 200) {
            currentRotations = null;
            targetSolution = null;
            clicksNeeded.clear();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScanTime < 250) return;
        lastScanTime = now;

        scanFrames();
    }

    private static void scanFrames() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<ItemFrame> frames = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemFrame frame) {
                if (frame.getItem().getItem() == Items.ARROW) {
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

        int[] rotations = new int[25];
        Arrays.fill(rotations, -1);

        long now = System.currentTimeMillis();
        for (int index = 0; index < 25; index++) {
            BlockPos framePos = getFramePosition(index);

            if (recentClicks.containsKey(index) && now - recentClicks.get(index) < 1000) {
                if (currentRotations != null && currentRotations[index] != -1) {
                    rotations[index] = currentRotations[index];
                    continue;
                }
            }

            for (ItemFrame frame : frames) {
                if (frame.blockPosition().equals(framePos)) {
                    rotations[index] = frame.getRotation();
                    break;
                }
            }
        }

        currentRotations = rotations;

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
        return FRAME_GRID_CORNER.offset(0, index % 5, index / 5);
    }

    public static void onFrameClick(int index) {
        if (currentRotations != null && index >= 0 && index < 25 && currentRotations[index] != -1) {
            currentRotations[index] = (currentRotations[index] + 1) % 8;
            recentClicks.put(index, System.currentTimeMillis());

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

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveArrowAlign) return;
        if (clicksNeeded.isEmpty()) return;

        for (Map.Entry<Integer, Integer> entry : clicksNeeded.entrySet()) {
            int index = entry.getKey();
            int clicks = entry.getValue();
            if (clicks == 0) continue;

            BlockPos framePos = getFramePosition(index);
            Vec3 textPos = Vec3.atCenterOf(framePos).add(0, 0.1, -0.3);

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
