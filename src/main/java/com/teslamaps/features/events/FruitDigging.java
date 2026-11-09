package com.teslamaps.features.events;

// Ported from SkyHanni's CarnivalFruitDigging.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FruitDigging {

    static final int SIZE = 7;
    static final int MAX_DIGS = 15;
    private static final int START_X = -112, START_Z = 19, GRID_Y = 72;
    private static final AABB AREA = new AABB(START_X, GRID_Y - 1, START_Z, START_X + SIZE, GRID_Y + 4, START_Z + SIZE);

    private static final Pattern TREASURE = Pattern.compile("^TREASURE! There is an? (.*) nearby\\.$");
    private static final Pattern NO_FRUIT = Pattern.compile("^(?:TREASURE|ANCHOR)! There are no fruits nearby!$");
    private static final Pattern MINES = Pattern.compile("^MINES! There (?:is|are) (\\d+) bombs? hidden nearby\\.$");
    private static final Pattern REVEAL = Pattern.compile("^([A-Za-z ]+)(?: \\(\\+\\d+\\))?$");
    private static final List<String> SAMPLE = List.of("§6§lFruit Digging Solver", "§7Rounds: §e3§7/§e15", "§aDig: §fC4",
            "§7Use: §aCarnival Shovel §8(§cAnchor§8)", "§cBomb: §e12% §7Samples: §e500");

    enum Fruit {
        UNKNOWN("Unknown", 0, 0, null, false),
        NO_FRUIT("No Fruit", 0, 0, null, false),
        BOMB("Bomb", 0, 10, "a76a2811d1e176a07b6d0a657b910f134896ce30850f6e80c7c83732d85381ea", false),
        RUM("Rum", 0, 5, "407b275d28b927b1bf7f6dd9f45fbdad2af8571c54c8f027d1bff6956fbf3c16", false),
        MANGO("Mango", 300, 10, "f363a62126a35537f8189343a22660de75e810c6ac004a7d3da65f1c040a839", true),
        APPLE("Apple", 100, 8, "17ea278d6225c447c5943d652798d0bbbd1418434ce8c54c54fdac79994ddd6c", true),
        WATERMELON("Watermelon", 100, 4, "efe4ef83baf105e8dee6cf03dfe7407f1911b3b9952c891ae34139560f2931d6", true),
        POMEGRANATE("Pomegranate", 200, 4, "40824d18079042d5769f264f44394b95b9b99ce689688cc10c9eec3f882ccc08", true),
        COCONUT("Coconut", 200, 3, "10ceb1455b471d016a9f06d25f6e468df9fcf223e2c1e4795b16e84fcca264ee", true),
        CHERRY("Cherry", 200, 2, "c92b099a62cd2fbf8ada09dec145c75d7fda4dc57b968bea3a8fa11e37aa48b2", true),
        DURIAN("Durian", 800, 2, "ac268d36c2c6047ffeec00124096376b56dbb4d756a55329363a1b27fcd659cd", true),
        DRAGON_FRUIT("Dragonfruit", 1200, 1, "3cc761bcb0579763d9b8ab6b7b96fa77eb6d9605a804d838fec39e7b25f95591", true);

        final String display;
        final int points, count;
        final String texture;
        final boolean edible;

        Fruit(String display, int points, int count, String texture, boolean edible) {
            this.display = display;
            this.points = points;
            this.count = count;
            this.texture = texture;
            this.edible = edible;
        }

        static Fruit byTexture(String tex) {
            if (tex == null) return null;
            for (Fruit f : values()) if (tex.equals(f.texture)) return f;
            return null;
        }

        static Fruit byName(String name) {
            for (Fruit f : values()) if (f.display.equalsIgnoreCase(name)) return f;
            return null;
        }
    }

    private static final class Cell {
        Integer mines;
        Fruit treasure = Fruit.UNKNOWN, solved = Fruit.UNKNOWN, anchored = Fruit.UNKNOWN, uncovered = Fruit.UNKNOWN;
        boolean diggable = true, removed;

        Fruit found() { return uncovered != Fruit.UNKNOWN ? uncovered : solved; }

        FruitDiggingSolver.CellInput input() {
            Fruit content = uncovered != Fruit.UNKNOWN ? uncovered : solved != Fruit.UNKNOWN ? solved : null;
            return new FruitDiggingSolver.CellInput(content, diggable, !diggable && content == null, mines,
                    treasure != Fruit.UNKNOWN ? treasure : null, anchored != Fruit.UNKNOWN ? anchored : null);
        }
    }

    private record Label(Vec3 pos, String text, int color) {}

    private static final FruitDiggingSolver SOLVER = new FruitDiggingSolver(SIZE);
    private static Cell[][] grid = newGrid();
    private static final Map<Fruit, Integer> remaining = new EnumMap<>(Fruit.class);
    private static boolean playing, dirty;
    private static int lastRow = -1, lastCol = -1, digs;
    private static FruitDiggingSolver.Recommendation rec;
    private static final Map<Integer, String> standNames = new HashMap<>();
    private static final Set<Integer> seenItems = new HashSet<>();
    private static List<Label> labels = List.of();
    private static List<AABB> dugBoxes = List.of();
    private static List<String> hudLines = List.of();
    private static Object lastLevel;
    private static HudEditElement hud;

    static { resetRemaining(); }

    private FruitDigging() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static Cell[][] newGrid() {
        Cell[][] g = new Cell[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) g[r][c] = new Cell();
        return g;
    }

    private static void resetRemaining() {
        remaining.clear();
        for (Fruit f : Fruit.values()) remaining.put(f, f.count);
    }

    public static void register() {
        hud = HudEditElement.of("fruitDiggingHud", "Fruit Digging Solver").ctx(HudEditElement.HudCtx.ANY).section(Carnival.CATEGORY)
                .enabled(() -> cfg().carnivalFruitDigging && (cfg().fruitDiggingBestDig || cfg().fruitDiggingRemaining))
                .size(() -> TextHud.width(hudLines.isEmpty() ? SAMPLE : hudLines), () -> TextHud.height(hudLines.isEmpty() ? SAMPLE.size() : hudLines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, hudLines.isEmpty() ? SAMPLE : hudLines,
                        TextHud.width(hudLines.isEmpty() ? SAMPLE : hudLines)));
        Feature.of("FruitDigging").section(Carnival.CATEGORY)
                .when(() -> cfg().carnivalFruitDigging)
                .tick(FruitDigging::tick)
                .serverChat(FruitDigging::onChat)
                .world(FruitDigging::render)
                .hudHiddenInInventory("fruitdigging", FruitDigging::renderHud)
                .config(Carnival.CATEGORY, b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Fruit Digging Solver", () -> c.carnivalFruitDigging, v -> c.carnivalFruitDigging = v)
                            .desc("Carnival Fruit Digging: reads the 7x7 field and the dowsing hints, estimates what lies under every cell and recommends the next dig plus the shovel mode. HUD position in /tmap gui")
                            .tags("carnival", "fruit", "digging", "solver", "pirateman");
                    b.toggle("  Show Dug / Destroyed Fruit", () -> c.fruitDiggingFound, v -> c.fruitDiggingFound = v);
                    b.toggle("  Show Treasure / Anchor / Mines Hints", () -> c.fruitDiggingClues, v -> c.fruitDiggingClues = v);
                    b.toggle("  Show Fruit Guesses", () -> c.fruitDiggingGuesses, v -> c.fruitDiggingGuesses = v);
                    b.toggle("  Best Dig", () -> c.fruitDiggingBestDig, v -> c.fruitDiggingBestDig = v);
                    b.color("  Best Dig Color", () -> c.colorFruitDiggingBest, v -> c.colorFruitDiggingBest = v);
                    b.toggle("  Remaining Fruit", () -> c.fruitDiggingRemaining, v -> c.fruitDiggingRemaining = v);
                });
    }

    private static int[] cellOf(double x, double z) {
        int col = (int) Math.floor(x - START_X), row = (int) Math.floor(z - START_Z);
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return null;
        return new int[]{row, col};
    }

    private static void reset() {
        playing = false;
        grid = newGrid();
        resetRemaining();
        lastRow = lastCol = -1;
        rec = null;
        dirty = false;
        digs = 0;
        standNames.clear();
        seenItems.clear();
        labels = List.of();
        dugBoxes = List.of();
        hudLines = List.of();
    }

    private static Fruit lastDug() {
        if (lastRow < 0) return Fruit.NO_FRUIT;
        return grid[lastRow][lastCol].uncovered;
    }

    private static int dugSoFar(Fruit f) {
        return f.count - remaining.getOrDefault(f, 0);
    }

    private static int pointValue(Fruit f) {
        Fruit last = lastDug();
        double mul = last == Fruit.POMEGRANATE ? 1.5 : last == Fruit.DURIAN ? 0.5 : 1.0;
        if (f == Fruit.APPLE) return (int) (mul * (f.points + 100 * dugSoFar(f)));
        if (f == Fruit.CHERRY) return (int) (mul * (f.points + 300 * dugSoFar(f)));
        return (int) (mul * f.points);
    }

    private static void onChat(String raw) {
        if (!Carnival.inArea()) return;
        String s = StringUtil.stripTrim(raw);
        if (s.equals("[NPC] Carnival Pirateman: Good luck, matey!")) {
            reset();
            playing = true;
            dirty = true;
            return;
        }
        if (!playing) return;
        if (s.equals("Fruit Digging")) {
            reset();
            return;
        }
        Matcher m = MINES.matcher(s);
        if (m.matches()) {
            if (lastRow >= 0) {
                grid[lastRow][lastCol].mines = Integer.parseInt(m.group(1));
                dirty = true;
            }
            return;
        }
        m = TREASURE.matcher(s);
        if (m.matches()) {
            Fruit f = Fruit.byName(m.group(1));
            if (f != null && lastRow >= 0) {
                grid[lastRow][lastCol].treasure = f;
                dirty = true;
            }
            return;
        }
        if (NO_FRUIT.matcher(s).matches() && lastRow >= 0) {
            Cell c = grid[lastRow][lastCol];
            c.treasure = Fruit.NO_FRUIT;
            c.anchored = Fruit.NO_FRUIT;
            dirty = true;
        }
    }

    static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!playing || !oldState.is(Blocks.SAND) || pos.getY() < GRID_Y - 2 || pos.getY() > GRID_Y + 2) return;
        int[] rc = cellOf(pos.getX(), pos.getZ());
        if (rc == null) return;
        Cell cell = grid[rc[0]][rc[1]];
        cell.diggable = false;
        if (newState.is(Blocks.SANDSTONE)) {
            lastRow = rc[0];
            lastCol = rc[1];
            digs++;
        } else if (newState.is(Blocks.SANDSTONE_STAIRS)) {
            removeRemaining(cell, cell.solved);
        }
        dirty = true;
    }

    private static void removeRemaining(Cell cell, Fruit f) {
        if (f == Fruit.UNKNOWN || f == Fruit.NO_FRUIT) return;
        if (!cell.removed && !cell.diggable) {
            Integer n = remaining.get(f);
            if (n == null) return;
            remaining.put(f, n - 1);
            cell.removed = true;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
            return;
        }
        if (!playing) return;
        if (!Carnival.inArea()) {
            reset();
            return;
        }
        scanEntities(mc);
        if (!dirty) return;
        dirty = false;
        Recompute.run();
    }

    private static void scanEntities(Minecraft mc) {
        for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, AREA)) {
            Component cn = s.getCustomName();
            if (cn == null) continue;
            String name = StringUtil.stripTrim(cn.getString());
            if (name.isBlank() || name.equals(standNames.get(s.getId()))) continue;
            standNames.put(s.getId(), name);
            int[] rc = cellOf(s.getBlockX(), s.getBlockZ());
            if (rc == null) continue;
            Matcher m = REVEAL.matcher(name);
            if (!m.matches()) continue;
            Fruit f = Fruit.byName(m.group(1).trim());
            if (f == null) continue;
            Cell cell = grid[rc[0]][rc[1]];
            removeRemaining(cell, f);
            cell.uncovered = f;
            dirty = true;
        }
        for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, AREA)) {
            if (e.getItem().isEmpty() || !seenItems.add(e.getId())) continue;
            anchor(e);
        }
    }

    private static void anchor(ItemEntity e) {
        int[] rc = cellOf(e.getX(), e.getZ());
        if (rc == null || lastRow < 0) return;
        Fruit f = Fruit.byTexture(SoulweaverHider.headTextureUrl(e.getItem()));
        if (f == null) f = Fruit.byName(StringUtil.stripTrim(e.getItem().getHoverName().getString()));
        if (f == null) return;
        Cell solved = grid[rc[0]][rc[1]];
        Cell dug = grid[lastRow][lastCol];
        if (solved.solved == Fruit.UNKNOWN) {
            solved.solved = f;
            removeRemaining(solved, f);
            dirty = true;
        }
        if (solved.diggable && dug.anchored == Fruit.UNKNOWN) {
            dug.anchored = f;
            dirty = true;
        }
    }

    /** Solver + display rebuild, run from the tick when something changed. */
    private static final class Recompute {
        static void run() {
            TeslaMapsConfig c = cfg();
            FruitDiggingSolver.CellInput[][] in = new FruitDiggingSolver.CellInput[SIZE][SIZE];
            for (int r = 0; r < SIZE; r++) for (int col = 0; col < SIZE; col++) in[r][col] = grid[r][col].input();
            Fruit last = lastDug();
            double mul = last == Fruit.POMEGRANATE ? 1.5 : last == Fruit.DURIAN ? 0.5 : 1.0;
            rec = c.fruitDiggingBestDig ? SOLVER.recommend(in, digs, mul, last == Fruit.COCONUT) : null;

            List<Label> ls = new ArrayList<>();
            List<AABB> boxes = new ArrayList<>();
            for (int r = 0; r < SIZE; r++) {
                for (int col = 0; col < SIZE; col++) {
                    Cell cell = grid[r][col];
                    int x = START_X + col, z = START_Z + r;
                    if (!cell.diggable) boxes.add(new AABB(x, GRID_Y, z, x + 1, GRID_Y + 1.1, z + 1));
                    List<String> text = new ArrayList<>();
                    List<Integer> colors = new ArrayList<>();
                    if (c.fruitDiggingFound && !cell.diggable && cell.found() != Fruit.UNKNOWN) {
                        text.add(cell.found().display);
                        colors.add(0xFF55FF55);
                    }
                    if (c.fruitDiggingGuesses && cell.uncovered == Fruit.UNKNOWN && cell.diggable && cell.solved != Fruit.UNKNOWN) {
                        text.add(cell.solved.display + " (" + pointValue(cell.solved) + ")");
                        colors.add(0xFF55FFFF);
                    }
                    if (c.fruitDiggingClues) {
                        if (cell.treasure != Fruit.UNKNOWN) {
                            text.add("≤ " + cell.treasure.display);
                            colors.add(0xFFFFAA00);
                        }
                        if (cell.anchored != Fruit.UNKNOWN && cell.anchored != cell.treasure) {
                            text.add("≥ " + cell.anchored.display);
                            colors.add(0xFFFFAA00);
                        }
                        if (cell.mines != null) {
                            text.add(String.valueOf(cell.mines));
                            colors.add(0xFFFF5555);
                        }
                    }
                    for (int i = 0; i < text.size(); i++) {
                        double y = GRID_Y + 1.1 + (text.size() - 1 - i) * 0.25;
                        ls.add(new Label(new Vec3(x + 0.5, y, z + 0.5), text.get(i), colors.get(i)));
                    }
                }
            }
            if (rec != null && grid[rec.row()][rec.col()].diggable) {
                int x = START_X + rec.col(), z = START_Z + rec.row();
                ls.add(new Label(new Vec3(x + 0.5, GRID_Y + 2.1, z + 0.5),
                        "§aDig §cHERE §7with §8(§c" + rec.shovel() + "§8) §7" + pct(rec.pBomb()) + " bomb", 0xFFFFFFFF));
            }
            labels = ls;
            dugBoxes = boxes;

            List<String> h = new ArrayList<>();
            if (c.fruitDiggingBestDig && rec != null) {
                h.add("§6§lFruit Digging Solver");
                h.add("§7Rounds: §e" + digs + "§7/§e" + MAX_DIGS);
                h.add("§aDig: §f" + (char) ('A' + rec.row()) + (rec.col() + 1));
                h.add("§7Use: §aCarnival Shovel §8(§c" + rec.shovel() + "§8)");
                h.add("§cBomb: §e" + pct(rec.pBomb()) + " §7Samples: §e" + rec.samples());
            }
            if (c.fruitDiggingRemaining) {
                List<Fruit> left = new ArrayList<>();
                for (Fruit f : Fruit.values()) if (remaining.getOrDefault(f, 0) > 0) left.add(f);
                left.sort(Comparator.comparingInt(FruitDigging::pointValue).reversed().thenComparing(f -> f.display));
                if (!left.isEmpty()) {
                    if (!h.isEmpty()) h.add("");
                    h.add("§eFruit Digging");
                    for (Fruit f : left) h.add("§7" + f.display + " (" + pointValue(f) + "): §f" + remaining.get(f));
                }
            }
            hudLines = h;
        }

        private static String pct(double p) {
            return Math.round(p * 1000) / 10.0 + "%";
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!playing || !Carnival.inArea()) return;
        for (AABB b : dugBoxes) ESPRenderer.drawFilledBox(pose, b, 0x66808080, cam, false);
        FruitDiggingSolver.Recommendation r = rec;
        if (r != null && cfg().fruitDiggingBestDig && grid[r.row()][r.col()].diggable) {
            int x = START_X + r.col(), z = START_Z + r.row();
            int color = TeslaMapsConfig.parseColor(cfg().colorFruitDiggingBest);
            AABB box = new AABB(x, GRID_Y, z, x + 1, GRID_Y + 1, z + 1);
            ESPRenderer.drawFilledBox(pose, box, (color & 0xFFFFFF) | 0x5A000000, cam, true);
            ESPRenderer.drawBoxOutline(pose, box, color | 0xFF000000, 2f, cam, true);
        }
        for (Label l : labels) ESPRenderer.drawText(pose, l.text(), l.pos(), 0.6f, cam, false, true, l.color());
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = hudLines;
        if (l.isEmpty() || hud == null || !playing || !Carnival.inArea()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), l, TextHud.width(l));
    }
}
