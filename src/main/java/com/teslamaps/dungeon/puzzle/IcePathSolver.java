package com.teslamaps.dungeon.puzzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class IcePathSolver {

    private static final int[][] SOLUTION = {
        {8, 9, 12, 9}, {12, 9, 12, 8}, {12, 8, 20, 8}, {20, 8, 20, 24},
        {20, 24, 19, 24}, {19, 24, 19, 23}, {19, 23, 21, 23}, {21, 23, 21, 14},
        {21, 14, 14, 14}, {14, 14, 14, 25}
    };

    // board as in Skyblocker's IcePath: origin (23, 67, 24), rows run -z, columns -x
    private static final int N = 17;
    private static final boolean[][] board = new boolean[N][N];
    private static int fishRow = -1, fishCol = -1;

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

        boolean dynamic = TeslaMapsConfig.get().icePathDynamic;
        if (!inPath) {
            rotation = room.getRotation();
            if (rotation < 0) return;
            cornerX = room.getCornerX();
            cornerZ = room.getCornerZ();
            inPath = true;
            path.clear();
            fishRow = fishCol = -1;
            if (!dynamic) {
                for (int[] s : SOLUTION) {
                    int[] a = fromComp(s[0], s[1]);
                    int[] b = fromComp(s[2], s[3]);
                    path.add(new Seg(a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5));
                }
            }
        }

        Silverfish fish = null;
        double best = Double.MAX_VALUE;
        for (Silverfish s : mc.level.getEntitiesOfClass(Silverfish.class, mc.player.getBoundingBox().inflate(40))) {
            double d = s.distanceToSqr(mc.player);
            if (d < best) { best = d; fish = s; }
        }
        if (fish == null || fish.isDeadOrDying()) return;

        if (dynamic) {
            tickDynamic(mc, fish);
            return;
        }
        if (path.isEmpty()) return;

        Seg cur = path.peekFirst();
        if (Math.abs(fish.getX() - cur.x2) + Math.abs(fish.getZ() - cur.z2) < 0.8) path.pollFirst();
    }

    private static void tickDynamic(Minecraft mc, Silverfish fish) {
        boolean changed = false;
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < N; col++) {
                int[] w = fromComp(23 - col, 24 - row);
                boolean block = !mc.level.getBlockState(new BlockPos(w[0], 67, w[1])).isAir();
                if (board[row][col] != block) {
                    board[row][col] = block;
                    changed = true;
                }
            }
        }

        BlockPos fp = fish.blockPosition();
        int[] rel = toComp(fp.getX(), fp.getZ());
        int row = 24 - rel[1], col = 23 - rel[0];
        if (row < 0 || row >= N || col < 0 || col >= N) return;
        if (row != fishRow || col != fishCol) {
            fishRow = row;
            fishCol = col;
            changed = true;
        }
        if (changed) solve();
    }

    private static void solve() {
        boolean[][] seen = new boolean[N][N];
        int[][] parent = new int[N * N][];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{fishRow, fishCol});
        seen[fishRow][fishCol] = true;
        int[] goal = null;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (p[0] == 0 && p[1] >= 7 && p[1] <= 9) { goal = p; break; }
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int r = p[0], c = p[1];
                while (r + d[0] >= 0 && r + d[0] < N && c + d[1] >= 0 && c + d[1] < N && !board[r + d[0]][c + d[1]]) {
                    r += d[0];
                    c += d[1];
                }
                if (seen[r][c]) continue;
                seen[r][c] = true;
                parent[r * N + c] = p;
                queue.add(new int[]{r, c});
            }
        }

        path.clear();
        if (goal == null) return;
        List<int[]> cells = new ArrayList<>();
        for (int[] p = goal; p != null; p = parent[p[0] * N + p[1]]) cells.add(0, p);
        for (int i = 0; i + 1 < cells.size(); i++) {
            int[] a = fromComp(23 - cells.get(i)[1], 24 - cells.get(i)[0]);
            int[] b = fromComp(23 - cells.get(i + 1)[1], 24 - cells.get(i + 1)[0]);
            path.add(new Seg(a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5));
        }
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

    private static int[] toComp(int worldX, int worldZ) {
        return rotatePos(worldX - cornerX, worldZ - cornerZ, rotation);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.icePathSolver || path.isEmpty()) return;
        int next = TeslaMapsConfig.parseColor(cfg.colorIcePathNext) | 0xFF000000;
        int rest = TeslaMapsConfig.parseColor(cfg.colorIcePathRest) | 0xFF000000;
        int i = 0;
        for (Seg s : path) {
            ESPRenderer.drawLine(matrices, new Vec3(s.x1, 67.0, s.z1), new Vec3(s.x2, 67.0, s.z2), i == 0 ? next : rest, 3f, cameraPos);
            i++;
        }
    }

    public static void reset() {
        path.clear();
        inPath = false;
        fishRow = fishCol = -1;
        for (boolean[] r : board) java.util.Arrays.fill(r, false);
    }
}
