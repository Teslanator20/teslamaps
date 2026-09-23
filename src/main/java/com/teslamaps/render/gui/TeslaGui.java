package com.teslamaps.render.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rounded rects, outlines, shadows and gradients for GUI code. Each call queues
 * one quad into the vanilla gui render state, so they batch with each other and
 * respect the surrounding pose/stratum.
 */
public class TeslaGui {

    private static final float EDGE_PAD = 1.0f;

    public static void roundedRect(GuiGraphicsExtractor ctx, float x, float y,
                                   float width, float height, float radius, int argb) {
        roundedRectGradient(ctx, x, y, width, height, radius, argb, argb);
    }

    public static void roundedRectGradient(GuiGraphicsExtractor ctx, float x, float y,
                                            float width, float height, float radius,
                                            int topArgb, int bottomArgb) {
        submit(ctx, new SdfRectRenderState(TeslaGuiPipelines.SDF_FILL, ctx.pose(),
                x, y, width, height, radius, 0.0f, EDGE_PAD, topArgb, bottomArgb, null));
    }

    public static void roundedOutline(GuiGraphicsExtractor ctx, float x, float y,
                                       float width, float height, float radius,
                                       float thickness, int argb) {
        submit(ctx, new SdfRectRenderState(TeslaGuiPipelines.SDF_OUTLINE, ctx.pose(),
                x, y, width, height, radius, thickness, EDGE_PAD, argb, argb, null));
    }

    public static void roundedOutlineGradient(GuiGraphicsExtractor ctx, float x, float y,
                                              float width, float height, float radius,
                                              float thickness, int topArgb, int bottomArgb) {
        submit(ctx, new SdfRectRenderState(TeslaGuiPipelines.SDF_OUTLINE, ctx.pose(),
                x, y, width, height, radius, thickness, EDGE_PAD, topArgb, bottomArgb, null));
    }

    /** Outer glow / drop shadow. {@code blur} is the falloff distance in gui units. */
    public static void shadow(GuiGraphicsExtractor ctx, float x, float y,
                               float width, float height, float radius,
                               float blur, int argb) {
        submit(ctx, new SdfRectRenderState(TeslaGuiPipelines.SDF_SHADOW, ctx.pose(),
                x, y, width, height, radius, blur, blur + EDGE_PAD, argb, argb, null));
    }

    private static void submit(GuiGraphicsExtractor ctx, SdfRectRenderState state) {
        ctx.guiRenderState.addGuiElement(state);
    }

    // -------------------------------------------------------------------------
    // Composed shapes
    // -------------------------------------------------------------------------

    private static final float BUTTON_RADIUS = 4.0f;
    private static final float BUTTON_BORDER = 1.0f;
    private static final float BUTTON_SHADOW_OFFSET_Y = 2.0f;
    private static final float BUTTON_SHADOW_BLUR = 6.0f;
    private static final float HIGHLIGHT_FRACTION = 0.35f;
    private static final float HIGHLIGHT_MIN = 6.0f;

    /**
     * Translucent glass panel: drop shadow, dark base, white veil, top highlight
     * gradient and a hairline outline. The alphas are deliberately tiny — this
     * only reads as glass over a moving background.
     */
    public static void glassPanel(GuiGraphicsExtractor ctx, float x, float y,
                                   float width, float height, float radius,
                                   int veilAlpha, int borderAlpha,
                                   int shadowAlpha, int highlightAlpha) {
        if (shadowAlpha > 0) {
            shadow(ctx, x, y + BUTTON_SHADOW_OFFSET_Y, width, height, radius,
                    BUTTON_SHADOW_BLUR, withAlpha(0x000000, shadowAlpha));
        }

        roundedRect(ctx, x, y, width, height, radius, withAlpha(0x141A26, 28));
        roundedRect(ctx, x, y, width, height, radius, withAlpha(0xFFFFFF, veilAlpha));

        if (highlightAlpha > 0) {
            float highlightHeight = Math.max(HIGHLIGHT_MIN, height * HIGHLIGHT_FRACTION);
            roundedRectGradient(ctx, x + BUTTON_BORDER, y + BUTTON_BORDER,
                    width - BUTTON_BORDER * 2.0f, highlightHeight,
                    Math.max(radius - BUTTON_BORDER, 0.0f),
                    withAlpha(0xFFFFFF, highlightAlpha), withAlpha(0xFFFFFF, 0));
        }

        if (borderAlpha > 0) {
            roundedOutline(ctx, x + BUTTON_BORDER * 0.5f, y + BUTTON_BORDER * 0.5f,
                    width - BUTTON_BORDER, height - BUTTON_BORDER, radius,
                    BUTTON_BORDER, withAlpha(0xFFFFFF, borderAlpha));
        }
    }

    /** Glass panel with the button alpha ramp applied for the given state. */
    public static void glassButton(GuiGraphicsExtractor ctx, float x, float y,
                                    float width, float height,
                                    boolean hovered, boolean enabled) {
        int veil = enabled ? (hovered ? 23 : 15) : 10;
        glassPanel(ctx, x, y, width, height, BUTTON_RADIUS, veil,
                enabled ? 30 : 18, enabled ? 14 : 9, enabled ? 10 : 8);
    }

    /**
     * Opaque panel for HUD overlays: soft shadow, dark card, top highlight and a
     * hairline outline. Use this for every new overlay so they share one look.
     */
    public static void hudPanel(GuiGraphicsExtractor ctx, float x, float y,
                                float width, float height, float radius) {
        // Kept tight and faint: enough to lift the panel off the world, not a halo.
        shadow(ctx, x, y + 1, width, height, radius, 4, withAlpha(0x000000, 55));
        roundedRect(ctx, x, y, width, height, radius, withAlpha(0x1C1C1E, 232));
        roundedRectGradient(ctx, x + 1, y + 1, width - 2, Math.min(height * 0.5f, 14), radius - 1,
                withAlpha(0xFFFFFF, 16), withAlpha(0xFFFFFF, 0));
        roundedOutline(ctx, x + 0.5f, y + 0.5f, width - 1, height - 1, radius, 1.0f,
                withAlpha(0xFFFFFF, 40));
    }

    /** Padding a backdrop leaves around the content it sits behind. */
    public static final int BACKDROP_PADDING = 5;

    /** What {@link #backdrop} actually leaves free — the active style decides. */
    public static int backdropPadding() {
        return HudStyle.padding();
    }

    /**
     * Panel behind an overlay that was written as plain text.
     * <p>
     * Takes the content's own bounds and grows outwards, so the text keeps the
     * exact position it had before — the anchor the player dragged it to still
     * points at the same pixel. HUD Style "Vanilla" draws nothing here.
     */
    public static void backdrop(GuiGraphicsExtractor ctx, float contentX, float contentY,
                                float contentWidth, float contentHeight) {
        if (contentWidth <= 0 || contentHeight <= 0) return;

        float p = backdropPadding();
        backdropRect(ctx, contentX - p, contentY - p,
                contentWidth + p * 2, contentHeight + p * 2);
    }

    /**
     * Same panel for overlays that already had a background and therefore a box
     * size the HUD editor knows about. Taking the rect as given keeps that size
     * exactly, so the editor preview still matches what is drawn.
     */
    public static void backdropRect(GuiGraphicsExtractor ctx, float x, float y,
                                    float width, float height) {
        if (width <= 0 || height <= 0) return;
        HudStyle.panel(ctx, x, y, width, height, 5);
    }

    /** Horizontal track with a fill. {@code fromLeft} picks the anchored end. */
    public static void progressTrack(GuiGraphicsExtractor ctx, float x, float y,
                                      float width, float height,
                                      float fraction, boolean fromLeft, int fillColor) {
        float radius = height * 0.5f;
        roundedRect(ctx, x, y, width, height, radius, withAlpha(0x000000, 170));
        roundedOutline(ctx, x, y, width, height, radius, 1.0f, withAlpha(0xFFFFFF, 26));

        float fill = Math.clamp(fraction, 0.0f, 1.0f) * width;
        if (fill < height) return;   // below the cap width it would render as a blob
        roundedRect(ctx, fromLeft ? x : x + width - fill, y, fill, height, radius, fillColor);
    }

    /**
     * Check glyph drawn from two rounded bars. The font's own tick is a blocky bitmap that
     * clashes with everything else here, so anything we draw ourselves uses this instead.
     */
    public static void checkIcon(GuiGraphicsExtractor ctx, float x, float y, float size, int argb) {
        float thickness = Math.max(size * 0.16f, 1.2f);
        // two strokes meeting at the elbow (0.42, 0.74): short one down-right, long one up-right
        stroke(ctx, x + size * 0.18f, y + size * 0.50f, size * 0.34f, 45.0f, thickness, argb);
        stroke(ctx, x + size * 0.40f, y + size * 0.74f, size * 0.62f, -52.0f, thickness, argb);
    }

    /** Cross glyph, same weight as {@link #checkIcon} so the two line up in a list. */
    public static void crossIcon(GuiGraphicsExtractor ctx, float x, float y, float size, int argb) {
        float thickness = Math.max(size * 0.16f, 1.2f);
        float len = size * 0.85f;
        float inset = (size - len * 0.7071f) * 0.5f;
        stroke(ctx, x + inset, y + inset, len, 45.0f, thickness, argb);
        stroke(ctx, x + inset, y + size - inset, len, -45.0f, thickness, argb);
    }

    /** One rounded bar of {@code len}, starting at (x,y) and running at {@code degrees}. */
    private static void stroke(GuiGraphicsExtractor ctx, float x, float y,
                               float len, float degrees, float thickness, int argb) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().rotate((float) Math.toRadians(degrees));
        roundedRect(ctx, 0, -thickness * 0.5f, len, thickness, thickness * 0.5f, argb);
        ctx.pose().popMatrix();
    }

    /** Gear glyph drawn from shapes — a ring plus six teeth rotated around the centre. */
    public static void gearIcon(GuiGraphicsExtractor ctx, float centerX, float centerY,
                                 float size, int argb) {
        float radius = size * 0.5f;
        float ringRadius = radius * 0.62f;
        float toothWidth = radius * 0.30f;
        float toothLength = radius * 0.42f;

        for (int i = 0; i < 6; i++) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(centerX, centerY);
            ctx.pose().rotate((float) (i * Math.PI / 3.0));
            roundedRect(ctx, -toothWidth * 0.5f, -ringRadius - toothLength * 0.7f,
                    toothWidth, toothLength, toothWidth * 0.35f, argb);
            ctx.pose().popMatrix();
        }

        roundedOutline(ctx, centerX - ringRadius, centerY - ringRadius,
                ringRadius * 2.0f, ringRadius * 2.0f, ringRadius,
                Math.max(radius * 0.26f, 1.0f), argb);
    }

    /**
     * Eye glyph. A real eye is two arcs meeting at points, which no rounded rect
     * gives you — so each lid is walked as a short polyline of rounded bars, and
     * the iris is a filled disc with a hole punched by the button colour behind it.
     */

    /**
     * Picture frame with a hill - what the button actually opens is a background and skybox
     * picker, including your own images.
     * <p>
     * Deliberately plain: one colour, few shapes. At the ~13px this renders at, a palette ring
     * with dots inside turned into noise and read as an accessibility symbol.
     */
    public static void imageIcon(GuiGraphicsExtractor ctx, float centerX, float centerY,
                                 float size, int argb) {
        float w = size;
        float h = size * 0.80f;
        float x = centerX - w / 2f;
        float y = centerY - h / 2f;
        float border = Math.max(size * 0.09f, 1.0f);

        roundedOutline(ctx, x, y, w, h, Math.max(size * 0.10f, 1.5f), border, argb);

        // hill as a three step staircase - a real triangle disappears at this size
        float stepH = Math.max(h * 0.13f, 1.0f);
        float baseY = y + h - border - stepH;
        for (int i = 0; i < 3; i++) {
            float inset = w * (0.14f + i * 0.13f);
            roundedRect(ctx, x + inset, baseY - i * stepH, w - inset * 2f - w * 0.06f, stepH, 0f, argb);
        }
    }

    public static void eyeIcon(GuiGraphicsExtractor ctx, float centerX, float centerY,
                               float size, int argb) {
        float halfWidth = size * 0.5f;
        float lidHeight = size * 0.30f;
        float thickness = Math.max(size * 0.095f, 1.0f);
        final int segments = 8;

        for (int lid = 0; lid < 2; lid++) {
            float direction = lid == 0 ? -1.0f : 1.0f;   // upper lid, then lower
            float previousX = -halfWidth;
            float previousY = 0.0f;

            for (int i = 1; i <= segments; i++) {
                float progress = i / (float) segments;
                float x = -halfWidth + progress * size;
                float y = direction * lidHeight * (float) Math.sin(progress * Math.PI);

                float dx = x - previousX;
                float dy = y - previousY;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                float degrees = (float) Math.toDegrees(Math.atan2(dy, dx));

                stroke(ctx, centerX + previousX, centerY + previousY, length, degrees, thickness, argb);

                previousX = x;
                previousY = y;
            }
        }

        float iris = size * 0.34f;
        roundedRect(ctx, centerX - iris * 0.5f, centerY - iris * 0.5f, iris, iris, iris * 0.5f, argb);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
