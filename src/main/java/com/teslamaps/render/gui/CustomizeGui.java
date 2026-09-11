package com.teslamaps.render.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** Small drawing helpers the customize screen shares. */
public final class CustomizeGui {
    public static final Identifier INNER_SPACE = Identifier.fromNamespaceAndPath("teslamaps", "menu_inner_space");

    private CustomizeGui() {}

    public static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * Vanilla only has a vertical gradient, so the quad is drawn rotated a quarter turn:
     * with a -90 degree rotation a local point (a, b) lands at (b, -a), which turns the
     * local y axis (the one fillGradient runs along) into screen x.
     */
    public static void horizontalGradient(GuiGraphicsExtractor graphics, float startX, float startY, float endX, float endY, int colorStart, int colorEnd) {
        int x0 = (int) startX;
        int y0 = (int) startY;
        int w = (int) endX - x0;
        int h = (int) endY - y0;
        if (w <= 0 || h <= 0) return;

        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();
        matrices.translate(x0, y0);
        matrices.rotate((float) -(Math.PI / 2));
        graphics.fillGradient(-h, 0, 0, w, colorStart, colorEnd);
        matrices.popMatrix();
    }
}
