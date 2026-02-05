package com.teslamaps.dungeon.puzzle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Water Board Solver - Shows lever timing for one-flow solutions.
 */
public class WaterBoardSolver {

    private static JsonObject SOLUTIONS;

    // Block positions to check for variant detection
    private static final int[] TOP_LEFT_BLOCK = {16, 26};
    private static final int[] TOP_RIGHT_BLOCK = {14, 26};
    // Wool positions for door detection (purple wool at Y=57)
    private static final int[] PURPLE_WOOL = {15, 19};
    private static final Block[] WOOL_ORDER = {
        Blocks.PURPLE_WOOL,  // door 0
        Blocks.ORANGE_WOOL,  // door 1
        Blocks.BLUE_WOOL,    // door 2
        Blocks.LIME_WOOL,    // door 3
        Blocks.RED_WOOL      // door 4
    };

    // Lever positions
    public enum LeverType {
        QUARTZ("quartz", Blocks.QUARTZ_BLOCK, 20, 61, 20),
        GOLD("gold", Blocks.GOLD_BLOCK, 20, 61, 15),
        COAL("coal", Blocks.COAL_BLOCK, 20, 61, 10),
        DIAMOND("diamond", Blocks.DIAMOND_BLOCK, 10, 61, 20),
        EMERALD("emerald", Blocks.EMERALD_BLOCK, 10, 61, 15),
        TERRACOTTA("terracotta", Blocks.TERRACOTTA, 10, 61, 10),
        WATER("water", Blocks.LAVA, 15, 60, 5);

        public final String name;
        public final Block block;
        public final int x, y, z;

        LeverType(String name, Block block, int x, int y, int z) {
            this.name = name;
            this.block = block;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static LeverType fromName(String name) {
            for (LeverType type : values()) {
                if (type.name.equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    private static int variant = -1;
    private static String subvariant = null;
    private static Map<LeverType, List<Double>> solution = null;
    private static long waterStartMillis = 0;
    private static boolean inWaterBoard = false;
    private static DungeonRoom currentRoom = null;
    private static int cornerX = 0;
    private static int cornerZ = 0;
    private static int rotation = -1;

    static {
        loadSolutions();
    }

    private static void loadSolutions() {
        try {
            InputStream is = WaterBoardSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/watertimes.json");
            if (is != null) {
                SOLUTIONS = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                TeslaMaps.LOGGER.info("[WaterBoardSolver] Loaded water solutions");
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[WaterBoardSolver] Failed to load solutions", e);
            SOLUTIONS = new JsonObject();
        }
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().solveWaterBoard) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Check if we're in Water Board room
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Water Board")) {
            if (inWaterBoard) {
                reset();
            }
            return;
        }

        if (!inWaterBoard) {
            inWaterBoard = true;
            currentRoom = room;
            variant = -1;
            subvariant = null;
            solution = null;
            waterStartMillis = 0;

            // Get corner and rotation from room (blue terracotta position)
            rotation = room.getRotation();
            cornerX = room.getCornerX();
            cornerZ = room.getCornerZ();

            TeslaMaps.LOGGER.info("[WaterBoardSolver] Entered Water Board room, rotation={}, corner=({},{})",
                rotation, cornerX, cornerZ);

            // Detect variant
            detectVariant(mc);
        }

        // Detect subvariant (doors) once we have rotation
        if (variant >= 0 && subvariant == null && rotation >= 0) {
            detectSubvariant(mc);
        }
    }

    /**
     * Rotate coordinates: rotates coordinates by degree
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
     * Convert to world coords: converts relative component coords to world coords
     */
    private static int[] fromComp(int x, int z) {
        if (rotation < 0) return null;
        // Rotate by 360 - rotation (inverse rotation)
        int[] rotated = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{rotated[0] + cornerX, rotated[1] + cornerZ};
    }

    private static void detectVariant(MinecraftClient mc) {
        if (rotation < 0) {
            TeslaMaps.LOGGER.warn("[WaterBoardSolver] No rotation detected yet");
            return;
        }

        // Find Y level (77 or 78) by checking for sea lantern
        int currentY = 77;
        int[] lanternPos = fromComp(15, 27);
        if (lanternPos != null) {
            Block lanternBlock = mc.world.getBlockState(new BlockPos(lanternPos[0], currentY, lanternPos[1])).getBlock();
            if (lanternBlock != Blocks.SEA_LANTERN) {
                currentY = 78;
            }
        }

        // Get top left and right blocks
        int[] topLeft = fromComp(TOP_LEFT_BLOCK[0], TOP_LEFT_BLOCK[1]);
        int[] topRight = fromComp(TOP_RIGHT_BLOCK[0], TOP_RIGHT_BLOCK[1]);

        if (topLeft == null || topRight == null) {
            TeslaMaps.LOGGER.warn("[WaterBoardSolver] Could not convert block positions");
            return;
        }

        Block leftBlock = mc.world.getBlockState(new BlockPos(topLeft[0], currentY, topLeft[1])).getBlock();
        Block rightBlock = mc.world.getBlockState(new BlockPos(topRight[0], currentY, topRight[1])).getBlock();

        TeslaMaps.LOGGER.info("[WaterBoardSolver] Top blocks at Y={}: left={} at ({},{}), right={} at ({},{})",
            currentY, leftBlock, topLeft[0], topLeft[1], rightBlock, topRight[0], topRight[1]);

        // If blocks are air or stone, try offset position 
        if (leftBlock == Blocks.AIR || leftBlock == Blocks.STONE) {
            int[] newPos = fromComp(TOP_LEFT_BLOCK[0], TOP_LEFT_BLOCK[1] + 1);
            if (newPos != null) {
                leftBlock = mc.world.getBlockState(new BlockPos(newPos[0], currentY, newPos[1])).getBlock();
            }
        }
        if (rightBlock == Blocks.AIR || rightBlock == Blocks.STONE) {
            int[] newPos = fromComp(TOP_RIGHT_BLOCK[0], TOP_RIGHT_BLOCK[1] + 1);
            if (newPos != null) {
                rightBlock = mc.world.getBlockState(new BlockPos(newPos[0], currentY, newPos[1])).getBlock();
            }
        }

        // Determine variant (0-3)
        if (leftBlock == Blocks.GOLD_BLOCK && rightBlock == Blocks.TERRACOTTA) {
            variant = 0;
        } else if (leftBlock == Blocks.EMERALD_BLOCK && rightBlock == Blocks.QUARTZ_BLOCK) {
            variant = 1;
        } else if (leftBlock == Blocks.QUARTZ_BLOCK && rightBlock == Blocks.DIAMOND_BLOCK) {
            variant = 2;
        } else if (leftBlock == Blocks.GOLD_BLOCK && rightBlock == Blocks.QUARTZ_BLOCK) {
            variant = 3;
        }

        TeslaMaps.LOGGER.info("[WaterBoardSolver] Detected variant: {} (left={}, right={})", variant, leftBlock, rightBlock);
    }

    private static void detectSubvariant(MinecraftClient mc) {
        // Check wool blocks to determine which doors are closed
        // Keep retrying until we find 3 doors 
        StringBuilder sb = new StringBuilder();

        for (int idx = 0; idx < WOOL_ORDER.length; idx++) {
            Block woolType = WOOL_ORDER[idx];
            int[] pos = fromComp(PURPLE_WOOL[0], PURPLE_WOOL[1] - idx);
            if (pos == null) continue;

            Block block = mc.world.getBlockState(new BlockPos(pos[0], 57, pos[1])).getBlock();

            if (block == woolType) {
                sb.append(idx);
            }
        }

        String detected = sb.toString();

        if (detected.length() == 3) {
            // Successfully found all 3 doors
            subvariant = detected;
            TeslaMaps.LOGGER.info("[WaterBoardSolver] Detected subvariant (doors): '{}'", subvariant);
            loadSolution();
        }
        // If we didn't find 3, subvariant stays null and we'll retry next tick
    }

    private static void loadSolution() {
        if (SOLUTIONS == null || variant < 0 || subvariant == null) return;

        // Variant 0-3 maps to watertimes.json uses 1-4
        String variantKey = String.valueOf(variant + 1);

        TeslaMaps.LOGGER.info("[WaterBoardSolver] Looking up solution for variant={} doors={}", variantKey, subvariant);

        JsonObject variantSolutions = SOLUTIONS.getAsJsonObject(variantKey);
        if (variantSolutions == null) {
            TeslaMaps.LOGGER.warn("[WaterBoardSolver] No solutions for variant {}", variantKey);
            return;
        }

        JsonObject doorSolution = variantSolutions.getAsJsonObject(subvariant);
        if (doorSolution == null) {
            TeslaMaps.LOGGER.warn("[WaterBoardSolver] No solution for variant {} doors {}", variantKey, subvariant);
            return;
        }

        // Parse solution
        solution = new EnumMap<>(LeverType.class);
        for (Map.Entry<String, JsonElement> entry : doorSolution.entrySet()) {
            LeverType leverType = LeverType.fromName(entry.getKey());
            if (leverType != null) {
                List<Double> times = new ArrayList<>();
                for (JsonElement elem : entry.getValue().getAsJsonArray()) {
                    times.add(elem.getAsDouble());
                }
                solution.put(leverType, times);
            }
        }

        TeslaMaps.LOGGER.info("[WaterBoardSolver] Loaded solution with {} lever types", solution.size());
    }

    public static void onLeverClick(BlockPos pos) {
        if (!TeslaMapsConfig.get().solveWaterBoard || solution == null) return;

        for (LeverType leverType : LeverType.values()) {
            int[] leverPos = fromComp(leverType.x, leverType.z);
            if (leverPos == null) continue;

            if (Math.abs(pos.getX() - leverPos[0]) <= 1 &&
                Math.abs(pos.getY() - leverType.y) <= 1 &&
                Math.abs(pos.getZ() - leverPos[1]) <= 1) {

                List<Double> times = solution.get(leverType);
                if (times != null && !times.isEmpty()) {
                    times.remove(0);
                    TeslaMaps.LOGGER.info("[WaterBoardSolver] {} clicked, {} times remaining",
                        leverType.name, times.size());

                    if (leverType == LeverType.WATER && waterStartMillis == 0) {
                        waterStartMillis = System.currentTimeMillis();
                        TeslaMaps.LOGGER.info("[WaterBoardSolver] Water lever clicked, timer started");
                    }
                }
                break;
            }
        }
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveWaterBoard || solution == null) return;

        long now = System.currentTimeMillis();
        int colorFirst = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorWaterFirst);
        int colorSecond = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorWaterSecond);

        List<LeverTime> remaining = new ArrayList<>();
        for (Map.Entry<LeverType, List<Double>> entry : solution.entrySet()) {
            LeverType leverType = entry.getKey();
            List<Double> times = entry.getValue();

            int[] pos = fromComp(leverType.x, leverType.z);
            if (pos == null) continue;

            for (int i = 0; i < times.size(); i++) {
                remaining.add(new LeverTime(leverType.name, new BlockPos(pos[0], leverType.y, pos[1]), times.get(i), i));
            }
        }

        remaining.sort((a, b) -> {
            if (a.time == 0.0 && b.time != 0.0) return -1;
            if (b.time == 0.0 && a.time != 0.0) return 1;
            return Double.compare(a.time, b.time);
        });

        if (!remaining.isEmpty() && TeslaMapsConfig.get().waterBoardTracers) {
            LeverTime first = remaining.get(0);
            Vec3d firstCenter = Vec3d.ofCenter(first.pos);
            ESPRenderer.drawTracerFromCamera(matrices, firstCenter, colorFirst, cameraPos);
            ESPRenderer.drawBoxOutline(matrices, new Box(first.pos), colorFirst, 3f, cameraPos);

            if (remaining.size() > 1) {
                LeverTime second = remaining.get(1);
                if (!second.pos.equals(first.pos)) {
                    Vec3d secondCenter = Vec3d.ofCenter(second.pos);
                    ESPRenderer.drawLine(matrices, firstCenter, secondCenter, colorSecond, 2f, cameraPos);
                }
            }
        }

        Map<BlockPos, List<LeverTime>> byPos = new HashMap<>();
        for (LeverTime lt : remaining) {
            byPos.computeIfAbsent(lt.pos, k -> new ArrayList<>()).add(lt);
        }

        for (Map.Entry<BlockPos, List<LeverTime>> entry : byPos.entrySet()) {
            BlockPos leverPos = entry.getKey();
            List<LeverTime> clicks = entry.getValue();

            for (int i = 0; i < clicks.size(); i++) {
                LeverTime lt = clicks.get(i);
                long timeInMillis = (long)(lt.time * 1000);

                String text;
                if (waterStartMillis == 0) {
                    text = timeInMillis == 0 ? "\u00A7a\u00A7lCLICK!" : String.format("\u00A7e%.1fs", lt.time);
                } else {
                    long remainingMillis = waterStartMillis + timeInMillis - now;
                    text = remainingMillis <= 0 ? "\u00A7a\u00A7lCLICK!" : String.format("\u00A7e%.1fs", remainingMillis / 1000.0);
                }

                Vec3d textPos = Vec3d.ofCenter(leverPos).add(0, i * 0.5 + 1.5, 0);
                ESPRenderer.drawText(matrices, text, textPos, 1.5f, cameraPos);
            }
        }
    }

    public static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (!TeslaMapsConfig.get().solveWaterBoard || solution == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.textRenderer == null) return;

        long now = System.currentTimeMillis();

        List<LeverTime> remaining = new ArrayList<>();
        for (Map.Entry<LeverType, List<Double>> entry : solution.entrySet()) {
            LeverType leverType = entry.getKey();
            List<Double> times = entry.getValue();

            for (int i = 0; i < times.size(); i++) {
                remaining.add(new LeverTime(leverType.name, null, times.get(i), i));
            }
        }

        if (remaining.isEmpty()) return;

        remaining.sort((a, b) -> {
            if (a.time == 0.0 && b.time != 0.0) return -1;
            if (b.time == 0.0 && a.time != 0.0) return 1;
            return Double.compare(a.time, b.time);
        });

        int screenWidth = mc.getWindow().getScaledWidth();
        int baseX = screenWidth / 2 - 60;
        int baseY = 10;
        int lineHeight = 12;
        int height = Math.min(remaining.size(), 8) * lineHeight + 20;

        context.fill(baseX - 4, baseY - 4, baseX + 124, baseY + height, 0xAA000000);
        context.drawTextWithShadow(mc.textRenderer, "Water Board (v" + (variant+1) + " " + subvariant + ")", baseX, baseY, 0xFF55FFFF);
        baseY += lineHeight + 2;

        int count = 0;
        for (LeverTime lt : remaining) {
            if (count >= 8) break;

            long timeInMillis = (long)(lt.time * 1000);
            String text;
            int color;

            if (waterStartMillis == 0) {
                if (timeInMillis == 0) {
                    text = lt.name + ": CLICK!";
                    color = 0xFF55FF55;
                } else {
                    text = String.format("%s: %.1fs", lt.name, lt.time);
                    color = 0xFFFFFF55;
                }
            } else {
                long remainingMillis = waterStartMillis + timeInMillis - now;
                if (remainingMillis <= 0) {
                    text = lt.name + ": CLICK!";
                    color = 0xFF55FF55;
                } else {
                    text = String.format("%s: %.1fs", lt.name, remainingMillis / 1000.0);
                    color = 0xFFFFFF55;
                }
            }

            context.drawTextWithShadow(mc.textRenderer, text, baseX, baseY + count * lineHeight, color);
            count++;
        }
    }

    public static void reset() {
        variant = -1;
        subvariant = null;
        solution = null;
        waterStartMillis = 0;
        inWaterBoard = false;
        currentRoom = null;
        cornerX = 0;
        cornerZ = 0;
        rotation = -1;
    }

    private record LeverTime(String name, BlockPos pos, double time, int index) {}
}
