package com.teslamaps.dungeon.puzzle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BoulderSolver {

    private static final List<BoulderClick> currentSolution = new ArrayList<>();

    private record BoulderClick(BlockPos renderPos, BlockPos clickPos) {}

    private static final Map<String, List<int[]>> BOULDER_SOLUTIONS = loadSolutions();

    private static int cornerX, cornerZ, rotation;
    private static boolean inBoulder = false;
    private static boolean solved = false;
    private static int scanTicks = 0;

    private static Map<String, List<int[]>> loadSolutions() {
        Map<String, List<int[]>> map = new HashMap<>();
        try (InputStream is = BoulderSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/boulderSolutions.json")) {
            if (is == null) return map;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                List<int[]> steps = new ArrayList<>();
                for (JsonElement step : e.getValue().getAsJsonArray()) {
                    JsonArray a = step.getAsJsonArray();
                    steps.add(new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt()});
                }
                map.put(e.getKey(), steps);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[BoulderSolver] Failed to load solutions", e);
        }
        return map;
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

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Boulder")) {
            if (inBoulder) {
                reset();
            }
            return;
        }

        if (!inBoulder) {
            inBoulder = true;
            cornerX = room.getCornerX();
            cornerZ = room.getCornerZ();
            rotation = room.getRotation();
            scanTicks = 0;

            if (rotation < 0) {
                inBoulder = false;
                return;
            }
        }

        // blocks may not be loaded on entry, rescan every second until a pattern matches
        if (!solved) {
            if (scanTicks++ % 20 == 0) scanAndFindSolution(mc);
            return;
        }

        // next boulder pushed by anyone (teammate included): its block is gone
        if (!currentSolution.isEmpty() && mc.level.getBlockState(currentSolution.get(0).renderPos).isAir()) {
            currentSolution.remove(0);
        }
    }

    private static int[] rotatePos(int x, int z, int degree) {
        return switch (degree % 360) {
            case 0 -> new int[]{x, z};
            case 90 -> new int[]{z, -x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    private static int[] fromComp(int x, int z) {
        int[] rotated = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{rotated[0] + cornerX, rotated[1] + cornerZ};
    }

    private static void scanAndFindSolution(Minecraft mc) {
        StringBuilder pattern = new StringBuilder();
        for (int z = 0; z < 16; z += 3) {
            for (int x = 0; x < 19; x += 3) {
                int[] pos = fromComp(24 - x, 24 - z);
                boolean hasBoulder = !mc.level.getBlockState(new BlockPos(pos[0], 65, pos[1])).isAir();
                pattern.append(hasBoulder ? "1" : "0");
            }
        }

        String patternStr = pattern.toString();
        List<int[]> solution = BOULDER_SOLUTIONS.get(patternStr);
        if (solution == null) {
            TeslaMaps.debug("[BoulderSolver] No solution for pattern {} (rotation={})", patternStr, rotation);
            return;
        }

        currentSolution.clear();
        for (int[] step : solution) {
            int[] render = fromComp(step[0], step[1]);
            int[] click = fromComp(step[2], step[3]);
            currentSolution.add(new BoulderClick(
                new BlockPos(render[0], 65, render[1]),
                new BlockPos(click[0], 65, click[1])
            ));
        }
        solved = true;

        TeslaMaps.debug("[BoulderSolver] Found solution with {} clicks", currentSolution.size());
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveBoulder) return;
        if (currentSolution.isEmpty()) return;

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        int color = TeslaMapsConfig.parseColor(cfg.colorBoulder);
        int clickColor = cfg.boulderClickBlock ? TeslaMapsConfig.parseColor(cfg.colorBoulderClickBlock) : 0;

        int count = cfg.showAllBoulderClicks ? currentSolution.size() : 1;

        for (int i = 0; i < count && i < currentSolution.size(); i++) {
            BoulderClick click = currentSolution.get(i);
            ESPRenderer.drawESPBox(matrices, new AABB(click.renderPos), color, cameraPos);
            if (cfg.boulderClickBlock) {
                ESPRenderer.drawESPBox(matrices, new AABB(click.clickPos), clickColor, cameraPos);
            }
        }
    }

    public static void onBlockInteract(BlockPos pos) {
        if (!inBoulder) return;
        for (int i = 0; i < currentSolution.size(); i++) {
            if (currentSolution.get(i).clickPos.equals(pos)) {
                currentSolution.remove(i);
                return;
            }
        }
    }

    public static void reset() {
        currentSolution.clear();
        inBoulder = false;
        solved = false;
        scanTicks = 0;
    }
}
