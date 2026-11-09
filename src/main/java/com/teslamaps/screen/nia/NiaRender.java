package com.teslamaps.screen.nia;

import com.teslamaps.render.gui.CustomizeGui;
import com.teslamaps.render.gui.SdfRectRenderState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.List;

// Ported from NiaMod's render/Render2D.java and gui/GuiStyle.java
final class NiaRender {

    static final Style ADWAITA = Style.EMPTY.withFont(new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("teslamaps", "nia/adwaita_sans")));
    static Style style = ADWAITA;

    private static final ArrayDeque<ScreenRectangle> CLIPS = new ArrayDeque<>();

    private NiaRender() {}

    static FormattedCharSequence seq(String text) {
        return FormattedCharSequence.forward(text, style);
    }

    static Component styled(String text) {
        return Component.literal(text).withStyle(style);
    }

    static int width(Font font, String text) {
        return font.width(seq(text));
    }

    static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        g.text(font, seq(text), x, y, color, false);
    }

    static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color) {
        g.text(font, text, x, y, color, false);
    }

    static List<FormattedCharSequence> wrap(Font font, String text, int maxWidth) {
        if (text == null || text.isBlank()) return List.of();
        return font.split(styled(text), Math.max(1, maxWidth));
    }

    static String ellipsize(Font font, String text, int maxWidth) {
        if (width(font, text) <= maxWidth) return text;
        String s = text;
        while (!s.isEmpty() && width(font, s + "...") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    static void resetClips() {
        CLIPS.clear();
    }

    static void pushClip(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.enableScissor(x0, y0, x1, y1);
        ScreenRectangle r = new ScreenRectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0))
                .transformAxisAligned(g.pose());
        ScreenRectangle top = CLIPS.peek();
        if (top != null) {
            ScreenRectangle i = top.intersection(r);
            r = i == null ? new ScreenRectangle(r.left(), r.top(), 0, 0) : i;
        }
        CLIPS.push(r);
    }

    static void popClip(GuiGraphicsExtractor g) {
        g.disableScissor();
        CLIPS.poll();
    }

    static void roundedRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        ScreenRectangle clip = CLIPS.peek();
        if (clip != null) {
            ScreenRectangle r = new ScreenRectangle((int) Math.floor(x) - 1, (int) Math.floor(y) - 1,
                    (int) Math.ceil(w) + 2, (int) Math.ceil(h) + 2).transformMaxBounds(g.pose());
            if (!clip.intersects(r)) return;
        }
        g.guiRenderState.addGuiElement(new SdfRectRenderState(NiaPipelines.ROUNDED_RECT, g.pose(),
                x, y, w, h, Math.max(0, radius), 0f, 1f, color, color, CLIPS.peek()));
    }

    static void surface(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int fill, int border) {
        if (w <= 0 || h <= 0) return;
        if ((border >>> 24) > 0) {
            roundedRect(g, x, y, w, h, radius, border);
            if (w <= 2 || h <= 2) return;
            roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
            return;
        }
        roundedRect(g, x, y, w, h, radius, fill);
    }

    static void circle(GuiGraphicsExtractor g, float cx, float cy, float diameter, int color) {
        float r = diameter / 2f;
        roundedRect(g, cx - r, cy - r, diameter, diameter, r, color);
    }

    static void dropShadow(GuiGraphicsExtractor g, int x, int y, int w, int h, int layers, int baseColor, int radius) {
        int baseAlpha = (baseColor >>> 24) & 0xFF;
        for (int i = layers; i >= 1; i--) {
            float strength = (layers - i + 1) / (float) (layers + 2);
            int alpha = Math.round(baseAlpha * strength);
            roundedRect(g, x - i, y - i, w + i * 2, h + i * 2, radius + i, (alpha << 24) | (baseColor & 0x00FFFFFF));
        }
    }

    static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static void horizontalGradient(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int left, int right) {
        CustomizeGui.horizontalGradient(g, x0, y0, x1, y1, left, right);
    }

    static int withAlpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    static int lerpColor(int a, int b, float t) {
        int aA = (a >>> 24) & 0xFF, aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
        return (Math.round(aA + (bA - aA) * t) << 24)
                | (Math.round(aR + (bR - aR) * t) << 16)
                | (Math.round(aG + (bG - aG) * t) << 8)
                | Math.round(aB + (bB - aB) * t);
    }

    static int hsvToRgb(float h, float s, float v) {
        return java.awt.Color.HSBtoRGB(h / 360.0f, s, v) & 0xFFFFFF;
    }

    static float[] rgbToHsv(int rgb) {
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        return new float[]{hsb[0] * 360.0f, hsb[1], hsb[2]};
    }
}
