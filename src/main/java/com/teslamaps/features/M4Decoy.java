/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file ports the Decoy feature from BirdAddon
 * (https://github.com/BarefootBird/BirdAddon, BSD 3-Clause). See NOTICE.md.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.M4Mobs;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Highlights the best available M4 decoy spot (where to place your decoy). Ported from BirdAddon.
public class M4Decoy {
    private static final AABB SEARCH = new AABB(-36.0, 77.0, -36.0, 47.0, 83.0, 47.0);

    // decoy spots {x, z} in priority order (best first)
    private static final int[][] SPOTS = {
            {26, 29}, {29, 26}, {31, 28}, {27, 32},
            {21, 31}, {32, 21}, {19, 33}, {33, 19},
            {30, 32}, {33, 30}, {27, 35},
            {32, 34}, {35, 31}, {29, 36},
            {34, 24}, {35, 27}, {37, 27},
            {27, 38}, {24, 37}, {21, 36}, {24, 40}
    };

    private static Vec3 bestSpot = null;

    private static boolean isNpc(Entity e) {
        if (e instanceof Zombie || e instanceof Skeleton || e instanceof WitherSkeleton) return true;
        if (e instanceof Player p) {
            String n = p.getGameProfile().name();
            return "Lost Adventurer".equals(n) || "Crypt Souleater".equals(n)
                    || "Crypt Dreadlord".equals(n) || "Diamond Guy".equals(n);
        }
        return false;
    }

    public static void tick() {
        bestSpot = null;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.m4Decoy || !M4Mobs.inM4Boss()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int bestIdx = Integer.MAX_VALUE;
        for (Entity e : mc.level.getEntities((Entity) null, SEARCH)) {
            if (!isNpc(e)) continue;
            for (int i = 0; i < SPOTS.length && i < bestIdx; i++) {
                if (SPOTS[i][0] + 0.5 == e.getX() && SPOTS[i][1] + 0.5 == e.getZ()) {
                    bestIdx = i;
                    bestSpot = e.position();
                }
            }
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().m4Decoy || bestSpot == null) return;
        AABB box = new AABB(bestSpot.x - 0.5, bestSpot.y, bestSpot.z - 0.5,
                bestSpot.x + 0.5, bestSpot.y + 0.05, bestSpot.z + 0.5);
        ESPRenderer.drawFilledBox(matrices, box, 0x66FF5555, cameraPos, true);
    }

    public static void reset() { bestSpot = null; }
}
