package com.teslamaps.dungeon.puzzle;

// Ported from Skyblocker's WaterboardPreviewer.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.WaterBoardSolver.LeverType;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class WaterboardPreviewer {

    private static final int MIN_X = 6, MAX_X = 24, MIN_Y = 58, MAX_Y = 81, BOARD_Z = 26;
    private static final int ENTRANCE_X = 15, ENTRANCE_Y = 78;
    private static final int WATER_COLOR = 0xFF3AB3DA;

    private static LeverType prospective = null;
    private static List<Vec3[]> lines = List.of();
    private static List<AABB> retract = List.of(), extend = List.of();
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    private WaterboardPreviewer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("WaterboardPreviewer").section("Puzzles & Terminals")
                .when(() -> cfg().solveWaterBoard && cfg().waterBoardPreview)
                .tick(WaterboardPreviewer::tick)
                .world(WaterboardPreviewer::render);
    }

    private static int color(LeverType t) {
        return switch (t) {
            case COAL -> 0xFFB02E26;
            case GOLD -> 0xFFFED83D;
            case QUARTZ -> 0xFF9D9D97;
            case DIAMOND -> 0xFF169C9C;
            case EMERALD -> 0xFF80C71F;
            case TERRACOTTA -> 0xFFF9801D;
            case WATER -> WATER_COLOR;
        };
    }

    private static LeverType fromBlock(Block b) {
        for (LeverType t : LeverType.values()) if (t.block == b) return t;
        return null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !WaterBoardSolver.roomKnown()) {
            clear();
            return;
        }
        prospective = cfg().waterBoardPreviewLevers ? findProspective(mc) : null;

        List<int[][]> path = new ArrayList<>();
        path.add(new int[][]{{ENTRANCE_X, ENTRANCE_Y + 5}, {ENTRANCE_X, ENTRANCE_Y + 3}});
        vertical(mc, ENTRANCE_X, ENTRANCE_Y + 3, path);
        List<Vec3[]> out = new ArrayList<>();
        for (int[][] seg : path) {
            Vec3 head = center(seg[0][0], seg[0][1]);
            Vec3 tail = center(seg[1][0], seg[1][1]);
            if (head == null || tail == null) continue;
            if (prospective == null) {
                out.add(new Vec3[]{head, tail});
                continue;
            }
            Vec3 fwd = tail.subtract(head).normalize();
            double dist = head.distanceTo(tail);
            for (int i = 0; i < dist; i++) {
                out.add(new Vec3[]{head, head.add(fwd.scale(0.3))});
                out.add(new Vec3[]{head.add(fwd.scale(0.7)), head.add(fwd)});
                head = head.add(fwd);
            }
        }
        lines = out;

        List<AABB> r = new ArrayList<>(), e = new ArrayList<>();
        if (prospective != null) {
            for (int x = MIN_X; x <= MAX_X; x++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    BlockPos active = world(x, y, BOARD_Z);
                    BlockPos inactive = world(x, y, BOARD_Z + 1);
                    if (active == null || inactive == null) continue;
                    if (mc.level.getBlockState(active).is(prospective.block)) r.add(new AABB(active));
                    else if (mc.level.getBlockState(inactive).is(prospective.block)) e.add(new AABB(active));
                }
            }
        }
        retract = r;
        extend = e;
    }

    private static void vertical(Minecraft mc, int x, int y, List<int[][]> path) {
        if (!passable(mc, x, y - 1)) return;
        int tail = y - 1;
        while (passable(mc, x, tail - 1)) tail--;
        path.add(new int[][]{{x, y}, {x, tail}});
        horizontal(mc, x, tail, path);
    }

    private static void horizontal(Minecraft mc, int x, int y, List<int[][]> path) {
        if (passable(mc, x, y - 1)) return;
        int left = x, leftSteps = 0;
        while (passable(mc, left + 1, y) && !passable(mc, left, y - 1) && leftSteps < 7) { left++; leftSteps++; }
        int right = x, rightSteps = 0;
        while (passable(mc, right - 1, y) && !passable(mc, right, y - 1) && rightSteps < 7) { right--; rightSteps++; }

        boolean leftDrop = passable(mc, left, y - 1), rightDrop = passable(mc, right, y - 1);
        if (leftDrop && leftSteps <= 5 && (leftSteps < rightSteps || !rightDrop)) {
            path.add(new int[][]{{x, y}, {left, y}});
            vertical(mc, left, y, path);
        } else if (rightDrop && rightSteps <= 5 && (rightSteps < leftSteps || !leftDrop)) {
            path.add(new int[][]{{x, y}, {right, y}});
            vertical(mc, right, y, path);
        } else {
            if (leftSteps > 0) {
                path.add(new int[][]{{x, y}, {left, y}});
                vertical(mc, left, y, path);
            }
            if (rightSteps > 0) {
                path.add(new int[][]{{x, y}, {right, y}});
                vertical(mc, right, y, path);
            }
        }
    }

    private static boolean passable(Minecraft mc, int x, int y) {
        if (x < MIN_X || x > MAX_X || y < MIN_Y || y > MAX_Y) return false;
        int[] w = WaterBoardSolver.toWorld(x, BOARD_Z);
        int[] b = WaterBoardSolver.toWorld(x, BOARD_Z + 1);
        if (w == null || b == null) return false;
        BlockState state = mc.level.getBlockState(M.set(w[0], y, w[1]));
        boolean open = state.isAir() || state.is(Blocks.WATER);
        if (prospective == null) return open;
        boolean isLever = state.is(prospective.block);
        BlockState behind = mc.level.getBlockState(M.set(b[0], y, b[1]));
        return open && !behind.is(prospective.block) || isLever;
    }

    private static LeverType findProspective(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (double d = 0; d < 40; d += 0.1) {
            int bx = (int) Math.floor(eye.x + look.x * d);
            int by = (int) Math.floor(eye.y + look.y * d);
            int bz = (int) Math.floor(eye.z + look.z * d);
            if (bx == lx && by == ly && bz == lz) continue;
            lx = bx; ly = by; lz = bz;
            int[] rel = WaterBoardSolver.toRelative(bx, bz);
            if (rel == null || rel[1] != BOARD_Z) continue;
            if (rel[0] < MIN_X || rel[0] > MAX_X || by < MIN_Y || by > MAX_Y) continue;
            LeverType t = fromBlock(mc.level.getBlockState(M.set(bx, by, bz)).getBlock());
            if (t == null) {
                BlockPos behind = world(rel[0], by, BOARD_Z + 1);
                if (behind != null) t = fromBlock(mc.level.getBlockState(behind).getBlock());
            }
            if (t != null && t != LeverType.WATER) return t;
        }
        return null;
    }

    private static BlockPos world(int x, int y, int z) {
        int[] w = WaterBoardSolver.toWorld(x, z);
        return w == null ? null : new BlockPos(w[0], y, w[1]);
    }

    private static Vec3 center(int x, int y) {
        BlockPos p = world(x, y, BOARD_Z);
        return p == null ? null : Vec3.atCenterOf(p);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (Vec3[] l : lines) ESPRenderer.drawLine(pose, l[0], l[1], WATER_COLOR, 3f, cam);
        LeverType p = prospective;
        if (p == null) return;
        int c = color(p);
        for (AABB b : retract) ESPRenderer.drawBoxOutline(pose, b, c, 2f, cam);
        int fill = (c & 0xFFFFFF) | 0xCC000000;
        for (AABB b : extend) ESPRenderer.drawFilledBox(pose, b, fill, cam);
    }

    private static void clear() {
        prospective = null;
        lines = List.of();
        retract = List.of();
        extend = List.of();
    }
}
