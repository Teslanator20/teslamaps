package com.teslamaps.render.gui;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The look every HUD overlay panel is drawn in. One switch here restyles all of
 * them, so an overlay only ever asks for "a panel this big" and never picks a
 * colour or a corner radius itself.
 */
public class HudStyle {

    public static final String MODERN = "Modern";
    public static final String VANILLA = "Vanilla";
    public static final String TOOLTIP = "Tooltip";
    public static final String GLASS = "Glass";

    public static final String[] NAMES = {MODERN, VANILLA, TOOLTIP, GLASS};

    public static String current() {
        String style = TeslaMapsConfig.get().hudStyle;
        for (String name : NAMES) if (name.equals(style)) return name;
        return MODERN;
    }

    /** Gap a panel leaves around the text it sits behind. */
    public static int padding() {
        return switch (current()) {
            case VANILLA -> 0;
            case TOOLTIP -> 4;
            default -> 5;
        };
    }

    public static void panel(GuiGraphicsExtractor ctx, float x, float y, float width, float height) {
        panel(ctx, x, y, width, height, 5);
    }

    public static void panel(GuiGraphicsExtractor ctx, float x, float y, float width, float height, float radius) {
        if (width <= 0 || height <= 0) return;
        switch (current()) {
            case VANILLA -> { }   // SkyHanni draws its trackers on nothing at all
            case TOOLTIP -> tooltip(ctx, x, y, width, height);
            case GLASS -> TeslaGui.glassPanel(ctx, x, y, width, height, radius, 20, 45, 70, 16);
            default -> TeslaGui.hudPanel(ctx, x, y, width, height, radius);
        }
    }

    /** Vanilla item-tooltip frame: near-opaque fill, purple gradient hairline. */
    private static void tooltip(GuiGraphicsExtractor ctx, float x, float y, float width, float height) {
        final int fill = 0xF0100010;
        final int borderTop = 0xFF5000FF;
        final int borderBottom = 0xFF28007F;

        TeslaGui.roundedRect(ctx, x, y, width, height, 0, fill);
        TeslaGui.roundedRect(ctx, x + 1, y + 1, width - 2, 1, 0, borderTop);
        TeslaGui.roundedRect(ctx, x + 1, y + height - 2, width - 2, 1, 0, borderBottom);
        TeslaGui.roundedRectGradient(ctx, x + 1, y + 1, 1, height - 2, 0, borderTop, borderBottom);
        TeslaGui.roundedRectGradient(ctx, x + width - 2, y + 1, 1, height - 2, 0, borderTop, borderBottom);
    }
}
