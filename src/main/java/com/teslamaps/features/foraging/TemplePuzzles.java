package com.teslamaps.features.foraging;

// Ported from Skyblocker's DesertTemplePuzzle.java (Torrhus) and TerracottaPuzzle.java (Galatea)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Desert Temple button order (Torrhus Canyon) and Forest Temple terracotta rotations (Galatea). */
public final class TemplePuzzles {

    // ---- desert temple ----
    private static final Map<DyeColor, BlockPos> BUTTONS = new EnumMap<>(DyeColor.class);
    static {
        BUTTONS.put(DyeColor.BROWN, new BlockPos(-611, 49, 232));
        BUTTONS.put(DyeColor.RED, new BlockPos(-612, 49, 232));
        BUTTONS.put(DyeColor.ORANGE, new BlockPos(-613, 49, 232));
        BUTTONS.put(DyeColor.YELLOW, new BlockPos(-614, 48, 232));
        BUTTONS.put(DyeColor.LIME, new BlockPos(-614, 47, 232));
        BUTTONS.put(DyeColor.GREEN, new BlockPos(-614, 46, 232));
        BUTTONS.put(DyeColor.CYAN, new BlockPos(-613, 45, 232));
        BUTTONS.put(DyeColor.LIGHT_BLUE, new BlockPos(-612, 45, 232));
        BUTTONS.put(DyeColor.BLUE, new BlockPos(-611, 45, 232));
        BUTTONS.put(DyeColor.PURPLE, new BlockPos(-610, 46, 232));
        BUTTONS.put(DyeColor.MAGENTA, new BlockPos(-610, 47, 232));
        BUTTONS.put(DyeColor.PINK, new BlockPos(-610, 48, 232));
    }
    private static final BlockPos GLASS_FLOOR_CENTRE = new BlockPos(-612, 42, 227);
    private static final BlockPos GLASS_WHEEL_CENTRE = new BlockPos(-612, 47, 233);
    // bird, snake, frog, fly: statue -> where it has to go
    private static final int[][] STATUES = {
            {-618, 46, 248, -616, 46, 248}, {-607, 46, 249, -607, 46, 247},
            {-606, 46, 240, -608, 46, 240}, {-617, 46, 241, -617, 46, 239}};
    private static final Vec3[][] ARROWS = new Vec3[STATUES.length][];
    static {
        for (int i = 0; i < STATUES.length; i++) {
            int[] s = STATUES[i];
            Vec3 a = new Vec3(s[0] + 0.5, s[1] + 0.5, s[2] + 0.5), b = new Vec3(s[3] + 0.5, s[4] + 0.5, s[5] + 0.5);
            Vec3 d = b.subtract(a).normalize();
            Vec3 p = new Vec3(-d.z, 0, d.x);
            Vec3 back = b.subtract(d.scale(0.3));
            ARROWS[i] = new Vec3[]{a, b, back.add(p.scale(0.3)), back.subtract(p.scale(0.3)),
                    back.add(0, 0.3, 0), back.subtract(0, 0.3, 0)};
        }
    }

    private static List<Vec3> buttonLabels = List.of();
    private static boolean desertHere;
    private static int desertTicks;

    // ---- forest temple ----
    private static final BlockPos WALL_TOP_LEFT = new BlockPos(-633, 66, 85);
    private static final BlockPos WALL_BOTTOM_RIGHT = new BlockPos(-640, 65, 85);
    private static final BlockPos FLOOR_TOP_LEFT = new BlockPos(-633, 59, 76);
    private static final BlockPos FLOOR_BOTTOM_LEFT = new BlockPos(-633, 59, 75);

    private record Label(Vec3 pos, String text) {}
    private static List<Label> floorLabels = List.of();
    private static boolean forestHere;
    private static int forestTicks;

    private TemplePuzzles() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DesertTemplePuzzle").section("Torrhus Canyon").when(() -> cfg().desertTemplePuzzle)
                .tick(TemplePuzzles::tickDesert)
                .world(TemplePuzzles::renderDesert)
                .config("Torrhus Canyon", b -> {
                    b.label("Desert Temple");
                    b.toggle("Desert Temple Puzzle", () -> cfg().desertTemplePuzzle, v -> cfg().desertTemplePuzzle = v)
                            .desc("Button order from the glass floor (rarest colour first) and arrows for the animal statues.")
                            .tags("desert", "temple", "puzzle", "solver");
                });
        Feature.of("ForestTemplePuzzle").section("Foraging").when(() -> cfg().forestTemplePuzzle)
                .tick(TemplePuzzles::tickForest)
                .world(TemplePuzzles::renderForest)
                .config("Foraging", b -> {
                    b.label("Forest Temple");
                    b.toggle("Forest Temple Puzzle", () -> cfg().forestTemplePuzzle, v -> cfg().forestTemplePuzzle = v)
                            .desc("Galatea: rotations per floor terracotta. Positive = right click, negative = left click.")
                            .tags("forest", "temple", "terracotta", "puzzle", "solver");
                });
    }

    // ---------------------------------------------------------------- desert

    private static void tickDesert() {
        if (desertTicks++ % 20 == 0) {
            String sub = Foraging.inTorrhus() ? FrogMask.subArea() : "";
            desertHere = sub.equals("Torrhus Springs") || sub.equals("Desert Temple");
            if (!desertHere) buttonLabels = List.of();
        }
        if (!desertHere || desertTicks % 100 != 1) return;
        ClientLevel level = Minecraft.getInstance().level;
        List<DyeColor> order = solveDesert(level);
        List<Vec3> out = new ArrayList<>(order.size());
        for (DyeColor c : order) {
            BlockPos p = BUTTONS.get(c);
            if (p != null) out.add(Vec3.atBottomCenterOf(p).add(0, 0.2, 0.2));
        }
        buttonLabels = out;
    }

    private static List<DyeColor> solveDesert(ClientLevel level) {
        Map<DyeColor, Integer> counts = new EnumMap<>(DyeColor.class);
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(GLASS_FLOOR_CENTRE);
        visited.add(GLASS_FLOOR_CENTRE);
        while (!queue.isEmpty() && visited.size() < 4096) {
            BlockPos cur = queue.remove();
            if (level.getBlockState(cur).getBlock() instanceof StainedGlassBlock g) counts.merge(g.getColor(), 1, Integer::sum);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = cur.relative(d);
                if (visited.contains(n) || !(level.getBlockState(n).getBlock() instanceof StainedGlassBlock)) continue;
                visited.add(n);
                queue.add(n);
            }
        }
        List<DyeColor> order = new ArrayList<>(counts.keySet());
        order.sort((a, b) -> Integer.compare(counts.get(a), counts.get(b)));
        return order;
    }

    private static void renderDesert(PoseStack pose, Vec3 cam) {
        if (!desertHere) return;
        Minecraft mc = Minecraft.getInstance();
        List<Vec3> labels = buttonLabels;
        if (!labels.isEmpty() && !mc.level.getBlockState(GLASS_WHEEL_CENTRE).isAir()) {
            for (int i = 0; i < labels.size(); i++) {
                ESPRenderer.drawText(pose, NUMBERS[Math.min(i, NUMBERS.length - 1)], labels.get(i), 1.0f, cam, false, true);
            }
        }
        for (Vec3[] a : ARROWS) {
            ESPRenderer.drawLine(pose, a[0], a[1], 0xFFFFFFFF, 5f, cam, false);
            for (int i = 2; i < a.length; i++) ESPRenderer.drawLine(pose, a[i], a[1], 0xFFFFFFFF, 5f, cam, false);
        }
    }

    private static final String[] NUMBERS = {"§e1", "§e2", "§e3", "§e4", "§e5", "§e6", "§e7", "§e8", "§e9", "§e10", "§e11", "§e12"};

    // ---------------------------------------------------------------- forest

    private static void tickForest() {
        if (forestTicks++ % 20 == 0) {
            forestHere = Foraging.inMoonglade() && FrogMask.subArea().equals("Forest Temple");
            if (!forestHere) floorLabels = List.of();
        }
        if (!forestHere || forestTicks % 5 != 0) return;
        ClientLevel level = Minecraft.getInstance().level;
        List<Direction> wall = new ArrayList<>();
        for (int x = WALL_TOP_LEFT.getX(); x >= WALL_BOTTOM_RIGHT.getX(); x--) {
            for (int y = WALL_TOP_LEFT.getY(); y >= WALL_BOTTOM_RIGHT.getY(); y--) {
                BlockState s = level.getBlockState(new BlockPos(x, y, WALL_TOP_LEFT.getZ()));
                if (!isTerracotta(s)) continue;
                wall.add(mapWall(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));
            }
        }
        List<Label> out = new ArrayList<>();
        for (int i = 0; i < wall.size(); i++) {
            int row = i / 2;
            BlockPos pos = i % 2 == 0 ? FLOOR_TOP_LEFT.offset(-row, 0, 0) : FLOOR_BOTTOM_LEFT.offset(-row, 0, 0);
            BlockState s = level.getBlockState(pos);
            if (!isTerracotta(s)) continue;
            int n = rotations(s.getValue(BlockStateProperties.HORIZONTAL_FACING), wall.get(i));
            if (n != 0) out.add(new Label(Vec3.atCenterOf(pos).add(0, 1, 0), (n > 0 ? "§a" : "§c") + n));
        }
        floorLabels = out;
    }

    private static boolean isTerracotta(BlockState s) {
        return s.is(Blocks.GLAZED_TERRACOTTA.orange()) && s.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
    }

    private static Direction mapWall(Direction d) {
        return switch (d) {
            case NORTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    /** Shortest turn count, positive = clockwise (right click), negative = counter-clockwise. */
    private static int rotations(Direction from, Direction to) {
        Direction cw = from, ccw = from;
        for (int i = 1; i <= 2; i++) {
            if (from == to) return 0;
            cw = cw.getClockWise();
            if (cw == to) return i;
            ccw = ccw.getCounterClockWise();
            if (ccw == to) return -i;
        }
        return 0;
    }

    private static void renderForest(PoseStack pose, Vec3 cam) {
        List<Label> labels = floorLabels;
        for (Label l : labels) ESPRenderer.drawText(pose, l.text(), l.pos(), 1.2f, cam, false, true);
    }
}
