package com.teslamaps.dungeon.puzzle;

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
import java.util.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WaterBoardSolver {

    private static JsonObject SOLUTIONS;

    private static final int[] TOP_LEFT_BLOCK = {16, 26};
    private static final int[] TOP_RIGHT_BLOCK = {14, 26};

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
    private static int variantAttempts = 0;

    static {
        loadSolutions();
    }

    private static void loadSolutions() {
        try {
            InputStream is = WaterBoardSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/watertimes.json");
            if (is != null) {
                SOLUTIONS = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                TeslaMaps.debug("[WaterBoardSolver] Loaded water solutions");
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

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

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
            variantAttempts = 0;

            rotation = room.getRotation();
            cornerX = room.getCornerX();
            cornerZ = room.getCornerZ();

            TeslaMaps.debug("[WaterBoardSolver] Entered Water Board room, rotation={}, corner=({},{})",
                rotation, cornerX, cornerZ);
        }

        // retry variant detection: the board blocks are often not loaded yet on the first tick after entering
        if (variant < 0 && rotation >= 0) {
            if (variantAttempts % 20 == 0) detectVariant(mc);
            variantAttempts++;
        }

        if (variant >= 0 && subvariant == null && rotation >= 0) {
            detectSubvariant(mc);
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
        if (rotation < 0) return null;
        int[] rotated = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{rotated[0] + cornerX, rotated[1] + cornerZ};
    }

    private static void detectVariant(Minecraft mc) {
        if (rotation < 0) {
            TeslaMaps.LOGGER.warn("[WaterBoardSolver] No rotation detected yet");
            return;
        }

        // Odin-style: a single identifying block per pattern is enough
        variant = readVariantSingle(mc, cornerX, cornerZ, rotation);
        if (variant >= 0) {
            TeslaMaps.debug("[WaterBoardSolver] Detected variant {} (corner=({},{}) rot={})",
                variant, cornerX, cornerZ, rotation);
            return;
        }

        // rare: multi-component room picked the wrong corner/rotation -> the normal read above stayed -1.
        // only then brute-force the other component corners x rotations with the stricter pair check.
        detectVariantFallback(mc);
    }

    private static int readVariantSingle(Minecraft mc, int cx, int cz, int rot) {
        if (blockAtComp(mc, 14, 27, rot, cx, cz, 77) == Blocks.TERRACOTTA) return 0;
        if (blockAtComp(mc, 16, 27, rot, cx, cz, 78) == Blocks.EMERALD_BLOCK) return 1;
        if (blockAtComp(mc, 14, 27, rot, cx, cz, 78) == Blocks.DIAMOND_BLOCK) return 2;
        if (blockAtComp(mc, 14, 27, rot, cx, cz, 78) == Blocks.QUARTZ_BLOCK) return 3;
        return -1;
    }

    private static void detectVariantFallback(Minecraft mc) {
        if (currentRoom == null) return;
        int baseRot = currentRoom.getRotation();
        int[] rots = {0, 90, 180, 270};

        for (int[] comp : currentRoom.getComponents()) {
            int[] corner = com.teslamaps.scanner.ComponentGrid.gridToWorldCorner(comp[0], comp[1]);
            if (baseRot >= 0 && tryDetect(mc, corner[0], corner[1], baseRot)) return;
            for (int rot : rots) {
                if (rot == baseRot) continue;
                if (tryDetect(mc, corner[0], corner[1], rot)) return;
            }
        }
    }

    private static boolean tryDetect(Minecraft mc, int cx, int cz, int rot) {
        for (int y : new int[]{77, 78}) {
            int v = readVariant(mc, cx, cz, rot, y);
            if (v >= 0) {
                cornerX = cx;
                cornerZ = cz;
                rotation = rot;
                variant = v;
                TeslaMaps.debug("[WaterBoardSolver] Detected variant {} at corner=({},{}) rot={} Y={}", v, cx, cz, rot, y);
                return true;
            }
        }
        return false;
    }

    private static int readVariant(Minecraft mc, int cx, int cz, int rot, int y) {
        Block left = blockAtComp(mc, TOP_LEFT_BLOCK[0], TOP_LEFT_BLOCK[1], rot, cx, cz, y);
        Block right = blockAtComp(mc, TOP_RIGHT_BLOCK[0], TOP_RIGHT_BLOCK[1], rot, cx, cz, y);
        if (left == Blocks.AIR || left == Blocks.STONE)
            left = blockAtComp(mc, TOP_LEFT_BLOCK[0], TOP_LEFT_BLOCK[1] + 1, rot, cx, cz, y);
        if (right == Blocks.AIR || right == Blocks.STONE)
            right = blockAtComp(mc, TOP_RIGHT_BLOCK[0], TOP_RIGHT_BLOCK[1] + 1, rot, cx, cz, y);

        if (left == Blocks.GOLD_BLOCK && right == Blocks.TERRACOTTA) return 0;
        if (left == Blocks.EMERALD_BLOCK && right == Blocks.QUARTZ_BLOCK) return 1;
        if (left == Blocks.QUARTZ_BLOCK && right == Blocks.DIAMOND_BLOCK) return 2;
        if (left == Blocks.GOLD_BLOCK && right == Blocks.QUARTZ_BLOCK) return 3;
        return -1;
    }

    private static Block blockAtComp(Minecraft mc, int x, int z, int rot, int cx, int cz, int y) {
        int[] r = rotatePos(x, z, (360 - rot) % 360);
        return mc.level.getBlockState(new BlockPos(r[0] + cx, y, r[1] + cz)).getBlock();
    }

    private static void detectSubvariant(Minecraft mc) {
        StringBuilder sb = new StringBuilder();

        // doors 0-4 = wool columns at z 19..15; any block present = column extended (door closed).
        // color-agnostic on purpose - exact wool matching broke on some boards (like Odin does it)
        for (int idx = 0; idx < 5; idx++) {
            int[] pos = fromComp(15, 19 - idx);
            if (pos == null) continue;

            if (!mc.level.getBlockState(new BlockPos(pos[0], 56, pos[1])).isAir()) {
                sb.append(idx);
            }
        }

        String detected = sb.toString();

        if (detected.length() == 3) {
            subvariant = detected;
            TeslaMaps.debug("[WaterBoardSolver] Detected subvariant (doors): '{}'", subvariant);
            loadSolution();
        }
    }

    private static void loadSolution() {
        if (SOLUTIONS == null || variant < 0 || subvariant == null) return;

        String variantKey = String.valueOf(variant + 1);

        TeslaMaps.debug("[WaterBoardSolver] Looking up solution for variant={} doors={}", variantKey, subvariant);

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

        TeslaMaps.debug("[WaterBoardSolver] Loaded solution with {} lever types", solution.size());
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
                    TeslaMaps.debug("[WaterBoardSolver] {} clicked, {} times remaining",
                        leverType.name, times.size());

                    if (leverType == LeverType.WATER && waterStartMillis == 0) {
                        waterStartMillis = System.currentTimeMillis();
                        TeslaMaps.debug("[WaterBoardSolver] Water lever clicked, timer started");
                    }
                }
                break;
            }
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
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

        if (!remaining.isEmpty()) {
            LeverTime first = remaining.get(0);
            Vec3 firstCenter = Vec3.atCenterOf(first.pos);
            LeverTime second = remaining.size() > 1 ? remaining.get(1) : null;
            boolean hasSecond = second != null && !second.pos.equals(first.pos);

            ESPRenderer.drawBoxOutline(matrices, new AABB(first.pos), colorFirst, 3f, cameraPos);
            if (hasSecond) {
                ESPRenderer.drawBoxOutline(matrices, new AABB(second.pos), colorSecond, 2f, cameraPos);
            }

            if (TeslaMapsConfig.get().waterBoardTracers) {
                ESPRenderer.drawTracerFromCamera(matrices, firstCenter, colorFirst, cameraPos);
                if (hasSecond) {
                    ESPRenderer.drawLine(matrices, firstCenter, Vec3.atCenterOf(second.pos), colorSecond, 2f, cameraPos);
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

                Vec3 textPos = Vec3.atCenterOf(leverPos).add(0, i * 0.5 + 1.5, 0);
                ESPRenderer.drawText(matrices, text, textPos, 1.5f, cameraPos);
            }
        }
    }

    public static void renderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!TeslaMapsConfig.get().solveWaterBoard || solution == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;

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

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int baseX = screenWidth / 2 - 60;
        int baseY = 10;
        int lineHeight = 12;
        int height = Math.min(remaining.size(), 8) * lineHeight + 20;

        context.fill(baseX - 4, baseY - 4, baseX + 124, baseY + height, 0xAA000000);
        context.text(mc.font, "Water Board (v" + (variant+1) + " " + subvariant + ")", baseX, baseY, 0xFF55FFFF);
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

            context.text(mc.font, text, baseX, baseY + count * lineHeight, color);
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
        variantAttempts = 0;
    }

    private record LeverTime(String name, BlockPos pos, double time, int index) {}
}
