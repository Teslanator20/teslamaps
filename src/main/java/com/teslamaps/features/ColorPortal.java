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

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonScore;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class ColorPortal {

    private static final List<BlockPos> portals = new ArrayList<>();
    private static int scanCd = 0;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!TeslaMapsConfig.get().colorPortal || mc.player == null || mc.level == null
                || !DungeonManager.isInDungeon() || DungeonManager.isInBoss()) {
            portals.clear();
            return;
        }
        if (--scanCd > 0) return;
        scanCd = 20; // rescan ~1x/s

        portals.clear();
        BlockPos p = mc.player.blockPosition();
        for (int x = -10; x <= 10; x++)
            for (int y = -6; y <= 6; y++)
                for (int z = -10; z <= 10; z++) {
                    BlockPos bp = p.offset(x, y, z);
                    if (mc.level.getBlockState(bp).is(Blocks.NETHER_PORTAL)) portals.add(bp.immutable());
                }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().colorPortal || portals.isEmpty()) return;
        int color = colorForScore(DungeonScore.getScore());
        for (BlockPos bp : portals) {
            ESPRenderer.drawFilledBox(matrices, new AABB(bp), color, cameraPos);
        }
    }

    private static int colorForScore(int score) {
        if (score < 270) return 0xA0FF1010;   // red
        if (score < 300) return 0xA0FFD700;   // gold (S)
        return 0xA040FF00;                     // green (S+)
    }
}
