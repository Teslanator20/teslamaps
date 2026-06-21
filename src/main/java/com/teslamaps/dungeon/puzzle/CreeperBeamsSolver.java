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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CreeperBeamsSolver {

    private static final int[] PAIR_COLORS = {
        0xFFFFAA00, // Gold
        0xFF55FF55, // Green
        0xFFFF55FF, // Light Purple
        0xFF55FFFF, // Cyan
        0xFFFFFF55, // Yellow
        0xFFAA0000, // Dark Red
        0xFFFFFFFF, // White
        0xFF5555FF  // Blue
    };

    private static List<int[]> lanternPairs;
    private static final List<LanternPair> currentPairs = new ArrayList<>();
    private static String lastRoomName = "";
    private static long lastScanTime = 0;
    private static int detectedRotation = -1;

    private record LanternPair(BlockPos pos1, BlockPos pos2, int color) {}

    static {
        loadSolutions();
    }

    private static void loadSolutions() {
        try {
            InputStream is = CreeperBeamsSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/creeperBeamsSolutions.json");
            if (is != null) {
                Type type = new TypeToken<List<int[]>>(){}.getType();
                lanternPairs = new Gson().fromJson(new InputStreamReader(is), type);
                TeslaMaps.LOGGER.info("[CreeperBeamsSolver] Loaded {} lantern pairs", lanternPairs.size());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[CreeperBeamsSolver] Failed to load solutions", e);
            lanternPairs = List.of();
        }
    }

    public static boolean isActive() {
        return DungeonManager.isInDungeon() && "Creeper Beams".equals(lastRoomName);
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().solveCreeperBeams) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Creeper Beams")) {
            if (!lastRoomName.equals("")) {
                reset();
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScanTime < 500) return;
        lastScanTime = now;

        if (!lastRoomName.equals("Creeper Beams")) {
            lastRoomName = "Creeper Beams";
            detectedRotation = -1; // Reset rotation detection on room enter
        }

        scanLanterns(room);
    }

    private static void scanLanterns(DungeonRoom room) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos corner = room.getCorner();
        if (corner == null) return;

        if (detectedRotation == -1) {
            detectedRotation = detectRotation(corner);
            if (detectedRotation == -1) {
                TeslaMaps.LOGGER.debug("[CreeperBeamsSolver] Could not detect rotation yet");
                return;
            }
            TeslaMaps.LOGGER.info("[CreeperBeamsSolver] Detected rotation: {}", detectedRotation);
        }

        currentPairs.clear();

        int colorIndex = 0;
        for (int[] pair : lanternPairs) {
            if (pair.length < 6) continue;

            BlockPos rel1 = new BlockPos(pair[0], pair[1], pair[2]);
            BlockPos rel2 = new BlockPos(pair[3], pair[4], pair[5]);

            BlockPos world1 = transformPos(rel1, corner, detectedRotation);
            BlockPos world2 = transformPos(rel2, corner, detectedRotation);

            boolean isLantern1 = mc.level.getBlockState(world1).getBlock() == Blocks.SEA_LANTERN;
            boolean isLantern2 = mc.level.getBlockState(world2).getBlock() == Blocks.SEA_LANTERN;

            if (isLantern1 && isLantern2) {
                int color = PAIR_COLORS[colorIndex % PAIR_COLORS.length];
                currentPairs.add(new LanternPair(world1, world2, color));
            }

            colorIndex++;
        }
    }

    private static int detectRotation(BlockPos corner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;

        int bestRotation = -1;
        int bestMatches = 0;

        for (int rotation = 0; rotation < 4; rotation++) {
            int matches = 0;

            for (int[] pair : lanternPairs) {
                if (pair.length < 6) continue;

                BlockPos rel1 = new BlockPos(pair[0], pair[1], pair[2]);
                BlockPos rel2 = new BlockPos(pair[3], pair[4], pair[5]);

                BlockPos world1 = transformPos(rel1, corner, rotation);
                BlockPos world2 = transformPos(rel2, corner, rotation);

                if (mc.level.getBlockState(world1).getBlock() == Blocks.SEA_LANTERN) matches++;
                if (mc.level.getBlockState(world2).getBlock() == Blocks.SEA_LANTERN) matches++;
            }

            if (matches > bestMatches) {
                bestMatches = matches;
                bestRotation = rotation;
            }
        }

        return bestMatches >= 4 ? bestRotation : -1;
    }

    private static BlockPos transformPos(BlockPos relative, BlockPos corner, int rotation) {
        int x = relative.getX();
        int z = relative.getZ();

        int rx, rz;
        switch (rotation) {
            case 1 -> { rx = 30 - z; rz = x; }
            case 2 -> { rx = 30 - x; rz = 30 - z; }
            case 3 -> { rx = z; rz = 30 - x; }
            default -> { rx = x; rz = z; }
        }

        return new BlockPos(corner.getX() + rx, relative.getY(), corner.getZ() + rz);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveCreeperBeams) return;
        if (currentPairs.isEmpty()) return;

        for (LanternPair pair : currentPairs) {
            AABB box1 = new AABB(pair.pos1);
            AABB box2 = new AABB(pair.pos2);

            ESPRenderer.drawESPBox(matrices, box1, pair.color, cameraPos);
            ESPRenderer.drawESPBox(matrices, box2, pair.color, cameraPos);

            if (TeslaMapsConfig.get().creeperBeamsTracers) {
                Vec3 center1 = Vec3.atCenterOf(pair.pos1);
                Vec3 center2 = Vec3.atCenterOf(pair.pos2);
                ESPRenderer.drawLine(matrices, center1, center2, pair.color, 2f, cameraPos);
            }
        }
    }

    public static void reset() {
        currentPairs.clear();
        lastRoomName = "";
        detectedRotation = -1;
    }
}
