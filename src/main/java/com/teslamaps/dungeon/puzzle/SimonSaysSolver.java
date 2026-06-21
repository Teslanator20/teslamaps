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
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SimonSaysSolver {

    private static final BlockPos START_BUTTON = new BlockPos(110, 121, 91);

    private static final Set<BlockPos> GRID = Set.of(
        new BlockPos(110, 123, 92), new BlockPos(110, 123, 93), new BlockPos(110, 123, 94), new BlockPos(110, 123, 95),
        new BlockPos(110, 122, 92), new BlockPos(110, 122, 93), new BlockPos(110, 122, 94), new BlockPos(110, 122, 95),
        new BlockPos(110, 121, 92), new BlockPos(110, 121, 93), new BlockPos(110, 121, 94), new BlockPos(110, 121, 95),
        new BlockPos(110, 120, 92), new BlockPos(110, 120, 93), new BlockPos(110, 120, 94), new BlockPos(110, 120, 95)
    );

    private static final List<BlockPos> clickSequence = new ArrayList<>();
    private static int clickIndex = 0;

    public static int getClicked() { return clickIndex; }
    public static int getSequenceSize() { return clickSequence.size(); }
    private static boolean firstPhase = true;
    private static int lastLanternTick = -1;
    private static int startClickCount = 0;
    private static long lastTickTime = 0;

    public static void tick() {
        if (!TeslaMapsConfig.get().solveSimonSays) {
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
        if (playerPos.distToLowCornerSqr(110, 121, 91) > 400) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTickTime < 50) return;
        lastTickTime = now;

        if (lastLanternTick >= 0) {
            lastLanternTick++;
            if (lastLanternTick > 10) {
                int buttonCount = 0;
                for (BlockPos pos : GRID) {
                    if (mc.level.getBlockState(pos).getBlock() == Blocks.STONE_BUTTON) {
                        buttonCount++;
                    }
                }
                if (buttonCount > 8) {
                    TeslaMaps.LOGGER.info("[SimonSays] Grid reset detected");
                    firstPhase = false;
                    startClickCount = 0;
                }
            }
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().solveSimonSays) return;
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (pos.equals(START_BUTTON)) {
            if (newState.getBlock() == Blocks.STONE_BUTTON &&
                newState.hasProperty(BlockStateProperties.POWERED) && newState.getValue(BlockStateProperties.POWERED)) {
                clickSequence.clear();
                clickIndex = 0;
                firstPhase = true;
                lastLanternTick = -1;
                TeslaMaps.LOGGER.info("[SimonSays] Start button pressed, resetting");
            }
            return;
        }

        if (pos.getX() == 111 && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 92 && pos.getZ() <= 95) {
            if (oldState.getBlock() == Blocks.SEA_LANTERN && newState.getBlock() == Blocks.OBSIDIAN) {
                if (!clickSequence.contains(pos)) {
                    clickSequence.add(pos.immutable());
                    lastLanternTick = 0;
                    TeslaMaps.LOGGER.info("[SimonSays] Added to sequence: {} (total: {})", pos, clickSequence.size());

                    if (firstPhase) {
                        if (clickSequence.size() == 2) {
                            BlockPos first = clickSequence.get(0);
                            clickSequence.set(0, clickSequence.get(1));
                            clickSequence.set(1, first);
                        } else if (clickSequence.size() == 3) {
                            clickSequence.remove(1);
                        }
                    }
                }
            }
        }

        if (pos.getX() == 110 && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 92 && pos.getZ() <= 95) {
            if (oldState.getBlock() == Blocks.STONE_BUTTON &&
                newState.hasProperty(BlockStateProperties.POWERED) && newState.getValue(BlockStateProperties.POWERED)) {
                BlockPos lanternPos = pos.east(); // x+1 is the lantern position
                int index = clickSequence.indexOf(lanternPos);
                if (index >= 0) {
                    clickIndex = index + 1;
                    if (clickIndex >= clickSequence.size()) {
                        clickSequence.clear();
                        clickIndex = 0;
                        firstPhase = false;
                        TeslaMaps.LOGGER.info("[SimonSays] Round complete!");
                    }
                }
            } else if (newState.isAir()) {
                clickSequence.clear();
                clickIndex = 0;
            }
        }
    }

    public static void onChatMessage(String message) {
        if (message.equals("[BOSS] Goldor: Who dares trespass into my domain?")) {
            startClickCount = 0;
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveSimonSays) return;
        if (clickSequence.isEmpty() || clickIndex >= clickSequence.size()) return;

        int firstColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSimonSaysFirst);
        int secondColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSimonSaysSecond);
        int thirdColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorSimonSaysThird);

        for (int i = clickIndex; i < clickSequence.size(); i++) {
            BlockPos lanternPos = clickSequence.get(i);
            BlockPos buttonPos = lanternPos.west();

            int color = switch (i - clickIndex) {
                case 0 -> firstColor;
                case 1 -> secondColor;
                default -> thirdColor;
            };

            AABB box = new AABB(
                buttonPos.getX() + 0.05, buttonPos.getY() + 0.37, buttonPos.getZ() + 0.3,
                buttonPos.getX() - 0.15, buttonPos.getY() + 0.63, buttonPos.getZ() + 0.7
            );
            ESPRenderer.drawESPBox(matrices, box, color, cameraPos);
        }
    }

    public static void reset() {
        clickSequence.clear();
        clickIndex = 0;
        firstPhase = true;
        lastLanternTick = -1;
        startClickCount = 0;
    }
}
