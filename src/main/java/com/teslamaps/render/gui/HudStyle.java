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
    public static final String CUSTOM = "Custom";

    public static final String[] NAMES = {MODERN, GLASS, VANILLA, TOOLTIP, CUSTOM};
    public static final String[] BORDER_MODES = {"Solid", "Chroma", "Fade"};

    /** HUD id being drawn right now, set by the registry, for per-panel styles. */
    private static String panelId = null;

    public static void beginPanel(String id) { panelId = id; }
    public static void endPanel() { panelId = null; }

    public static String current() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String style = c.hudStyle;
        if (panelId != null && c.hudPanelStyles != null && !c.hudPanelStyles.isEmpty()) {
            String own = c.hudPanelStyles.get(panelId);
            if (own != null) style = own;
        }
        for (String name : NAMES) if (name.equals(style)) return name;
        return MODERN;
    }

    /**
     * Text colours for a panel. Vanilla draws no background at all, so the grey
     * hierarchy disappears on a bright sky — there the text goes white like the
     * trackers that never had a panel.
     */
    public static int label() {
        return VANILLA.equals(current())
                ? com.teslamaps.screen.AppleColors.TEXT_PRIMARY
                : com.teslamaps.screen.AppleColors.TEXT_SECONDARY;
    }

    public static int dim() {
        return VANILLA.equals(current())
                ? 0xFFCCCCCC
                : com.teslamaps.screen.AppleColors.TEXT_TERTIARY;
    }

    /** Gap a panel leaves around the text it sits behind. */
    public static int padding() {
        return switch (current()) {
            case VANILLA -> 0;
            case TOOLTIP -> 4;
            case CUSTOM -> Math.round(TeslaMapsConfig.get().hudCustomPadding);
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
            case CUSTOM -> custom(ctx, x, y, width, height);
            default -> TeslaGui.hudPanel(ctx, x, y, width, height, radius);
        }
    }

    /** User-set fill, border (solid / chroma / fade), radius and border width. */
    private static void custom(GuiGraphicsExtractor ctx, float x, float y, float width, float height) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        float r = Math.max(0, Math.min(c.hudCustomRadius, Math.min(width, height) / 2f));
        TeslaGui.roundedRect(ctx, x, y, width, height, r, TeslaMapsConfig.parseColor(c.hudCustomBg));
        float bw = c.hudCustomBorderWidth;
        if (bw <= 0) return;
        int top, bottom;
        switch (c.hudCustomBorderMode == null ? "Solid" : c.hudCustomBorderMode) {
            case "Chroma" -> {
                float h = (System.currentTimeMillis() % 4000L) / 4000f;
                top = 0xFF000000 | java.awt.Color.HSBtoRGB(h, 0.8f, 1f);
                bottom = 0xFF000000 | java.awt.Color.HSBtoRGB((h + 0.5f) % 1f, 0.8f, 1f);
            }
            case "Fade" -> {
                int a = TeslaMapsConfig.parseColor(c.hudCustomBorder), b = TeslaMapsConfig.parseColor(c.hudCustomBorderFade);
                double t = (System.currentTimeMillis() % 2500L) / 2500.0 * Math.PI * 2;
                top = blend(a, b, (float) ((Math.sin(t) + 1) / 2));
                bottom = blend(a, b, (float) ((Math.sin(t + Math.PI) + 1) / 2));
            }
            default -> top = bottom = TeslaMapsConfig.parseColor(c.hudCustomBorder);
        }
        TeslaGui.roundedOutlineGradient(ctx, x + bw / 2f, y + bw / 2f, width - bw, height - bw, r, bw, top, bottom);
    }

    private static int blend(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    /**
     * Same switch for the mod's own screens. Differs from {@link #panel} in one point: a screen
     * panel can never draw nothing, text floating on the dimmed world is unreadable, so Vanilla
     * gets the flat grey box the game uses for its own menus.
     */
    public static void screenPanel(GuiGraphicsExtractor ctx, float x, float y, float width, float height, float radius) {
        if (width <= 0 || height <= 0) return;
        switch (current()) {
            case VANILLA -> {
                TeslaGui.roundedRect(ctx, x, y, width, height, 0, 0xF0101010);
                TeslaGui.roundedRect(ctx, x, y, width, 1, 0, 0xFF5A5A5A);
                TeslaGui.roundedRect(ctx, x, y + height - 1, width, 1, 0, 0xFF5A5A5A);
                TeslaGui.roundedRect(ctx, x, y, 1, height, 0, 0xFF5A5A5A);
                TeslaGui.roundedRect(ctx, x + width - 1, y, 1, height, 0, 0xFF5A5A5A);
            }
            case TOOLTIP -> tooltip(ctx, x, y, width, height);
            case GLASS -> TeslaGui.glassPanel(ctx, x, y, width, height, radius, 26, 45, 70, 16);
            case CUSTOM -> custom(ctx, x, y, width, height);
            default -> TeslaGui.hudPanel(ctx, x, y, width, height, radius);
        }
    }

    /** Buttons and input fields inside those screens, so they match the panel they sit on. */
    public static void screenButton(GuiGraphicsExtractor ctx, float x, float y, float width, float height,
                                    boolean hovered, boolean enabled) {
        if (width <= 0 || height <= 0) return;
        switch (current()) {
            case VANILLA -> {
                int fill = !enabled ? 0xFF3F3F3F : (hovered ? 0xFF8B8B8B : 0xFF6C6C6C);
                TeslaGui.roundedRect(ctx, x, y, width, height, 0, fill);
                TeslaGui.roundedRect(ctx, x, y, width, 1, 0, 0xFFFFFFFF & 0x40FFFFFF);
                TeslaGui.roundedRect(ctx, x, y + height - 1, width, 1, 0, 0xFF2B2B2B);
            }
            case TOOLTIP -> {
                TeslaGui.roundedRect(ctx, x, y, width, height, 0, hovered ? 0xF0201028 : 0xF0100010);
                TeslaGui.roundedRect(ctx, x, y, width, 1, 0, 0xFF5000FF);
                TeslaGui.roundedRect(ctx, x, y + height - 1, width, 1, 0, 0xFF28007F);
            }
            default -> TeslaGui.glassButton(ctx, x, y, width, height, hovered, enabled);
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
