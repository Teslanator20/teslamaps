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
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TPMazeSolver {

    // Ordered so each consecutive group of 4 is one maze stage; last two are the exit pads.
    private static final BlockPos[] PORTAL_POSITIONS_RELATIVE = {
        new BlockPos(4, 69, 12), new BlockPos(4, 69, 6), new BlockPos(10, 69, 12), new BlockPos(10, 69, 6),
        new BlockPos(4, 69, 20), new BlockPos(4, 69, 14), new BlockPos(10, 69, 20), new BlockPos(10, 69, 14),
        new BlockPos(4, 69, 28), new BlockPos(4, 69, 22), new BlockPos(10, 69, 28), new BlockPos(10, 69, 22),
        new BlockPos(12, 69, 28), new BlockPos(12, 69, 22), new BlockPos(18, 69, 28), new BlockPos(18, 69, 22),
        new BlockPos(20, 69, 28), new BlockPos(20, 69, 22), new BlockPos(26, 69, 28), new BlockPos(26, 69, 22),
        new BlockPos(26, 69, 20), new BlockPos(26, 69, 14), new BlockPos(20, 69, 20), new BlockPos(20, 69, 14),
        new BlockPos(26, 69, 12), new BlockPos(26, 69, 6), new BlockPos(20, 69, 12), new BlockPos(20, 69, 6),
        new BlockPos(15, 69, 14), new BlockPos(15, 69, 12)
    };

    private static List<BlockPos> portalPositions = new ArrayList<>();
    private static List<BlockPos> correctPortals = new ArrayList<>();
    private static Set<BlockPos> visitedPortals = new CopyOnWriteArraySet<>();
    private static BlockPos best = null;
    private static String lastRoomName = "";
    private static int detectedRotation = -1;

    public static void tick() {
        if (!TeslaMapsConfig.get().solveTPMaze || !DungeonManager.isInDungeon()) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Teleport Maze")) {
            if (!lastRoomName.isEmpty()) reset();
            return;
        }

        if (!lastRoomName.equals("Teleport Maze")) {
            lastRoomName = "Teleport Maze";
            detectedRotation = -1;
            initializePortals(room);
        }
    }

    // Driven by the real position packet (ClientPlayNetworkHandlerMixin -> handleMovePlayer), so we
    // act on the exact teleport position/look, not a tick-late distance guess.
    public static void onPositionPacket(Vec3 pos, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        // Mixin injects at HEAD of handleMovePlayer: only run on the main-thread invocation (the
        // network-thread one bails here), and the player is still at its pre-teleport position.
        if (!mc.isSameThread()) return;
        if (!TeslaMapsConfig.get().solveTPMaze || portalPositions.isEmpty()) return;
        if (mc.player == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || !"Teleport Maze".equals(room.getName())) return;

        // Pads sit on the half-block grid; real teleports land exactly centered, server corrections don't.
        if (pos.x % 0.5 != 0.0 || pos.z % 0.5 != 0.0) return;

        AABB posBox = AABB.unitCubeFromLowerCorner(pos).inflate(1.0, 0.0, 1.0);
        AABB playerBox = mc.player.getBoundingBox();
        for (BlockPos portal : portalPositions) {
            if (posBox.intersects(new AABB(portal)) || playerBox.inflate(1.0, 0.0, 1.0).intersects(new AABB(portal))) {
                visitedPortals.add(portal);
            }
        }

        getCorrectPortals(pos, yaw, pitch, playerBox);
        computeBest(pos, yaw, posBox);

        if (TeslaMapsConfig.get().debugMode) {
            TeslaMaps.LOGGER.info("[TPMazeSolver] tp -> {} correct, {} visited, best={}",
                correctPortals.size(), visitedPortals.size(), best);
        }
    }

    private static void getCorrectPortals(Vec3 pos, float yaw, float pitch, AABB playerBox) {
        if (correctPortals.isEmpty()) correctPortals = new ArrayList<>(portalPositions);

        Vec3 look = getLook(yaw, pitch);
        correctPortals = new ArrayList<>(correctPortals.stream()
            .filter(p -> !visitedPortals.contains(p))
            .filter(p -> {
                AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 4.0, p.getZ() + 1.0)
                    .inflate(0.75, 0.0, 0.75);
                return isXZInterceptable(box, 32.0, pos, look)
                    && !new AABB(p).inflate(0.5, 0.0, 0.5).intersects(playerBox);
            })
            .toList());
    }

    // Pick the suggested next portal: within the current 4-pad stage, prefer a still-correct pad
    // (the yaw-aligned one when several remain, instead of an arbitrary index), otherwise the
    // (diagonal/straight per config) pad closest to the current look yaw.
    private static void computeBest(Vec3 pos, float yaw, AABB posBox) {
        BlockPos on = null;
        for (BlockPos p : portalPositions) {
            if (posBox.intersects(new AABB(p))) { on = p; break; }
        }
        if (on == null) return;

        int idx = portalPositions.indexOf(on);
        if (idx == 28 || idx == 29) { best = null; return; } // landed on an exit pad
        int lowest = idx / 4 * 4;
        if (lowest + 4 > portalPositions.size()) return;

        final BlockPos onF = on;
        List<BlockPos> around = portalPositions.subList(lowest, lowest + 4).stream()
            .filter(p -> !p.equals(onF) && !visitedPortals.contains(p))
            .toList();

        List<BlockPos> correctInGroup = around.stream().filter(correctPortals::contains).toList();
        if (!correctInGroup.isEmpty()) {
            best = correctInGroup.stream().min(yawComparator(pos, yaw)).orElse(null);
            return;
        }

        boolean diag = TeslaMapsConfig.get().tpMazePrioritizeDiagonal;
        best = around.stream()
            .filter(p -> diag
                ? (p.getX() != onF.getX() && p.getZ() != onF.getZ())
                : (p.getX() == onF.getX() || p.getZ() == onF.getZ()))
            .min(yawComparator(pos, yaw))
            .orElse(around.isEmpty() ? null : around.get(0));
    }

    private static Comparator<BlockPos> yawComparator(Vec3 pos, float yaw) {
        return Comparator.comparingDouble(p -> {
            Vec3 c = p.getCenter();
            float portalYaw = (float) (Math.atan2(c.z - pos.z, c.x - pos.x) * 180.0 / Math.PI) - 90.0f;
            return Math.abs(Mth.wrapDegrees(portalYaw) - Mth.wrapDegrees(yaw));
        });
    }

    private static Vec3 getLook(float yaw, float pitch) {
        float f2 = -Mth.cos(-pitch * 0.017453292F);
        return new Vec3(
            Mth.sin(-yaw * 0.017453292F - (float) Math.PI) * f2,
            Mth.sin(-pitch * 0.017453292F),
            Mth.cos(-yaw * 0.017453292F - (float) Math.PI) * f2
        );
    }

    // Analytic ray/box test in the XZ plane (Y is irrelevant to maze sightlines): does the look ray
    // from pos cross the box on either its X or Z faces within range? Never skips a thin box.
    private static boolean isXZInterceptable(AABB box, double range, Vec3 pos, Vec3 look) {
        Vec3 goal = pos.add(look.scale(range));
        return inZ(intermediateWithX(pos, goal, box.minX), box)
            || inZ(intermediateWithX(pos, goal, box.maxX), box)
            || inX(intermediateWithZ(pos, goal, box.minZ), box)
            || inX(intermediateWithZ(pos, goal, box.maxZ), box);
    }

    private static boolean inX(Vec3 v, AABB box) { return v != null && v.x >= box.minX && v.x <= box.maxX; }
    private static boolean inZ(Vec3 v, AABB box) { return v != null && v.z >= box.minZ && v.z <= box.maxZ; }

    private static Vec3 intermediateWithX(Vec3 from, Vec3 goal, double x) {
        double dx = goal.x - from.x;
        if (dx * dx < 1e-8) return null;
        double t = (x - from.x) / dx;
        return (t < 0.0 || t > 1.0) ? null
            : new Vec3(from.x + dx * t, from.y + (goal.y - from.y) * t, from.z + (goal.z - from.z) * t);
    }

    private static Vec3 intermediateWithZ(Vec3 from, Vec3 goal, double z) {
        double dz = goal.z - from.z;
        if (dz * dz < 1e-8) return null;
        double t = (z - from.z) / dz;
        return (t < 0.0 || t > 1.0) ? null
            : new Vec3(from.x + (goal.x - from.x) * t, from.y + (goal.y - from.y) * t, from.z + dz * t);
    }

    private static void initializePortals(DungeonRoom room) {
        portalPositions.clear();
        correctPortals.clear();
        visitedPortals.clear();
        best = null;

        BlockPos corner = room.getCorner();
        if (corner == null) return;

        detectedRotation = detectRotation(corner);
        if (detectedRotation == -1) {
            TeslaMaps.LOGGER.warn("[TPMazeSolver] Could not detect rotation, using 0");
            detectedRotation = 0;
        }

        for (BlockPos rel : PORTAL_POSITIONS_RELATIVE) {
            portalPositions.add(transformPos(rel, corner, detectedRotation));
        }
        correctPortals.addAll(portalPositions);

        TeslaMaps.LOGGER.info("[TPMazeSolver] Initialized {} portals at corner {} with rotation {}",
            portalPositions.size(), corner, detectedRotation);
    }

    private static int detectRotation(BlockPos corner) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;

        int bestRotation = -1;
        int bestMatches = 0;
        for (int rotation = 0; rotation < 4; rotation++) {
            int matches = 0;
            for (BlockPos rel : PORTAL_POSITIONS_RELATIVE) {
                if (mc.level.getBlockState(transformPos(rel, corner, rotation)).getBlock() == Blocks.END_PORTAL_FRAME) {
                    matches++;
                }
            }
            if (matches > bestMatches) {
                bestMatches = matches;
                bestRotation = rotation;
            }
        }
        return bestMatches >= 5 ? bestRotation : -1;
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
        if (!TeslaMapsConfig.get().solveTPMaze || portalPositions.isEmpty()) return;

        int colorOne = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeOne);
        int colorMultiple = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeMultiple);
        int colorVisited = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeVisited);
        int colorOther = 0x80FFFFFF; // Semi-transparent white for unvisited

        for (BlockPos portal : portalPositions) {
            int color;
            if (correctPortals.contains(portal)) {
                color = correctPortals.size() == 1 ? colorOne : colorMultiple;
            } else if (visitedPortals.contains(portal)) {
                color = colorVisited;
            } else {
                color = colorOther;
            }
            ESPRenderer.drawFilledBox(matrices, new AABB(portal), color, cameraPos);
        }

        if (best != null && TeslaMapsConfig.get().tpMazeTracer) {
            int tracerColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorTPMazeTracer);
            Vec3 target = new Vec3(best.getX() + 0.5, best.getY() + 0.8, best.getZ() + 0.5);
            ESPRenderer.drawTracerFromCamera(matrices, target, tracerColor, cameraPos);
        }
    }

    public static void reset() {
        portalPositions.clear();
        correctPortals.clear();
        visitedPortals.clear();
        best = null;
        lastRoomName = "";
        detectedRotation = -1;
    }
}
