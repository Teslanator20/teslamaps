package com.teslamaps.dungeon.puzzle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Creeper Beams Solver - Shows lantern pairs for the Creeper Beams puzzle.
 * Scans for sea lanterns and matches pairs by trying all rotations.
 */
public class CreeperBeamsSolver {

    // Colors for different pairs
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

    public static void tick() {
        if (!TeslaMapsConfig.get().solveCreeperBeams) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Check if we're in Creeper Beams room
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Creeper Beams")) {
            if (!lastRoomName.equals("")) {
                reset();
            }
            return;
        }

        // Rescan periodically
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        BlockPos corner = room.getCorner();
        if (corner == null) return;

        // If we haven't detected rotation yet, try all 4 rotations
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

            // Only show if both are still sea lanterns
            boolean isLantern1 = mc.world.getBlockState(world1).getBlock() == Blocks.SEA_LANTERN;
            boolean isLantern2 = mc.world.getBlockState(world2).getBlock() == Blocks.SEA_LANTERN;

            if (isLantern1 && isLantern2) {
                int color = PAIR_COLORS[colorIndex % PAIR_COLORS.length];
                currentPairs.add(new LanternPair(world1, world2, color));
            }

            colorIndex++;
        }
    }

    /**
     * Detect room rotation by checking which rotation gives the most sea lantern matches.
     */
    private static int detectRotation(BlockPos corner) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return -1;

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

                if (mc.world.getBlockState(world1).getBlock() == Blocks.SEA_LANTERN) matches++;
                if (mc.world.getBlockState(world2).getBlock() == Blocks.SEA_LANTERN) matches++;
            }

            if (matches > bestMatches) {
                bestMatches = matches;
                bestRotation = rotation;
            }
        }

        // Only return if we found a reasonable number of matches
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

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveCreeperBeams) return;
        if (currentPairs.isEmpty()) return;

        for (LanternPair pair : currentPairs) {
            // Draw boxes around both lanterns
            Box box1 = new Box(pair.pos1);
            Box box2 = new Box(pair.pos2);

            ESPRenderer.drawESPBox(matrices, box1, pair.color, cameraPos);
            ESPRenderer.drawESPBox(matrices, box2, pair.color, cameraPos);

            // Draw line connecting them if enabled
            if (TeslaMapsConfig.get().creeperBeamsTracers) {
                Vec3d center1 = Vec3d.ofCenter(pair.pos1);
                Vec3d center2 = Vec3d.ofCenter(pair.pos2);
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
