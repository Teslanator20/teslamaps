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

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SpiritBearTimer {

    private static final Set<BlockPos> F4_POSITIONS = Set.of(
        new BlockPos(-3, 77, 33), new BlockPos(-9, 77, 31), new BlockPos(-16, 77, 26),
        new BlockPos(-20, 77, 20), new BlockPos(-23, 77, 13), new BlockPos(-24, 77, 6),
        new BlockPos(-24, 77, 0), new BlockPos(-22, 77, -7), new BlockPos(-18, 77, -13),
        new BlockPos(-12, 77, -19), new BlockPos(-5, 77, -22), new BlockPos(1, 77, -24),
        new BlockPos(8, 77, -24), new BlockPos(14, 77, -23), new BlockPos(21, 77, -19),
        new BlockPos(27, 77, -14), new BlockPos(31, 77, -8), new BlockPos(33, 77, -1),
        new BlockPos(34, 77, 5), new BlockPos(33, 77, 12), new BlockPos(31, 77, 19),
        new BlockPos(27, 77, 25), new BlockPos(20, 77, 30), new BlockPos(14, 77, 33),
        new BlockPos(7, 77, 34)
    );

    private static final Set<BlockPos> M4_POSITIONS = Set.of(
        new BlockPos(-2, 77, 33), new BlockPos(-7, 77, 32), new BlockPos(-13, 77, 28),
        new BlockPos(-17, 77, 24), new BlockPos(-21, 77, 18), new BlockPos(-23, 77, 13),
        new BlockPos(-24, 77, 7), new BlockPos(-24, 77, 2), new BlockPos(-23, 77, -4),
        new BlockPos(-21, 77, -9), new BlockPos(-17, 77, -14), new BlockPos(-12, 77, -19),
        new BlockPos(-6, 77, -22), new BlockPos(-1, 77, -23), new BlockPos(5, 77, -24),
        new BlockPos(10, 77, -24), new BlockPos(16, 77, -22), new BlockPos(21, 77, -19),
        new BlockPos(27, 77, -15), new BlockPos(30, 77, -10), new BlockPos(32, 77, -5),
        new BlockPos(34, 77, 1), new BlockPos(34, 77, 7), new BlockPos(33, 77, 12),
        new BlockPos(31, 77, 18), new BlockPos(28, 77, 23), new BlockPos(23, 77, 28),
        new BlockPos(18, 77, 31), new BlockPos(12, 77, 33), new BlockPos(7, 77, 34)
    );

    private static final BlockPos LAST_POSITION = new BlockPos(7, 77, 34);

    private static int kills = 0;
    private static int timer = -1; // -1 = not spawning, 0 = alive, >0 = ticks until spawn
    private static String lastFloor = "";

    public static void tick() {
        if (!TeslaMapsConfig.get().spiritBearTimer) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) {
            reset();
            return;
        }

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F4") && !floor.contains("M4"))) {
            reset();
            return;
        }

        lastFloor = floor;

        if (timer > 0) {
            timer--;
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().spiritBearTimer) return;
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) return;

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F4") && !floor.contains("M4"))) return;

        Set<BlockPos> positions = floor.contains("M4") ? M4_POSITIONS : F4_POSITIONS;
        if (!positions.contains(pos)) return;

        if (oldState.getBlock() == Blocks.COAL_BLOCK && newState.getBlock() == Blocks.SEA_LANTERN) {
            int maxKills = floor.contains("M4") ? 30 : 25;
            if (kills < maxKills) {
                kills++;
            }
            if (pos.equals(LAST_POSITION)) {
                timer = 68; // 3.4 seconds
            }
        }
        else if (oldState.getBlock() == Blocks.SEA_LANTERN && newState.getBlock() == Blocks.COAL_BLOCK) {
            if (kills > 0) {
                kills--;
            }
            if (pos.equals(LAST_POSITION)) {
                timer = -1;
            }
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!TeslaMapsConfig.get().spiritBearTimer) return;
        if (lastFloor.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int maxKills = lastFloor.contains("M4") ? 30 : 25;

        String text;
        if (timer < 0) {
            text = String.format("§6Spirit Bear: §d%d/%d", kills, maxKills);
        } else if (timer > 0) {
            text = String.format("§6Spirit Bear: §e%.2fs", timer / 20f);
        } else {
            text = "§6Spirit Bear: §aAlive!";
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int x = (screenWidth - mc.font.width(text)) / 2;
        int y = mc.getWindow().getGuiScaledHeight() - 50;

        context.text(mc.font, text, x, y, 0xFFFFFFFF);
    }

    public static void reset() {
        kills = 0;
        timer = -1;
        lastFloor = "";
    }

    public static boolean isActive() {
        return !lastFloor.isEmpty() && TeslaMapsConfig.get().spiritBearTimer;
    }
}
