package com.teslamaps.profileviewer.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The look of NotEnoughUpdates' profile viewer: dark translucent panels with a soft grey
 * edge that steps from dark to light, on a dimmed screen. Colors taken off pv_bg.png /
 * pv_basic.png (all panel fills are alpha 77).
 */
public class NeuStyle {
    public static final int PANEL_WIDTH = 431;
    public static final int PANEL_HEIGHT = 202;

    public static final int SCREEN_DIM = 0xA0101010;

    private static final int OUTLINE = 0xFF000000;
    private static final int FRAME = 0xFF555555;
    private static final int BODY = (77 << 24) | 0x373737;
    private static final int BODY_LIGHT = (120 << 24) | 0x4A4A4A;

    // the soft edge of an inner box, dark outside to light inside
    private static final int[] BOX_EDGE = {
            (77 << 24) | 0x0D0D0D,
            (77 << 24) | 0x565656,
            (77 << 24) | 0x636363,
            (77 << 24) | 0x707070,
            (77 << 24) | 0x777777,
            (77 << 24) | 0x7A7A7A,
            (77 << 24) | 0x7D7D7D,
    };
    private static final int BOX_BODY = (77 << 24) | 0x818181;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_GRAY = 0xFFBBBBBB;
    public static final int TEXT_DARK = 0xFF555555;
    public static final int TEXT_GREEN = 0xFF55FF55;
    public static final int TEXT_GOLD = 0xFFFFAA00;
    public static final int TEXT_AQUA = 0xFF55FFFF;
    public static final int TEXT_RED = 0xFFFF5555;
    public static final int TEXT_YELLOW = 0xFFFFFF55;
    public static final int TEXT_TURQUOISE = 0xFF3FE0D0;
    public static final int TEXT_PURPLE = 0xFFAA00AA;
    public static final int TEXT_LIGHT_PURPLE = 0xFFFF55FF;

    // a thin strip on the edge the tab opens towards, instead of a ring around the whole thing
    private static final int ACCENT = 0xFF3FE0D0;

    /** Tab above the window: 28 wide, the selected one is lit and reaches into the frame. */
    public static void tab(GuiGraphicsExtractor ctx, int x, int y, boolean selected) {
        int h = selected ? 32 : 31;
        ctx.fill(x, y, x + 28, y + h, OUTLINE);
        ctx.fill(x + 1, y + 1, x + 27, y + h, FRAME);
        ctx.fill(x + 3, y + 3, x + 25, y + h, selected ? BODY_LIGHT : BODY);
        if (selected) {
            ctx.fill(x + 3, y + 1, x + 25, y + 3, ACCENT);
        }
    }

    /** Recent player tab on the right edge. */
    public static void sideTab(GuiGraphicsExtractor ctx, int x, int y, boolean selected) {
        ctx.fill(x, y, x + 29, y + 28, OUTLINE);
        ctx.fill(x, y + 1, x + 28, y + 27, FRAME);
        ctx.fill(x, y + 3, x + 26, y + 25, selected ? BODY_LIGHT : BODY);
        if (selected) {
            ctx.fill(x + 26, y + 3, x + 28, y + 25, ACCENT);
        }
    }

    /** The window itself: black hairline, 2px grey frame, translucent body. */
    public static void window(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, OUTLINE);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME);
        ctx.fill(x + 3, y + 3, x + w - 3, y + h - 3, BODY);
    }

    /** An inner box, edge stepping inwards like NEU's textures do. */
    public static void box(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        for (int i = 0; i < BOX_EDGE.length; i++) {
            ctx.fill(x + i, y + i, x + w - i, y + h - i, BOX_EDGE[i]);
        }
        int inset = BOX_EDGE.length;
        ctx.fill(x + inset, y + inset, x + w - inset, y + h - inset, BOX_BODY);
    }

    /** Slim box for rows and small cells. */
    public static void slimBox(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, BOX_EDGE[0]);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, BOX_EDGE[3]);
        ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, BOX_BODY);
    }

    // vanilla xp bar colors, which is what NEU renders for every skill
    private static final int BAR_EMPTY = 0xFF1E1E1E;
    private static final int BAR_FILL = 0xFF80FF20;
    private static final int BAR_FILL_TOP = 0xFFB4FF6E;
    private static final int BAR_MAX = 0xFFFFD24A;
    private static final int BAR_MAX_TOP = 0xFFFFF0A0;

    /** NEU draws skill progress with the vanilla xp bar; same proportions and colors. */
    public static void bar(GuiGraphicsExtractor ctx, int x, int y, int w, double progress) {
        boolean maxed = progress >= 1;
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, progress)));
        ctx.fill(x, y, x + w, y + 5, 0xFF000000);
        ctx.fill(x, y + 1, x + w, y + 4, BAR_EMPTY);
        if (filled > 0) {
            ctx.fill(x, y + 1, x + filled, y + 4, maxed ? BAR_MAX : BAR_FILL);
            ctx.fill(x, y + 1, x + filled, y + 2, maxed ? BAR_MAX_TOP : BAR_FILL_TOP);
        }
    }

    /** Kept for the coloured bars on the dungeon page. */
    public static void bar(GuiGraphicsExtractor ctx, int x, int y, int w, double progress, int color) {
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, progress)));
        ctx.fill(x, y, x + w, y + 5, 0xFF000000);
        ctx.fill(x, y + 1, x + w, y + 4, BAR_EMPTY);
        if (filled > 0) {
            ctx.fill(x, y + 1, x + filled, y + 4, color);
            ctx.fill(x, y + 1, x + filled, y + 2, lighten(color));
        }
    }

    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
