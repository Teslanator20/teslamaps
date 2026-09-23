package com.teslamaps.render.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small padlock in the top-left corner of a 16x16 slot. */
public final class LockIcon {

    private LockIcon() {}

    public static void draw(GuiGraphicsExtractor ctx, int x, int y) {
        draw(ctx, x, y, 0xFFFFAA00);
    }

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, int color) {
        int shade = (color & 0xFF000000) | ((color >> 1) & 0x7F7F7F);
        ctx.fill(x + 1, y + 1, x + 6, y + 2, shade);
        ctx.fill(x + 1, y + 2, x + 2, y + 4, shade);
        ctx.fill(x + 5, y + 2, x + 6, y + 4, shade);
        ctx.fill(x, y + 4, x + 7, y + 9, color);
        ctx.fill(x + 3, y + 5, x + 4, y + 8, shade);
    }
}
