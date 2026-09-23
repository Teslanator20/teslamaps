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

    // relative board origins (Skyblocker IceFill): rows run -z, columns -x
    private static final int[][] BOARD_ORIGINS = {{16, 70, 9}, {17, 71, 16}, {18, 72, 25}};
    private static final int[] BOARD_SIZES = {3, 5, 7};
    private static final int DFS_BUDGET = 500_000;

    private static final List<Vec3> currentPath = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private static final List<Vec3>[] floorPaths = new List[3];
    private static DungeonRoom iceRoom = null;
    private static int[] clay = null;
    private static boolean solved = false;
    private static int retryTicks = 0;
    private static int dfsSteps = 0;

    static { load(); }

    private static void load() {
        try (InputStream is = IceFillSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/iceFillFloors.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            identifier = parseSection(root.getAsJsonArray("identifier"));
            easy = parseSection(root.getAsJsonArray("easy"));
            hard = parseSection(root.getAsJsonArray("hard"));
            TeslaMaps.debug("[IceFill] Loaded {} floors", identifier.size());
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
        // retry until the floors are loaded
        if (retryTicks++ % 20 != 0) return;

        if (clay == null) clay = DungeonWaypoints.scanClayPos(iceRoom);
        if (clay == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int found = TeslaMapsConfig.get().iceFillDynamic ? solveDynamic() : solvePatterns();

        currentPath.clear();
        for (List<Vec3> p : floorPaths) if (p != null) currentPath.addAll(p);
        solved = found == 3;
    }

    private static int solvePatterns() {
        List<List<List<int[]>>> patterns = TeslaMapsConfig.get().iceFillOptimized ? hard : easy;
        int found = 0;
        for (int floor = 0; floor < identifier.size() && floor < patterns.size() && floor < 3; floor++) {
            if (floorPaths[floor] != null) { found++; continue; }
            List<List<int[]>> floorIds = identifier.get(floor);
            for (int pi = 0; pi < floorIds.size(); pi++) {
                if (isAir(floorIds.get(pi).get(0)) && !isAir(floorIds.get(pi).get(1))) {
                    List<Vec3> path = new ArrayList<>();
                    for (int[] pt : patterns.get(floor).get(pi)) path.add(toWorld(pt[0], pt[1], pt[2]));
                    floorPaths[floor] = path;
                    found++;
                    break;
                }
            }
        }
        return found;
    }

    private static int solveDynamic() {
        int found = 0;
        for (int i = 0; i < 3; i++) {
            if (floorPaths[i] != null) { found++; continue; }
            boolean[][] board = readBoard(i);
            if (board == null) continue;
            List<int[]> cells = solveBoard(board);
            if (cells == null) {
                TeslaMaps.debug("[IceFill] No path for floor {}", i);
                continue;
            }
            int[] o = BOARD_ORIGINS[i];
            List<Vec3> path = new ArrayList<>();
            for (int c = 0; c < cells.size(); c++) {
                // keep only corners
                if (c > 0 && c < cells.size() - 1) {
                    int[] a = cells.get(c - 1), b = cells.get(c), n = cells.get(c + 1);
                    if (b[0] - a[0] == n[0] - b[0] && b[1] - a[1] == n[1] - b[1]) continue;
                }
                int[] cell = cells.get(c);
                path.add(toWorld(o[0] - cell[1], o[1], o[2] - cell[0]));
            }
            int mid = BOARD_SIZES[i] / 2;
            path.add(toWorld(o[0] - mid, o[1], o[2] + 1));
            floorPaths[i] = path;
            found++;
        }
        return found;
    }

    /** true = blocked. Null while any floor block below is still unloaded. */
    private static boolean[][] readBoard(int i) {
        int n = BOARD_SIZES[i];
        int[] o = BOARD_ORIGINS[i];
        boolean[][] board = new boolean[n][n];
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                int[] rel = {o[0] - col, o[1], o[2] - row};
                if (isAir(new int[]{rel[0], rel[1] - 1, rel[2]})) return null;
                board[row][col] = !isAir(rel);
            }
        }
        return board;
    }

    private static List<int[]> solveBoard(boolean[][] board) {
        int n = board.length;
        int free = 0;
        for (boolean[] r : board) for (boolean b : r) if (!b) free++;
        int[] start = {n - 1, n / 2};
        if (board[start[0]][start[1]] || board[0][n / 2]) return null;
        boolean[][] visited = new boolean[n][n];
        visited[start[0]][start[1]] = true;
        List<int[]> path = new ArrayList<>();
        path.add(start);
        dfsSteps = 0;
        return dfs(board, visited, path, free - 1, -1) ? path : null;
    }

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, 1}, {0, -1}};

    private static boolean dfs(boolean[][] board, boolean[][] visited, List<int[]> path, int left, int lastDir) {
        int n = board.length;
        int[] pos = path.get(path.size() - 1);
        if (left == 0) return pos[0] == 0 && pos[1] == n / 2;
        if (pos[0] == 0 && pos[1] == n / 2) return false;
        if (++dfsSteps > DFS_BUDGET) return false;
        for (int k = -1; k < 4; k++) {
            // straight first: fewer turns
            int d = k < 0 ? lastDir : k;
            if (d < 0 || (k >= 0 && d == lastDir)) continue;
            int r = pos[0] + DIRS[d][0], c = pos[1] + DIRS[d][1];
            if (r < 0 || r >= n || c < 0 || c >= n || board[r][c] || visited[r][c]) continue;
            visited[r][c] = true;
            path.add(new int[]{r, c});
            if (dfs(board, visited, path, left - 1, d)) return true;
            path.remove(path.size() - 1);
            visited[r][c] = false;
        }
        return false;
    }

    private static Vec3 toWorld(int x, int y, int z) {
        int[] xz = DungeonWaypoints.relativeToWorld(clay, x, z);
        return new Vec3(xz[0] + 0.5, y + 0.1, xz[1] + 0.5);
    }

    private static boolean isAir(int[] p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        int[] xz = DungeonWaypoints.relativeToWorld(clay, p[0], p[2]);
        return mc.level.getBlockState(new BlockPos(xz[0], p[1], xz[1])).isAir();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.solveIceFill || currentPath.size() < 2) return;
        DungeonRoom cur = DungeonManager.getCurrentRoom();
        if (cur == null || !"Ice Fill".equals(cur.getName())) return;

        int color = TeslaMapsConfig.parseColor(cfg.colorIceFill);
        int end = cfg.iceFillGradient ? TeslaMapsConfig.parseColor(cfg.colorIceFillEnd) : color;
        int segs = currentPath.size() - 1;
        for (int i = 0; i < segs; i++) {
            int c = cfg.iceFillGradient ? lerp(color, end, segs == 1 ? 0f : i / (float) (segs - 1)) : color;
            ESPRenderer.drawLine(matrices, currentPath.get(i), currentPath.get(i + 1), c, cfg.iceFillLineWidth, cameraPos);
        }
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return r;
    }

    public static void reset() {
        currentPath.clear();
        for (int i = 0; i < floorPaths.length; i++) floorPaths[i] = null;
        iceRoom = null;
        clay = null;
        solved = false;
        retryTicks = 0;
    }
}
