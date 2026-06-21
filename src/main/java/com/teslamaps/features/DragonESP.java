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
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.WitherDragons;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DragonESP {

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.dragonBoxes && !c.dragonHealth && !c.witherHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (!DungeonManager.isInDungeon() || f == null || f.getLevel() != 7) return;

        if (c.witherHighlight) {
            int wc = TeslaMapsConfig.parseColor(c.witherHighlightColor);
            for (net.minecraft.world.entity.boss.wither.WitherBoss w :
                    mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.wither.WitherBoss.class,
                            mc.player.getBoundingBox().inflate(60))) {
                AABB wb = w.getBoundingBox();
                if (c.witherHighlightBox) ESPRenderer.drawFilledBox(matrices, wb, (wc & 0xFFFFFF) | 0x66000000, cameraPos);
                else ESPRenderer.drawBoxOutline(matrices, wb, wc, 3.0f, cameraPos);
            }
        }

        for (EnderDragon d : mc.level.getEntitiesOfClass(EnderDragon.class, mc.player.getBoundingBox().inflate(150))) {
            if (d.getHealth() <= 0) continue; // dying -> skip
            int color = nearestColor(d.position());
            AABB box = d.getBoundingBox();
            if (c.dragonBoxes) ESPRenderer.drawBoxOutline(matrices, box, color, 4.0f, cameraPos);
            if (c.dragonHealth) {
                int pct = (int) (d.getMaxHealth() > 0 ? d.getHealth() / d.getMaxHealth() * 100 : 0);
                String hpColor = pct > 50 ? "§a" : pct > 25 ? "§e" : "§c";
                Vec3 top = new Vec3(d.getX(), box.maxY + 1.0, d.getZ());
                ESPRenderer.drawText(matrices, hpColor + pct + "%", top, 1.4f, cameraPos);
            }
        }
    }

    private static int nearestColor(Vec3 pos) {
        WitherDragons.Dragon best = null;
        double bestD = Double.MAX_VALUE;
        for (WitherDragons.Dragon dr : WitherDragons.Dragon.values()) {
            double dx = dr.spawnPos.getX() - pos.x, dy = dr.spawnPos.getY() - pos.y, dz = dr.spawnPos.getZ() - pos.z;
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist < bestD) { bestD = dist; best = dr; }
        }
        return best == null ? 0xFFFFFFFF : (0xFF000000 | (best.colorArgb & 0xFFFFFF));
    }
}
