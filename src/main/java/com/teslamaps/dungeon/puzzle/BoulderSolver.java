package com.teslamaps.dungeon.puzzle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Boulder Solver - Shows solution for Boulder puzzle room.
 * Scans boulder positions and looks up solution from database.
 */
public class BoulderSolver {

    private static Map<String, List<List<Integer>>> solutions;
    private static List<BoulderClick> currentSolution = new ArrayList<>();
    private static String lastRoomName = "";
    private static long lastScanTime = 0;

    private record BoulderClick(BlockPos renderPos, BlockPos clickPos) {}

    static {
        loadSolutions();
    }

    private static void loadSolutions() {
        try {
            InputStream is = BoulderSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/boulderSolutions.json");
            if (is != null) {
                Type type = new TypeToken<Map<String, List<List<Integer>>>>(){}.getType();
                solutions = new Gson().fromJson(new InputStreamReader(is), type);
                TeslaMaps.LOGGER.info("[BoulderSolver] Loaded {} boulder solutions", solutions.size());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[BoulderSolver] Failed to load solutions", e);
            solutions = Map.of();
        }
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().solveBoulder) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Check if we're in Boulder room
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Boulder")) {
            if (!lastRoomName.equals("")) {
                reset();
            }
            return;
        }

        // Scan every second
        long now = System.currentTimeMillis();
        if (now - lastScanTime < 1000) return;
        lastScanTime = now;

        if (!lastRoomName.equals("Boulder")) {
            lastRoomName = "Boulder";
            scanAndFindSolution(room);
        }
    }

    private static void scanAndFindSolution(DungeonRoom room) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // Get room corner for coordinate transformation
        BlockPos roomCorner = room.getCorner();
        if (roomCorner == null) return;
        int rotation = room.getRotation();

        // Scan boulder positions (6x7 grid)
        StringBuilder pattern = new StringBuilder();
        for (int z = 24; z >= 9; z -= 3) {
            for (int x = 24; x >= 6; x -= 3) {
                BlockPos relPos = new BlockPos(x, 66, z);
                BlockPos worldPos = transformPos(relPos, roomCorner, rotation);
                boolean isEmpty = mc.world.getBlockState(worldPos).isAir();
                pattern.append(isEmpty ? "0" : "1");
            }
        }

        String patternStr = pattern.toString();
        TeslaMaps.LOGGER.info("[BoulderSolver] Scanned pattern: {}", patternStr);

        // Look up solution
        List<List<Integer>> solution = solutions.get(patternStr);
        if (solution == null) {
            TeslaMaps.LOGGER.info("[BoulderSolver] No solution found for pattern");
            return;
        }

        currentSolution.clear();
        for (List<Integer> click : solution) {
            if (click.size() >= 4) {
                BlockPos renderRel = new BlockPos(click.get(0), 65, click.get(1));
                BlockPos clickRel = new BlockPos(click.get(2), 65, click.get(3));
                BlockPos renderWorld = transformPos(renderRel, roomCorner, rotation);
                BlockPos clickWorld = transformPos(clickRel, roomCorner, rotation);
                currentSolution.add(new BoulderClick(renderWorld, clickWorld));
            }
        }

        TeslaMaps.LOGGER.info("[BoulderSolver] Found solution with {} clicks", currentSolution.size());
    }

    private static BlockPos transformPos(BlockPos relative, BlockPos corner, int rotation) {
        int x = relative.getX();
        int z = relative.getZ();

        // Apply rotation (0=North, 1=East, 2=South, 3=West)
        int rx, rz;
        switch (rotation) {
            case 1 -> { rx = 30 - z; rz = x; }
            case 2 -> { rx = 30 - x; rz = 30 - z; }
            case 3 -> { rx = z; rz = 30 - x; }
            default -> { rx = x; rz = z; }
        }

        return new BlockPos(corner.getX() + rx, relative.getY(), corner.getZ() + rz);
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveBoulder) return;
        if (currentSolution.isEmpty()) return;

        int color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorBoulder);

        // Render all clicks if showAllBoulderClicks, otherwise just first
        int count = TeslaMapsConfig.get().showAllBoulderClicks ? currentSolution.size() : 1;

        for (int i = 0; i < count && i < currentSolution.size(); i++) {
            BoulderClick click = currentSolution.get(i);
            Box box = new Box(
                click.renderPos.getX(), click.renderPos.getY(), click.renderPos.getZ(),
                click.renderPos.getX() + 1, click.renderPos.getY() + 1, click.renderPos.getZ() + 1
            );
            ESPRenderer.drawESPBox(matrices, box, color, cameraPos);
        }
    }

    public static void onBlockInteract(BlockPos pos) {
        // Remove clicked position from solution
        currentSolution.removeIf(click -> click.clickPos.equals(pos));
    }

    public static void reset() {
        currentSolution.clear();
        lastRoomName = "";
    }
}
