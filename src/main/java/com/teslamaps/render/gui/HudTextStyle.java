package com.teslamaps.render.gui;

// Outline idea from SkyblockAddons' DrawUtils, align/backdrop from Devonian's StylizedTextHud
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Text look of the HUD panel being drawn: shadow / outline / none, line alignment and a
 * backdrop per line. Hooks the one text funnel of GuiGraphicsExtractor while a registry HUD draws.
 * Aligned or backdropped panels buffer their text and replay it at the end of the panel.
 */
public final class HudTextStyle {

    public static final String[] SHADOWS = {"Shadow", "Outline", "None"};
    public static final String[] ALIGNS = {"Left", "Center", "Right"};
    public static final String[] BACKDROPS = {"Off", "Line"};

    private HudTextStyle() {}

    private static int shadowMode, align;
    private static boolean backdrop, active, replaying;

    private static final class Entry {
        Font font;
        FormattedCharSequence seq;
        int x, y, color;
        boolean shadow;
        final Matrix3x2f m = new Matrix3x2f();
        float x0, x1, ys, h;
        int line;
    }

    private static final List<Entry> POOL = new ArrayList<>();
    private static int used;
    private static float[] lineMin = new float[16], lineMax = new float[16], lineY = new float[16], lineH = new float[16];

    private static int index(String[] names, String v) {
        if (v == null) return 0;
        for (int i = 0; i < names.length; i++) if (names[i].equals(v)) return i;
        return 0;
    }

    private static String own(Map<String, String> m, String id, String fallback) {
        if (m == null || m.isEmpty() || id == null) return fallback;
        String v = m.get(id);
        return v != null ? v : fallback;
    }

    public static void begin(String id) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        shadowMode = index(SHADOWS, own(c.hudPanelTextShadow, id, c.hudTextShadow));
        align = index(ALIGNS, own(c.hudPanelTextAlign, id, "Left"));
        backdrop = index(BACKDROPS, own(c.hudPanelLineBackdrop, id, "Off")) == 1;
        active = shadowMode != 0 || align != 0 || backdrop;
        used = 0;
    }

    /** True = handled here, skip the vanilla draw. */
    public static boolean onText(GuiGraphicsExtractor g, Font font, FormattedCharSequence seq, int x, int y, int color, boolean shadow) {
        if (!active || replaying) return false;
        if (align != 0 || backdrop) {
            if (used == POOL.size()) POOL.add(new Entry());
            Entry e = POOL.get(used++);
            e.font = font;
            e.seq = seq;
            e.x = x;
            e.y = y;
            e.color = color;
            e.shadow = shadow;
            e.m.set(g.pose());
            return true;
        }
        if (!shadow || shadowMode == 0) return false;
        draw(g, font, seq, x, y, color, true);
        return true;
    }

    private static void draw(GuiGraphicsExtractor g, Font font, FormattedCharSequence seq, int x, int y, int color, boolean shadow) {
        replaying = true;
        try {
            if (shadow && shadowMode == 1) {
                FormattedCharSequence black = sink -> seq.accept((i, st, cp) -> sink.accept(i, Style.EMPTY.withBold(st.isBold()), cp));
                int bc = color & 0xFF000000;
                g.text(font, black, x + 1, y, bc, false);
                g.text(font, black, x - 1, y, bc, false);
                g.text(font, black, x, y + 1, bc, false);
                g.text(font, black, x, y - 1, bc, false);
                g.text(font, seq, x, y, color, false);
            } else {
                g.text(font, seq, x, y, color, shadow && shadowMode == 0);
            }
        } finally {
            replaying = false;
        }
    }

    public static void end(GuiGraphicsExtractor g) {
        int n = used;
        used = 0;
        boolean was = active;
        active = false;
        if (!was || n == 0) return;
        if (lineMin.length < n) {
            lineMin = new float[n];
            lineMax = new float[n];
            lineY = new float[n];
            lineH = new float[n];
        }
        int lines = 0;
        float blockMin = Float.MAX_VALUE, blockMax = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Entry e = POOL.get(i);
            Matrix3x2f m = e.m;
            e.x0 = m.m00 * e.x + m.m10 * e.y + m.m20;
            e.ys = m.m01 * e.x + m.m11 * e.y + m.m21;
            e.x1 = e.x0 + e.font.width(e.seq) * m.m00;
            e.h = e.font.lineHeight * m.m11;
            blockMin = Math.min(blockMin, e.x0);
            blockMax = Math.max(blockMax, e.x1);
            int line = -1;
            for (int l = 0; l < lines; l++) if (Math.abs(lineY[l] - e.ys) < 0.5f) { line = l; break; }
            if (line < 0) {
                line = lines++;
                lineY[line] = e.ys;
                lineH[line] = e.h;
                lineMin[line] = e.x0;
                lineMax[line] = e.x1;
            } else {
                lineMin[line] = Math.min(lineMin[line], e.x0);
                lineMax[line] = Math.max(lineMax[line], e.x1);
                lineH[line] = Math.max(lineH[line], e.h);
            }
            e.line = line;
        }
        // lineMin now doubles as the shift of each line
        for (int l = 0; l < lines; l++) {
            float shift = switch (align) {
                case 1 -> (blockMin + blockMax) / 2f - (lineMin[l] + lineMax[l]) / 2f;
                case 2 -> blockMax - lineMax[l];
                default -> 0f;
            };
            if (backdrop) {
                g.pose().pushMatrix();
                g.pose().identity();
                g.fill(Math.round(lineMin[l] + shift) - 1, Math.round(lineY[l]) - 1,
                        Math.round(lineMax[l] + shift) + 1, Math.round(lineY[l] + lineH[l]), 0x80000000);
                g.pose().popMatrix();
            }
            lineMin[l] = shift;
        }
        for (int i = 0; i < n; i++) {
            Entry e = POOL.get(i);
            float shift = lineMin[e.line];
            g.pose().pushMatrix();
            g.pose().set(e.m);
            if (shift != 0 && e.m.m00 != 0) g.pose().translate(shift / e.m.m00, 0);
            draw(g, e.font, e.seq, e.x, e.y, e.color, e.shadow);
            g.pose().popMatrix();
            e.font = null;
            e.seq = null;
        }
    }
}
