package com.teslamaps.features;

// After SkyHanni's BetterContainers.kt (NEU): filler panes out, connected slot/button frames, dye toggles as switches.
// Drawn with fills instead of SkyHanni's CTM textures.
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.injected.CustomizableStack;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class BetterContainers {

    public static final String[] STYLES = {"Dark", "Light", "Transparent", "Vanilla", "Purple", "Ocean"};
    private static final String[] EXCLUDED = {"Navigate the maze", "Ultrasequencer", "Chronomatron", "Superpairs",
            "Harp", "Experimentation Table", "Craft Item"};

    private static final int NONE = 0, BLANK = 1, SLOT = 2, BUTTON = 3, ON = 4, OFF = 5;
    private static final int UP = 1, RIGHT = 2, DOWN = 4, LEFT = 8;
    private static final int MAX = 128;

    private static final int[] kind = new int[MAX];
    private static final int[] edges = new int[MAX];
    private static final int[] sx = new int[MAX], sy = new int[MAX];
    private static int count;
    private static ContainerScreen screen;
    private static Object titleScreen;
    private static boolean titleOk;
    private static Feature feature;

    // bg, border, slot, slot edge, button, button edge, title
    private static final int[][] PALETTES = {
            {0xFF1B1B1F, 0xFF3A3A44, 0xFF2A2A31, 0xFF4B4B57, 0xFF30354A, 0xFF55608A, 0xFFE0E0E0},
            {0xFFE4E4E4, 0xFFFFFFFF, 0xFFCFCFCF, 0xFF9A9A9A, 0xFFD9DDEA, 0xFF8A93B5, 0xFF303030},
            {0x99000000, 0x55FFFFFF, 0x55000000, 0x44FFFFFF, 0x55202A44, 0x66A0B0FF, 0xFFFFFFFF},
            {0, 0, 0, 0, 0, 0, 0},
            {0xFF1E1528, 0xFF4A3466, 0xFF2C203B, 0xFF5E4485, 0xFF3A2450, 0xFF8A5CC0, 0xFFE8D8FF},
            {0xFF0E1C26, 0xFF24506A, 0xFF16303F, 0xFF2E6A8C, 0xFF123E4E, 0xFF3AA0B8, 0xFFD6F2FF},
    };

    private BetterContainers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("BetterContainers").section("Inventory").when(() -> cfg().betterContainers)
                .tickAlways(BetterContainers::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Menu Look");
                    b.toggle("Better Containers", () -> c.betterContainers, v -> c.betterContainers = v)
                            .desc("SkyBlock menus: hides the black filler panes and draws connected slot and button frames.");
                    b.dropdown("  Style", STYLES, () -> c.betterContainersStyle, v -> c.betterContainersStyle = v);
                    b.toggle("  Toggles As Switches", () -> c.betterContainersToggles, v -> c.betterContainersToggles = v)
                            .desc("Dyes with \"Click to enable/disable!\" are drawn as on/off switches.");
                });
    }

    private static int style() {
        String s = cfg().betterContainersStyle;
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(s)) return i;
        return 0;
    }

    private static boolean vanillaStyle() { return style() == 3; }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ContainerScreen cs) || !SkyblockUtils.isInSkyblock() || !titleAllowed(cs)
                || com.teslamaps.dungeon.termgui.TermGui.active() != null || LeapOverlay.shouldRender()
                || TerminalSolver.hideClutter()) {
            screen = null;
            return;
        }
        List<Slot> slots = cs.getMenu().slots;
        int n = Math.min(slots.size(), MAX);
        boolean toggles = cfg().betterContainersToggles;
        boolean blank = false, item = false;
        for (int i = 0; i < n; i++) {
            Slot s = slots.get(i);
            sx[i] = s.x;
            sy[i] = s.y;
            if (s.container instanceof Inventory) { kind[i] = SLOT; continue; }
            ItemStack st = s.getItem();
            int k = classify(st, toggles);
            kind[i] = k;
            if (k == BLANK) blank = true;
            else if (!st.isEmpty()) item = true;
        }
        count = n;
        if (!blank || !item) {
            screen = null;
            return;
        }
        for (int i = 0; i < n; i++) {
            int g = group(kind[i]), e = 0;
            if (g != 0) {
                for (int j = 0; j < n; j++) {
                    if (j == i || group(kind[j]) != g) continue;
                    int dx = sx[j] - sx[i], dy = sy[j] - sy[i];
                    if (dx == 0 && dy == -18) e |= UP;
                    else if (dx == 18 && dy == 0) e |= RIGHT;
                    else if (dx == 0 && dy == 18) e |= DOWN;
                    else if (dx == -18 && dy == 0) e |= LEFT;
                }
            }
            edges[i] = e;
        }
        screen = cs;
    }

    private static boolean titleAllowed(ContainerScreen cs) {
        if (cs != titleScreen) {
            titleScreen = cs;
            String t = StringUtil.stripTrim(cs.getTitle().getString());
            titleOk = true;
            for (String ex : EXCLUDED) if (t.startsWith(ex)) { titleOk = false; break; }
        }
        return titleOk;
    }

    private static int group(int k) {
        return k == SLOT ? 1 : k == BUTTON || k == ON || k == OFF ? 2 : 0;
    }

    private static int classify(ItemStack st, boolean toggles) {
        if (st.isEmpty()) return SLOT;
        CustomizableStack cs = (CustomizableStack) (Object) st;
        if (st.is(Items.STAINED_GLASS_PANE.black()) && st.getCount() == 1 && cs.teslamaps$skyblockId().isEmpty()
                && StringUtil.stripTrim(st.getHoverName().getString()).isEmpty()) return BLANK;
        if (!cs.teslamaps$skyblockId().isEmpty()) return SLOT;
        if (toggles && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("_dye")) {
            List<String> lore = cs.teslamaps$loreLines();
            if (!lore.isEmpty()) {
                String last = lore.get(lore.size() - 1);
                if (last.endsWith("Click to disable!")) return ON;
                if (last.endsWith("Click to enable!")) return OFF;
            }
        }
        return BUTTON;
    }

    public static boolean active(Object s) {
        return s != null && s == screen && feature != null && cfg().betterContainers && feature.active();
    }

    /** True = skip vanilla drawing of this slot. */
    public static boolean hideSlot(Object s, Slot slot) {
        if (!active(s)) return false;
        int i = slot.index;
        if (i < 0 || i >= count || slot.container instanceof Inventory) return false;
        int k = kind[i];
        return k == BLANK || k == ON || k == OFF;
    }

    /** Container title / "Inventory" label color. */
    public static int labelColor(Object s, int original) {
        if (!active(s) || vanillaStyle()) return original;
        return PALETTES[style()][6];
    }

    /** extractBackground HEAD. True = vanilla background is replaced. */
    public static boolean replaceBackground(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs) {
        if (!active(cs) || vanillaStyle()) return false;
        int[] p = PALETTES[style()];
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY(), w = acc.getImageWidth(), h = acc.getImageHeight();
        ctx.fill(left, top, left + w, top + h, p[0]);
        ctx.fill(left, top, left + w, top + 1, p[1]);
        ctx.fill(left, top + h - 1, left + w, top + h, p[1]);
        ctx.fill(left, top, left + 1, top + h, p[1]);
        ctx.fill(left + w - 1, top, left + w, top + h, p[1]);
        for (int i = 0; i < count; i++) {
            int k = kind[i];
            if (k == BLANK || k == NONE) continue;
            boolean button = group(k) == 2;
            cell(ctx, left + sx[i] - 1, top + sy[i] - 1, edges[i], button ? p[4] : p[2], button ? p[5] : p[3]);
            if (k == ON || k == OFF) toggle(ctx, left + sx[i] - 1, top + sy[i] - 1, k == ON);
        }
        return true;
    }

    /** extractBackground TAIL, Vanilla style only: paints over the filler cells. */
    public static void drawOverVanilla(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs) {
        if (!active(cs) || !vanillaStyle()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        for (int i = 0; i < count; i++) {
            int k = kind[i];
            int x = left + sx[i] - 1, y = top + sy[i] - 1;
            if (k == BLANK) ctx.fill(x, y, x + 18, y + 18, 0xFFC6C6C6);
            else if (k == ON || k == OFF) {
                ctx.fill(x, y, x + 18, y + 18, 0xFFC6C6C6);
                toggle(ctx, x, y, k == ON);
            }
        }
    }

    private static void cell(GuiGraphicsExtractor ctx, int x, int y, int e, int fill, int edge) {
        ctx.fill(x, y, x + 18, y + 18, fill);
        if ((e & UP) == 0) ctx.fill(x, y, x + 18, y + 1, edge);
        if ((e & DOWN) == 0) ctx.fill(x, y + 17, x + 18, y + 18, edge);
        if ((e & LEFT) == 0) ctx.fill(x, y, x + 1, y + 18, edge);
        if ((e & RIGHT) == 0) ctx.fill(x + 17, y, x + 18, y + 18, edge);
    }

    private static void toggle(GuiGraphicsExtractor ctx, int x, int y, boolean on) {
        int tx = x + 2, ty = y + 5, tw = 14, th = 8;
        int track = on ? 0xFF3FAF4F : 0xFF6A6A6A;
        ctx.fill(tx + 1, ty, tx + tw - 1, ty + th, track);
        ctx.fill(tx, ty + 1, tx + tw, ty + th - 1, track);
        int kx = on ? tx + tw - 7 : tx + 1;
        ctx.fill(kx, ty + 1, kx + 6, ty + th - 1, 0xFFF2F2F2);
    }
}
