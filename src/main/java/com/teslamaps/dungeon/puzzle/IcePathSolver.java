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
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

public class IcePathSolver {

    private static final int[][] SOLUTION = {
        {8, 9, 12, 9}, {12, 9, 12, 8}, {12, 8, 20, 8}, {20, 8, 20, 24},
        {20, 24, 19, 24}, {19, 24, 19, 23}, {19, 23, 21, 23}, {21, 23, 21, 14},
        {21, 14, 14, 14}, {14, 14, 14, 25}
    };

    private record Seg(double x1, double z1, double x2, double z2) {}

    private static final Deque<Seg> path = new ArrayDeque<>();
    private static int cornerX, cornerZ, rotation;
    private static boolean inPath = false;

    public static void tick() {
        if (!TeslaMapsConfig.get().icePathSolver || !DungeonManager.isInDungeon()) { reset(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Ice Path")) {
            if (inPath) reset();
            return;
        }

        if (!inPath) {
            rotation = room.getRotation();
            if (rotation < 0) return;
            cornerX = room.getCornerX();
            cornerZ = room.getCornerZ();
            inPath = true;
            path.clear();
            for (int[] s : SOLUTION) {
                int[] a = fromComp(s[0], s[1]);
                int[] b = fromComp(s[2], s[3]);
                path.add(new Seg(a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5));
            }
        }

        Silverfish fish = null;
        double best = Double.MAX_VALUE;
        for (Silverfish s : mc.level.getEntitiesOfClass(Silverfish.class, mc.player.getBoundingBox().inflate(40))) {
            double d = s.distanceToSqr(mc.player);
            if (d < best) { best = d; fish = s; }
        }
        if (fish == null || fish.isDeadOrDying() || path.isEmpty()) return;

        Seg cur = path.peekFirst();
        if (Math.abs(fish.getX() - cur.x2) + Math.abs(fish.getZ() - cur.z2) < 0.8) path.pollFirst();
    }

    private static int[] rotatePos(int x, int z, int degree) {
        return switch (degree % 360) {
            case 90 -> new int[]{z, -x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    private static int[] fromComp(int x, int z) {
        int[] r = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{r[0] + cornerX, r[1] + cornerZ};
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().icePathSolver || path.isEmpty()) return;
        int i = 0;
        for (Seg s : path) {
            int color = i == 0 ? 0xFF00FF00 : 0xFFFF0000;
            ESPRenderer.drawLine(matrices, new Vec3(s.x1, 67.0, s.z1), new Vec3(s.x2, 67.0, s.z2), color, 3f, cameraPos);
            i++;
        }
    }

    public static void reset() {
        path.clear();
        inPath = false;
    }
}
