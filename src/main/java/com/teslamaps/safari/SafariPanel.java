package com.teslamaps.safari;

// Ported from critterMod's HudPanel.java
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Stacked rows with measured columns: labels left, values right-aligned, optional bar between. */
public final class SafariPanel {

    private static final int LINE_HEIGHT = 11;
    private static final int PADDING = 5;
    private static final int GUTTER = 6;
    private static final int BAR_WIDTH = 34;
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_TRACK = 0x50FFFFFF;

    private enum Kind { TITLE, TEXT, PAIR, BAR, BLANK }

    private record Row(Kind kind, String label, String value, int labelColor, int valueColor, int current, int max) {}

    private final List<Row> rows = new ArrayList<>();
    private int cachedWidth = -1;
    private int measuredRows = -1, cachedValueWidth;
    private int[] valueWidths = new int[0];
    private Font measuredFont;

    private void measure(Font font) {
        if (measuredRows == rows.size() && measuredFont == font) return;
        measuredRows = rows.size();
        measuredFont = font;
        valueWidths = new int[rows.size()];
        for (int i = 0; i < valueWidths.length; i++) {
            String v = rows.get(i).value;
            valueWidths[i] = v == null ? 0 : font.width(v);
        }
        cachedValueWidth = valueWidth(font);
        cachedWidth = contentWidth(font) + PADDING * 2;
    }

    public SafariPanel title(String text, int color) {
        rows.add(new Row(Kind.TITLE, text, null, color, 0, 0, 0));
        return this;
    }

    public SafariPanel line(String text, int color) {
        rows.add(new Row(Kind.TEXT, text, null, color, 0, 0, 0));
        return this;
    }

    public SafariPanel pair(String label, String value, int labelColor, int valueColor) {
        rows.add(new Row(Kind.PAIR, label, value, labelColor, valueColor, 0, 0));
        return this;
    }

    public SafariPanel bar(String label, int current, int max, int labelColor, int barColor) {
        rows.add(new Row(Kind.BAR, label, current + "/" + max, labelColor, barColor, current, max));
        return this;
    }

    public SafariPanel blank() {
        rows.add(new Row(Kind.BLANK, "", null, 0, 0, 0, 0));
        return this;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public int width(Font font) {
        measure(font);
        return cachedWidth;
    }

    public int height() {
        return rows.size() * LINE_HEIGHT + PADDING * 2;
    }

    private int labelWidth(Font font) {
        int w = 0;
        for (Row r : rows) if (r.kind == Kind.PAIR || r.kind == Kind.BAR) w = Math.max(w, font.width(r.label));
        return w;
    }

    private int valueWidth(Font font) {
        int w = 0;
        for (Row r : rows) if (r.value != null) w = Math.max(w, font.width(r.value));
        return w;
    }

    private boolean anyBar() {
        for (Row r : rows) if (r.kind == Kind.BAR) return true;
        return false;
    }

    private int contentWidth(Font font) {
        int valueWidth = valueWidth(font);
        int w = labelWidth(font) + (valueWidth > 0 ? GUTTER + valueWidth : 0) + (anyBar() ? GUTTER + BAR_WIDTH : 0);
        for (Row r : rows) {
            if (r.kind == Kind.TITLE || r.kind == Kind.TEXT) w = Math.max(w, font.width(r.label));
        }
        return w;
    }

    public void draw(GuiGraphicsExtractor g, Font font, int x, int y, float scale) {
        if (rows.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        int panelWidth = width(font);
        HudStyle.panel(g, 0, 0, panelWidth, height());

        int valueRight = panelWidth - PADDING;
        int barLeft = valueRight - cachedValueWidth - GUTTER - BAR_WIDTH;
        int rowY = PADDING;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            switch (r.kind) {
                case BLANK -> { }
                case TITLE, TEXT -> g.text(font, r.label, PADDING, rowY, r.labelColor, true);
                case PAIR -> {
                    g.text(font, r.label, PADDING, rowY, r.labelColor, true);
                    g.text(font, r.value, valueRight - valueWidths[i], rowY, r.valueColor, true);
                }
                case BAR -> {
                    g.text(font, r.label, PADDING, rowY, r.labelColor, true);
                    int barY = rowY + 2;
                    g.fill(barLeft, barY, barLeft + BAR_WIDTH, barY + BAR_HEIGHT, BAR_TRACK);
                    if (r.max > 0 && r.current > 0) {
                        int filled = Math.max(1, BAR_WIDTH * Math.min(r.current, r.max) / r.max);
                        g.fill(barLeft, barY, barLeft + filled, barY + BAR_HEIGHT, r.valueColor);
                    }
                    g.text(font, r.value, valueRight - valueWidths[i], rowY, r.valueColor, true);
                }
            }
            rowY += LINE_HEIGHT;
        }
        g.pose().popMatrix();
    }
}
