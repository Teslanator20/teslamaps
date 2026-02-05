package com.teslamaps.dungeon.puzzle;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Boulder Solver - Shows solution for Boulder puzzle room.
 * Scans boulder positions and looks up solution from database.
 */
public class BoulderSolver {

    private static List<BoulderClick> currentSolution = new ArrayList<>();

    private record BoulderClick(BlockPos renderPos, BlockPos clickPos) {}

    // Solutions map (grid pattern -> list of click positions)
    private static final Map<String, List<int[]>> BOULDER_SOLUTIONS = Map.of(
        "100101001000000010101001111101010101010101", new ArrayList<>(List.of(new int[]{21, 11}, new int[]{22, 21})),
        "010000010111101001010011100000101110000111", new ArrayList<>(List.of(new int[]{13, 12})),
        "000000011111101001010011100000101110000110", new ArrayList<>(List.of(new int[]{13, 12})),
        "100000111101111011101110001110111010000000", new ArrayList<>(List.of(new int[]{21, 14}, new int[]{15, 17}, new int[]{15, 20}, new int[]{13, 21})),
        "110001110111011010001100111111100011000001", new ArrayList<>(List.of(new int[]{15, 14}, new int[]{19, 21})),
        "100100101000100010100010101000101000100010", new ArrayList<>(List.of(new int[]{22, 21})),
        "000000010101110101010011010000010100000000", new ArrayList<>(List.of(new int[]{22, 18})),
        "000000001111100100010010001011111110000000", new ArrayList<>(List.of(new int[]{24, 11}, new int[]{24, 14}, new int[]{24, 17}, new int[]{24, 20}, new int[]{22, 21}))
    );

    private static int cornerX, cornerZ, rotation;
    private static boolean inBoulder = false;

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

            if (rotation < 0) {
                inBoulder = false;
                return;
            }

            scanAndFindSolution(mc, room);
        }
    }

    /**
     * Rotate coordinates by degree
     */
    private static int[] rotatePos(int x, int z, int degree) {
        return switch (degree % 360) {
            case 0 -> new int[]{x, z};
            case 90 -> new int[]{z, -x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    /**
     * Convert component coords to world coords
     */
    private static int[] fromComp(int x, int z) {
        int[] rotated = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{rotated[0] + cornerX, rotated[1] + cornerZ};
    }

    private static void scanAndFindSolution(MinecraftClient mc, DungeonRoom room) {
        // Scan boulder grid to build grid pattern
        // Far left boulder at (24, 65, 24), width 3 blocks each
        StringBuilder pattern = new StringBuilder();
        for (int z = 0; z < 16; z += 3) {
            for (int x = 0; x < 19; x += 3) {
                int[] pos = fromComp(24 - x, 24 - z);
                boolean hasBoulder = !mc.world.getBlockState(new BlockPos(pos[0], 65, pos[1])).isAir();
                pattern.append(hasBoulder ? "1" : "0");
            }
        }

        String patternStr = pattern.toString();
        TeslaMaps.LOGGER.info("[BoulderSolver] Scanned pattern: {} (rotation={})", patternStr, rotation);

        // Look up solution 
        List<int[]> solution = BOULDER_SOLUTIONS.get(patternStr);
        if (solution == null) {
            TeslaMaps.LOGGER.warn("[BoulderSolver] No solution found for pattern");
            return;
        }

        currentSolution.clear();
        for (int[] click : solution) {
            int[] worldPos = fromComp(click[0], click[1]);
            currentSolution.add(new BoulderClick(
                new BlockPos(worldPos[0], 65, worldPos[1]),
                new BlockPos(worldPos[0], 65, worldPos[1])
            ));
        }

        TeslaMaps.LOGGER.info("[BoulderSolver] Found solution with {} clicks", currentSolution.size());
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
        inBoulder = false;
    }
}
