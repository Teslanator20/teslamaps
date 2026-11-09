package com.teslamaps.screen.h2;

import com.teslamaps.render.gui.SdfRectRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;

// Ported from h2team's Ui.java
final class H2Ui {
    static final int BG = 0xFF171A21;
    static final int SIDEBAR = 0xFF11141B;
    static final int CARD = 0xFF1C2029;
    static final int CARD_HOVER = 0xFF222733;
    static final int LINE = 0x14FFFFFF;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT2 = 0xDCFFFFFF;
    static final int MUTED = 0x82FFFFFF;
    static final int DIM = 0x4DFFFFFF;
    static final int ACCENT = 0xFF4794FD;
    static final int GOOD = 0xFF4ADE80;
    static final int WARN = 0xFFFBBF24;
    static final int BAD = 0xFFF87171;

    static final Style TITLE = font("h2_title"), LOGO = font("h2_logo");
    private static final Style SANS = font("h2_sans");

    /** Adwaita Sans instead of the pixel font. */
    static boolean smooth = false;

    private static Style font(String id) {
        return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("teslamaps", id)));
    }

    private static int pixelScale(Style font) {
        return font == LOGO ? 2 : 1;
    }

    private static final Deque<ScreenRectangle> scissors = new ArrayDeque<>();

    private H2Ui() {}

    private static Font mcFont() {
        return Minecraft.getInstance().font;
    }

    // shapes

    static void round(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        ctx.guiRenderState.addGuiElement(new SdfRectRenderState(H2Pipelines.ROUNDED_RECT, ctx.pose(),
                x, y, w, h, Math.min(r, Math.min(w, h) / 2f), 0f, 0f, color, color, scissors.peek()));
    }

    /** Filled rounded rect with a 1 px border. */
    static void surface(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int fill, int border) {
        if ((border >>> 24) != 0) {
            round(ctx, x, y, w, h, r, border);
            round(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
        } else {
            round(ctx, x, y, w, h, r, fill);
        }
    }

    /** Soft drop shadow: a few growing, fading rounded rects behind the shape. */
    static void shadow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int layers, int baseAlpha) {
        for (int i = layers; i >= 1; i--) {
            float strength = (layers - i + 1) / (float) (layers + 2);
            int a = Math.round(baseAlpha * strength * 0.5f);
            round(ctx, x - i, y - i + 1, w + i * 2, h + i * 2, r + i, a << 24);
        }
    }

    static void circle(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        round(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
    }

    // scissor, tracked here so shader rects get clipped too

    static void scissor(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2) {
        ctx.enableScissor(x1, y1, x2, y2);
        ScreenRectangle r = new ScreenRectangle(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1)).transformMaxBounds(ctx.pose());
        ScreenRectangle outer = scissors.peek();
        scissors.push(outer != null ? intersect(outer, r) : r);
    }

    private static ScreenRectangle intersect(ScreenRectangle a, ScreenRectangle b) {
        ScreenRectangle i = a.intersection(b);
        return i != null ? i : new ScreenRectangle(0, 0, 0, 0);
    }

    static void endScissor(GuiGraphicsExtractor ctx) {
        ctx.disableScissor();
        scissors.poll();
    }

    // text

    static net.minecraft.network.chat.MutableComponent styled(String s) {
        return Component.literal(s).setStyle(SANS);
    }

    static int width(String s) {
        return smooth ? mcFont().width(styled(s)) : mcFont().width(s);
    }

    static int width(String s, Style font) {
        if (smooth) return mcFont().width(Component.literal(s).setStyle(font));
        return mcFont().width(s) * pixelScale(font);
    }

    static void text(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        if (smooth) ctx.text(mcFont(), styled(s), x, y, color, false);
        else ctx.text(mcFont(), s, x, y, color, false);
    }

    static void text(GuiGraphicsExtractor ctx, String s, int x, int y, int color, Style font) {
        if (smooth) {
            ctx.text(mcFont(), Component.literal(s).setStyle(font), x, y, color, false);
            return;
        }
        int k = pixelScale(font);
        if (k == 1) {
            ctx.text(mcFont(), s, x, y + (font == TITLE ? 1 : 0), color, false);
            return;
        }
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(k, k);
        ctx.text(mcFont(), s, 0, 0, color, false);
        ctx.pose().popMatrix();
    }

    static void textRight(GuiGraphicsExtractor ctx, String s, int right, int y, int color) {
        text(ctx, s, right - width(s), y, color);
    }

    static void textCenter(GuiGraphicsExtractor ctx, String s, int cx, int y, int color) {
        text(ctx, s, cx - width(s) / 2, y, color);
    }

    /** Cut text with "…" so it ends before max. */
    static String fit(String s, int max) {
        if (max <= 0) return "";
        if (width(s) <= max) return s;
        while (s.length() > 1 && width(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s.trim() + "…";
    }

    // colour / animation

    static int alpha(int argb, float a) {
        int base = argb >>> 24;
        return ((int) (base * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }

    static int withAlpha(int rgb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Frame-rate independent approach of value toward target. */
    static float approach(float value, float target, float speed) {
        return value + (target - value) * Math.min(1f, speed);
    }

    /** Ease out cubic, for open/slide animations. */
    static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
