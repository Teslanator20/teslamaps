package com.teslamaps.features.mining;

// Ported from Skyblocker's FossilSolver / FossilCalculations / Structures / FossilTypes
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fossil Excavator: chance per tile from every fossil shape that still fits, best tile in green. */
public final class FossilExcavatorSolver {

    private static final int W = 9, H = 6;
    private static final byte UNKNOWN = 0, EMPTY = 1, FOSSIL = 2;
    private static final Pattern PERCENT = Pattern.compile("Fossil Excavation Progress: (\\d{1,2}(?:\\.\\d)?)%");
    private static final Pattern CHARGES = Pattern.compile("Chisel Charges Remaining: (\\d{1,2})");

    // rotations: 0/90/180/270, then 4..7 = flipped + 0/90/180/270
    private enum Fossil {
        CLAW("Claw", "7.7", new int[]{0, 2, 5, 6},
                ".X....", "XXXX..", ".XX.X.", ".X.X.X", "..X.X."),
        TUSK("Tusk", "12.5", new int[]{0, 1, 2, 7},
                "....X", ".X..X", "X...X", ".X.X.", "..X.."),
        UGLY("Ugly", "6.2", new int[]{0, 1, 2, 3},
                "..XX..", ".XXXX.", "XXXXXX", ".XXXX."),
        HELIX("Helix", "7.1", new int[]{0, 1, 2, 7},
                "XXXXX", "X...X", "X.X.X", "X.XXX"),
        WEBBED("Webbed", "10", new int[]{0, 4},
                "...X...", "X..X..X", ".X.X.X.", "..XXX.."),
        FOOTPRINT("Footprint", "7.7", new int[]{0, 1, 2, 3},
                "X.X.X", "X.X.X", ".XXX.", ".XXX.", "..X.."),
        CLUBBED("Clubbed", "9.1", new int[]{0, 2, 4, 6},
                "......XX", ".X....XX", "X....X..", ".XXXX..."),
        SPINE("Spine", "8.3", new int[]{0, 1, 2, 3},
                "..XX..", ".XXXX.", "XXXXXX");

        final String label, percent;
        final int[] transforms;
        final boolean[][] grid;
        final int tiles;

        Fossil(String label, String percent, int[] transforms, String... rows) {
            this.label = label;
            this.percent = percent;
            this.transforms = transforms;
            grid = new boolean[rows.length][rows[0].length()];
            int n = 0;
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    grid[y][x] = rows[y].charAt(x) == 'X';
                    if (grid[y][x]) n++;
                }
            }
            tiles = n;
        }
    }

    private record Placement(Fossil type, boolean[][] grid, int ox, int oy) {
        /** 1 fossil, 0 empty inside the shape, -1 outside. */
        int at(int x, int y) {
            int gx = x - ox, gy = y - oy;
            if (gy < 0 || gy >= grid.length || gx < 0 || gx >= grid[0].length) return -1;
            return grid[gy][gx] ? 1 : 0;
        }

        boolean fits(byte[] state, String percent) {
            if (percent != null && !percent.equals(type.percent)) return false;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    byte s = state[y * W + x];
                    if (s == FOSSIL && at(x, y) != 1) return false;
                    if (s == EMPTY && at(x, y) == 1) return false;
                }
            }
            return true;
        }
    }

    private static List<Placement> all;
    private static Feature feature;
    private static AbstractContainerMenu menu;
    private static int menuState = -1;
    private static boolean inMenu;
    private static String percent;
    private static int charges = -1;
    private static final double[] chance = new double[W * H];
    private static double best;
    private static int permutations = -1, minimum;
    private static String name;

    private FossilExcavatorSolver() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("FossilExcavatorSolver").section("Mining").when(() -> cfg().fossilSolver)
                .tick(FossilExcavatorSolver::tick)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Fossil Excavator Solver", () -> c.fossilSolver, v -> c.fossilSolver = v)
                            .desc("In the Fossil Excavator every hidden tile gets its fossil chance from all shapes that still fit; the best one is green. The tooltip shows possible patterns, minimum tiles left against your chisel charges and the fossil once it is certain.")
                            .tags("fossil", "excavator", "chisel", "glacite", "solver");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (inMenu && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean now = mc.screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Fossil Excavator");
        if (!now) {
            if (inMenu) {
                percent = null;
                charges = -1;
                menu = null;
            }
            inMenu = false;
            return;
        }
        inMenu = true;
        AbstractContainerMenu open = ((AbstractContainerScreen<?>) mc.screen).getMenu();
        if (open == menu && open.getStateId() == menuState) return;
        menu = open;
        menuState = open.getStateId();
        if (open.slots.size() < W * H) return;
        solve(open);
    }

    private static void solve(AbstractContainerMenu open) {
        if (all == null) all = placements();
        byte[] state = new byte[W * H];
        int fossils = 0;
        for (int i = 0; i < W * H; i++) {
            ItemStack st = open.getSlot(i).getItem();
            Item it = st.getItem();
            state[i] = it == Items.WHITE_STAINED_GLASS_PANE ? FOSSIL : it == Items.BROWN_STAINED_GLASS_PANE ? UNKNOWN : EMPTY;
            if (state[i] == FOSSIL) fossils++;
            if (st.isEmpty()) continue;
            for (String l : ItemUtil.loreLines(st)) {
                if (percent == null) {
                    Matcher m = PERCENT.matcher(l);
                    if (m.find()) percent = m.group(1);
                }
                Matcher c = CHARGES.matcher(l);
                if (c.find()) charges = Integer.parseInt(c.group(1));
            }
        }
        int[] total = new int[W * H];
        List<Placement> valid = new ArrayList<>();
        minimum = W * H;
        for (Placement p : all) {
            if (!p.fits(state, percent)) continue;
            valid.add(p);
            minimum = Math.min(minimum, p.type.tiles - fossils);
        }
        permutations = valid.size();
        name = valid.isEmpty() ? null : valid.get(0).type.label;
        for (Placement p : valid) if (!p.type.label.equals(name)) { name = null; break; }
        best = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (state[i] != UNKNOWN) { chance[i] = 0; continue; }
                for (Placement p : valid) if (p.at(x, y) == 1) total[i]++;
                chance[i] = valid.isEmpty() ? 0 : total[i] / (double) valid.size();
                best = Math.max(best, chance[i]);
            }
        }
    }

    /** From SlotBackgrounds, behind the item. */
    public static void drawHighlight(GuiGraphicsExtractor g, Slot slot) {
        if (!inMenu || permutations <= 0 || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs.getMenu() != menu) return;
        if (slot.container == mc.player.getInventory()) return;
        int i = slot.getContainerSlot();
        if (i < 0 || i >= W * H) return;
        double c = chance[i];
        if (c <= 0) return;
        if (c == best) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xC055FF55);
        else g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ((int) (c * 255) << 24) | 0x0000FF);
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        Item it = stack.getItem();
        if (it != Items.GRAY_STAINED_GLASS_PANE && it != Items.BROWN_STAINED_GLASS_PANE) return;
        lines.add(Component.empty());
        if (permutations == 0) {
            lines.add(Component.literal("§6No fossil shape fits"));
            return;
        }
        if (permutations < 0) return;
        lines.add(Component.literal("§7Possible patterns: §e" + permutations));
        lines.add(Component.literal("§7Minimum tiles left: " + (charges < 0 || charges >= minimum ? "§e" : "§c") + minimum));
        Minecraft mc = Minecraft.getInstance();
        if (it == Items.BROWN_STAINED_GLASS_PANE && mc.screen instanceof AbstractContainerScreen<?> cs) {
            Slot s = ((HandledScreenAccessor) cs).getFocusedSlot();
            if (s != null && s.container != mc.player.getInventory()) {
                int i = s.getContainerSlot();
                if (i >= 0 && i < W * H) lines.add(Component.literal("§7Fossil chance: §e" + Math.round(chance[i] * 100) + "%"));
            }
        }
        if (name != null && percent != null) lines.add(Component.literal("§7Fossil: §e" + name));
    }

    private static List<Placement> placements() {
        List<Placement> out = new ArrayList<>();
        for (Fossil f : Fossil.values()) {
            for (int t : f.transforms) {
                boolean[][] g = f.grid;
                if (t >= 4) g = flip(g);
                for (int r = 0; r < t % 4; r++) g = rotate(g);
                for (int x = 0; x <= W - g[0].length; x++) {
                    for (int y = 0; y <= H - g.length; y++) out.add(new Placement(f, g, x, y));
                }
            }
        }
        return out;
    }

    // same as Skyblocker's flipGrid: mirror top to bottom
    private static boolean[][] flip(boolean[][] g) {
        int h = g.length;
        boolean[][] o = new boolean[h][];
        for (int y = 0; y < h; y++) o[y] = g[h - 1 - y].clone();
        return o;
    }

    // 90 degrees, as Skyblocker's rotateGrid(90)
    private static boolean[][] rotate(boolean[][] g) {
        int h = g.length, w = g[0].length;
        boolean[][] o = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) o[x][h - 1 - y] = g[y][x];
        }
        return o;
    }
}
