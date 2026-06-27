/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Boxes the F7 Wither bosses (Maxor/Storm/Goldor/Necron — WitherBoss entities). Depth Check off = through walls.
public class BossESP {

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.bossESP) return;
        if (!DungeonManager.isInDungeon()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int color = TeslaMapsConfig.parseColor(c.colorBossESP);
        boolean throughWalls = !c.bossESPDepthCheck;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof WitherBoss wb) || wb.isInvisible()) continue;
            AABB box = wb.getBoundingBox();
            if (c.bossESPFilled) {
                ESPRenderer.drawFilledBox(matrices, box, color, cameraPos, throughWalls);
            } else {
                ESPRenderer.drawBoxOutline(matrices, box, color, 2.0f, cameraPos, throughWalls);
            }
        }
    }
}
