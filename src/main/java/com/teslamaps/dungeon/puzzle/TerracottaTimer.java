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
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TerracottaTimer {

    private static final CopyOnWriteArrayList<TerracottaSpawn> spawns = new CopyOnWriteArrayList<>();

    private record TerracottaSpawn(BlockPos pos, float timeRemaining) {}

    public static void tick() {
        if (!TeslaMapsConfig.get().terracottaTimer) {
            spawns.clear();
            return;
        }

        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) {
            spawns.clear();
            return;
        }

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F6") && !floor.contains("M6"))) {
            spawns.clear();
            return;
        }

        Iterator<TerracottaSpawn> iter = spawns.iterator();
        while (iter.hasNext()) {
            TerracottaSpawn spawn = iter.next();
            float newTime = spawn.timeRemaining - 0.05f;
            if (newTime <= 0) {
                spawns.remove(spawn);
            } else {
                spawns.set(spawns.indexOf(spawn), new TerracottaSpawn(spawn.pos, newTime));
            }
        }
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!TeslaMapsConfig.get().terracottaTimer) return;
        if (!DungeonManager.isInDungeon() || !DungeonManager.isInBoss()) return;

        String floor = DungeonManager.getFloorName();
        if (floor == null || (!floor.contains("F6") && !floor.contains("M6"))) return;

        if (newState.getBlock() instanceof FlowerPotBlock) {
            for (TerracottaSpawn spawn : spawns) {
                if (spawn.pos.equals(pos)) return;
            }

            float time = floor.contains("M6") ? 12f : 15f;
            spawns.add(new TerracottaSpawn(pos.immutable(), time));
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().terracottaTimer) return;
        if (spawns.isEmpty()) return;

        for (TerracottaSpawn spawn : spawns) {
            String colorCode = getColorCode(spawn.timeRemaining);
            String text = String.format("§%s%.1fs", colorCode, spawn.timeRemaining);
            Vec3 pos = Vec3.atCenterOf(spawn.pos);
            ESPRenderer.drawText(matrices, text, pos, 2f, cameraPos);
        }
    }

    private static String getColorCode(float time) {
        if (time > 5f) return "a"; // Green
        if (time > 2f) return "6"; // Orange
        return "c"; // Red
    }

    public static void reset() {
        spawns.clear();
    }
}
