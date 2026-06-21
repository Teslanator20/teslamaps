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
import com.teslamaps.render.ESPRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecretClickHighlight {

    private static final long DURATION_MS = 3000;
    private static final int GREEN_FILL = 0x6600FF00, GREEN_LINE = 0xFF00FF00;
    private static final int RED_FILL = 0x66FF0000, RED_LINE = 0xFFFF0000;

    private static final class Flash {
        final BlockPos pos;
        int fill, line;
        long until;
        Flash(BlockPos pos, int fill, int line, long until) { this.pos = pos; this.fill = fill; this.line = line; this.until = until; }
    }

    private static final List<Flash> flashes = new ArrayList<>();
    private static Flash lastClicked; // for the locked->red recolor

    public static void onSecretClick(BlockPos pos) {
        if (!TeslaMapsConfig.get().secretClickHighlight || !DungeonManager.isInDungeon()) return;
        long now = now();
        Flash f = new Flash(pos.immutable(), GREEN_FILL, GREEN_LINE, now + DURATION_MS);
        flashes.add(f);
        lastClicked = f;
    }

    public static void onChestLocked() {
        if (lastClicked == null) return;
        lastClicked.fill = RED_FILL;
        lastClicked.line = RED_LINE;
        lastClicked.until = now() + DURATION_MS;
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().secretClickHighlight || flashes.isEmpty()) return;
        long now = now();
        for (Iterator<Flash> it = flashes.iterator(); it.hasNext(); ) {
            Flash f = it.next();
            if (now >= f.until) { it.remove(); if (f == lastClicked) lastClicked = null; continue; }
            AABB box = new AABB(f.pos.getX(), f.pos.getY(), f.pos.getZ(),
                    f.pos.getX() + 1, f.pos.getY() + 1, f.pos.getZ() + 1);
            ESPRenderer.drawFilledBox(matrices, box, f.fill, cameraPos);
            ESPRenderer.drawBoxOutline(matrices, box, f.line, 3.0f, cameraPos);
        }
    }

    public static void reset() { flashes.clear(); lastClicked = null; }

    private static long now() { return System.currentTimeMillis(); }
}
