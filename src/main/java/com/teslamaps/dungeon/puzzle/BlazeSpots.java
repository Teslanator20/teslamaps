package com.teslamaps.dungeon.puzzle;

// Ported from Devonian's BlazeSolver.kt (efficientPos, etherSpots)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonWaypoints;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BlazeSpots {

    private static DungeonRoom room = null;
    private static long nextScan = 0;
    private static AABB standBox = null;
    private static List<AABB> etherBoxes = List.of();

    private BlazeSpots() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BlazeSpots").section("Puzzles & Terminals")
                .when(() -> cfg().solveBlaze && cfg().blazeSpots)
                .tick(BlazeSpots::tick)
                .world(BlazeSpots::render);
    }

    private static boolean isBlaze(String name) {
        return name != null && (name.equals("Blaze") || name.equals("Higher Blaze") || name.equals("Lower Blaze"));
    }

    private static void tick() {
        if (!DungeonManager.isInDungeon()) { clear(); return; }
        DungeonRoom current = DungeonManager.getCurrentRoom();
        if (current == null || !isBlaze(current.getName())) { clear(); return; }
        if (current != room) {
            clear();
            room = current;
        }
        var st = current.getCheckmarkState();
        if (st == com.teslamaps.map.CheckmarkState.WHITE || st == com.teslamaps.map.CheckmarkState.GREEN) {
            standBox = null;
            etherBoxes = List.of();
            return;
        }
        if (standBox != null) return;
        long now = System.currentTimeMillis();
        if (now < nextScan) return;
        nextScan = now + 1000;

        int[] clay = DungeonWaypoints.scanClayPos(current);
        if (clay == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int[] platform = DungeonWaypoints.relativeToWorld(clay, 15, 14);
        boolean hasPlatform;
        if ("Higher Blaze".equals(current.getName())) hasPlatform = true;
        else if ("Lower Blaze".equals(current.getName())) hasPlatform = false;
        else hasPlatform = mc.level.getBlockState(new BlockPos(platform[0], 118, platform[1])).is(Blocks.COBBLESTONE);

        int[] spot = DungeonWaypoints.relativeToWorld(clay, 20, 11);
        List<AABB> ether = new ArrayList<>();
        ether.add(new AABB(new BlockPos(spot[0], hasPlatform ? 85 : 35, spot[1])));
        if (hasPlatform) ether.add(new AABB(new BlockPos(spot[0], 68, spot[1])));
        etherBoxes = ether;
        standBox = new AABB(new BlockPos(spot[0], hasPlatform ? 103 : 53, spot[1]));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        AABB stand = standBox;
        if (stand == null) return;
        TeslaMapsConfig c = cfg();
        int etherOutline = TeslaMapsConfig.parseColor(c.blazeEtherOutline);
        int etherFill = TeslaMapsConfig.parseColor(c.blazeEtherFill);
        for (AABB b : etherBoxes) {
            ESPRenderer.drawFilledBox(pose, b, etherFill, cam);
            ESPRenderer.drawBoxOutline(pose, b, etherOutline, 2f, cam);
        }
        ESPRenderer.drawFilledBox(pose, stand, TeslaMapsConfig.parseColor(c.blazeSpotFill), cam);
        ESPRenderer.drawBoxOutline(pose, stand, TeslaMapsConfig.parseColor(c.blazeSpotOutline), 2f, cam);
    }

    private static void clear() {
        room = null;
        standBox = null;
        etherBoxes = List.of();
        nextScan = 0;
    }
}
