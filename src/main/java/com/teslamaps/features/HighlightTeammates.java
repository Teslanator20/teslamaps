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
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class HighlightTeammates {

    private static int classColor(String cls) {
        return switch (cls) {
            case "Archer" -> 0xFFFFA500;
            case "Berserk" -> 0xFFFF5555;
            case "Healer" -> 0xFFFF55FF;
            case "Mage" -> 0xFF55FFFF;
            case "Tank" -> 0xFF55FF55;
            default -> 0xFFFFFFFF;
        };
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().highlightTeammates || !DungeonManager.isInDungeon()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            var dp = PlayerTracker.getPlayer(p.getName().getString());
            if (dp.isEmpty()) continue;
            int color = classColor(dp.get().getDungeonClass());
            ESPRenderer.drawBoxOutline(matrices, p.getBoundingBox(), color, 3.0f, cameraPos, true);
        }
    }
}
