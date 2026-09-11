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

    /** NEU draws skill progress with the vanilla xp bar; same proportions, flat colors. */
    public static void bar(GuiGraphicsExtractor ctx, int x, int y, int w, double progress, int color) {
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, progress)));
        ctx.fill(x, y, x + w, y + 5, 0xFF1E1E1E);
        ctx.fill(x, y, x + w, y + 1, 0xFF000000);
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
