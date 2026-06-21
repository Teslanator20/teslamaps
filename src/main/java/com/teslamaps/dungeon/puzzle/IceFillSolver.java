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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonWaypoints;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class IceFillSolver {

    private static List<List<List<int[]>>> identifier, easy, hard;

    private static final List<Vec3> currentPath = new ArrayList<>();
    private static DungeonRoom iceRoom = null;
    private static int[] clay = null;
    private static boolean solved = false;

    static { load(); }

    private static void load() {
        try (InputStream is = IceFillSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/iceFillFloors.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            identifier = parseSection(root.getAsJsonArray("identifier"));
            easy = parseSection(root.getAsJsonArray("easy"));
            hard = parseSection(root.getAsJsonArray("hard"));
            TeslaMaps.LOGGER.info("[IceFill] Loaded {} floors", identifier.size());
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[IceFill] Failed to load patterns", e);
            identifier = List.of(); easy = List.of(); hard = List.of();
        }
    }

    private static List<List<List<int[]>>> parseSection(JsonArray floors) {
        List<List<List<int[]>>> out = new ArrayList<>();
        for (var floorEl : floors) {
            List<List<int[]>> floor = new ArrayList<>();
            for (var patEl : floorEl.getAsJsonArray()) {
                List<int[]> pattern = new ArrayList<>();
                for (var ptEl : patEl.getAsJsonArray()) {
                    JsonObject o = ptEl.getAsJsonObject();
                    pattern.add(new int[]{o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()});
                }
                floor.add(pattern);
            }
            out.add(floor);
        }
        return out;
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().solveIceFill || !DungeonManager.isInDungeon()) { reset(); return; }

        if (iceRoom == null && DungeonManager.getGrid() != null) {
            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                if (room != null && "Ice Fill".equals(room.getName())) { iceRoom = room; break; }
            }
        }
        if (iceRoom == null || solved) return;

        clay = DungeonWaypoints.scanClayPos(iceRoom);
        if (clay == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<List<List<int[]>>> patterns = TeslaMapsConfig.get().iceFillOptimized ? hard : easy;
        currentPath.clear();
        for (int floor = 0; floor < identifier.size() && floor < patterns.size(); floor++) {
            List<List<int[]>> floorIds = identifier.get(floor);
            for (int pi = 0; pi < floorIds.size(); pi++) {
                if (isAir(floorIds.get(pi).get(0)) && !isAir(floorIds.get(pi).get(1))) {
                    for (int[] pt : patterns.get(floor).get(pi)) {
                        int[] xz = DungeonWaypoints.relativeToWorld(clay, pt[0], pt[2]);
                        currentPath.add(new Vec3(xz[0] + 0.5, pt[1] + 0.1, xz[1] + 0.5));
                    }
                    break;
                }
            }
        }
        solved = true;
    }

    private static boolean isAir(int[] p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        int[] xz = DungeonWaypoints.relativeToWorld(clay, p[0], p[2]);
        return mc.level.getBlockState(new BlockPos(xz[0], p[1], xz[1])).isAir();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveIceFill || currentPath.size() < 2) return;
        DungeonRoom cur = DungeonManager.getCurrentRoom();
        if (cur == null || !"Ice Fill".equals(cur.getName())) return;

        int color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorIceFill);
        for (int i = 0; i + 1 < currentPath.size(); i++) {
            ESPRenderer.drawLine(matrices, currentPath.get(i), currentPath.get(i + 1), color, 3f, cameraPos);
        }
    }

    public static void reset() {
        currentPath.clear();
        iceRoom = null;
        clay = null;
        solved = false;
    }
}
