package com.teslamaps.screen.nia;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.screen.MapConfigScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Ported from NiaMod's gui/component/SectionComponent.java
final class NiaSection {
    private static final int MIN_HEADER_HEIGHT = 38;
    private static final int SETTING_START_X_OFFSET = 6;
    private static final int CONTENT_TOP_PADDING = 6;
    private static final int CONTENT_BOTTOM_PADDING = 6;
    private static final int CHILD_VERTICAL_GAP = 4;
    private static final int HEADER_TEXT_X = 6;
    private static final int HEADER_SWITCH_RIGHT_PADDING = 8;
    private static final int HEADER_SWITCH_TEXT_GAP = 8;
    private static final int HEADER_TOP_PADDING = 8;
    private static final int HEADER_TITLE_DESC_GAP = 7;
    private static final int HEADER_LINE_GAP = 2;
    private static final int HEADER_BOTTOM_PADDING = 6;
    private static final int INDENT_STEP = 8;

    /** Cards the user opened, kept for the session. */
    static final Set<String> EXPANDED = new HashSet<>();

    final NiaConfigScreen s;
    final String key;
    final String title;
    final String description;
    final MapConfigScreen.ToggleEntry master;
    final List<NiaRow> rows = new ArrayList<>();
    final boolean search;
    private final Animation opening = new Animation(Animation.Easing.EASE_OUT_EXPO, 200);
    private final Animation settingOpacity = new Animation(Animation.Easing.LINEAR, 100);
    private final Animation hover = new Animation(Animation.Easing.LINEAR, 50);
    private float headerToggleAnim = -1;
    private boolean expanded;
    private boolean mouseDown;
    private int x, y, width;
    private int clipTop = Integer.MIN_VALUE, clipBottom = Integer.MAX_VALUE;
    private int headerHeight = MIN_HEADER_HEIGHT;
    private List<FormattedCharSequence> titleLines = List.of(), descLines = List.of();
    private int linesFor = -1;
    private final int[] depth = new int[8];
    private final NiaRow[] parents = new NiaRow[8];

    NiaSection(NiaConfigScreen s, String key, String title, String description, MapConfigScreen.ToggleEntry master, boolean search) {
        this.s = s;
        this.key = key;
        this.title = title;
        this.description = description;
        this.master = master;
        this.search = search;
        this.expanded = search || EXPANDED.contains(key);
        opening.setValue(MIN_HEADER_HEIGHT);
        settingOpacity.setValue(0);
    }

    boolean isExpanded() { return expanded; }

    void setExpanded(boolean e) {
        expanded = e;
        if (search) return;
        if (e) EXPANDED.add(key);
        else EXPANDED.remove(key);
    }

    void syncStateImmediately(Font font) {
        layout(font);
        opening.setValue(expanded ? expandedHeight() : headerHeight);
        settingOpacity.setValue(expanded ? 255 : 0);
        hover.setValue(0);
        headerToggleAnim = master != null && master.value() ? 1 : 0;
    }

    void setPosition(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    void setViewportClip(int top, int bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    int getHeight() {
        return Math.max(headerHeight, (int) Math.round(opening.getValue()));
    }

    private void updateVisibility() {
        int n = 0;
        for (NiaRow r : rows) {
            if (search) { r.visible = true; continue; }
            while (n > 0 && depth[n - 1] >= r.indent) n--;
            boolean hidden = false;
            for (int i = 0; i < n; i++) {
                if (!((NiaRow.Toggle) parents[i]).t.value()) { hidden = true; break; }
            }
            r.visible = !hidden;
            if (r instanceof NiaRow.Toggle && n < 8) {
                depth[n] = r.indent;
                parents[n] = r;
                n++;
            }
        }
    }

    private int indentPx(NiaRow r) {
        return search ? 0 : Math.min(4, r.indent / 2) * INDENT_STEP;
    }

    private void layout(Font font) {
        int textW = headerTextWidth();
        if (textW != linesFor) {
            titleLines = NiaRender.wrap(font, title, textW);
            descLines = NiaRender.wrap(font, description, textW);
            linesFor = textW;
        }
        int th = textHeight(font, titleLines.size());
        int dh = textHeight(font, descLines.size());
        int gap = th > 0 && dh > 0 ? HEADER_TITLE_DESC_GAP : 0;
        headerHeight = Math.max(MIN_HEADER_HEIGHT, HEADER_TOP_PADDING + th + gap + dh + HEADER_BOTTOM_PADDING);
        updateVisibility();
        int childW = Math.max(1, width - SETTING_START_X_OFFSET * 2);
        for (NiaRow r : rows) {
            if (r.visible) r.layout(font, childW - indentPx(r));
        }
    }

    private int textHeight(Font font, int lines) {
        if (lines <= 0) return 0;
        return lines * font.lineHeight + (lines - 1) * HEADER_LINE_GAP;
    }

    private float expandedHeight() {
        float total = headerHeight + CONTENT_TOP_PADDING + CONTENT_BOTTOM_PADDING;
        int count = 0;
        for (NiaRow r : rows) {
            if (!r.visible) continue;
            if (count++ > 0) total += CHILD_VERTICAL_GAP;
            total += r.getHeight();
        }
        return count == 0 ? headerHeight : total;
    }

    /** Layout and animation step without drawing, for cards outside the viewport. */
    void update(Font font) {
        layout(font);
        float eh = expandedHeight();
        opening.setDuration(Math.min((long) eh * 3, 450));
        opening.run(expanded ? eh : headerHeight);
        settingOpacity.setDuration(expanded ? opening.getDuration() / 2 : opening.getDuration() / 3);
        settingOpacity.run(expanded ? 255 : 0);
    }

    void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme) {
        update(font);
        int total = getHeight();
        NiaRender.roundedRect(g, x, y, width, total, 8, theme.overlay());

        boolean headerHovered = mx >= x && mx <= x + width && my >= y && my <= y + headerHeight && my >= clipTop && my < clipBottom;
        hover.run(headerHovered ? mouseDown ? 35 : 20 : 0);
        if (hover.getValue() > 0.5) {
            NiaRender.roundedRect(g, x, y, width, headerHeight, 8, NiaRender.withAlpha(0x000000, (int) hover.getValue()));
        }

        boolean enabled = master == null || master.value();
        int nameColor = enabled ? (0xFF000000 | (theme.accentColor() & 0x00FFFFFF)) : 0xC8FFFFFF;
        int ty = y + HEADER_TOP_PADDING;
        for (FormattedCharSequence line : titleLines) {
            g.text(font, line, x + HEADER_TEXT_X, ty, nameColor, false);
            ty += font.lineHeight + HEADER_LINE_GAP;
        }
        if (!titleLines.isEmpty() && !descLines.isEmpty()) ty += HEADER_TITLE_DESC_GAP - HEADER_LINE_GAP;
        for (FormattedCharSequence line : descLines) {
            g.text(font, line, x + HEADER_TEXT_X, ty, 0x46FFFFFF, false);
            ty += font.lineHeight + HEADER_LINE_GAP;
        }
        if (master != null) {
            headerToggleAnim = NiaRow.Toggle.animate(headerToggleAnim, enabled);
            NiaRow.Toggle.renderToggle(g, theme, headerSwitchX(), headerSwitchY(), headerToggleAnim, 255, false);
        }

        if (total <= headerHeight + 1) return;
        int opacity = (int) settingOpacity.getValue();
        int top = y + headerHeight, bottom = y + total;
        NiaRender.pushClip(g, x, top, x + width, bottom);
        int visTop = Math.max(top, clipTop), visBottom = Math.min(bottom, clipBottom);
        float cy = headerHeight + CONTENT_TOP_PADDING;
        for (NiaRow r : rows) {
            if (!r.visible) continue;
            int rx = x + SETTING_START_X_OFFSET + indentPx(r);
            int ry = y + Math.round(cy);
            r.setPosition(rx, ry);
            if (ry + r.getHeight() > visTop && ry < visBottom) {
                r.render(g, font, mx, my, theme, opacity);
                if (my >= visTop && my < visBottom && r.contains(mx, my)) s.hoverRow = r;
            }
            cy += r.getHeight() + CHILD_VERTICAL_GAP;
        }
        NiaRender.popClip(g);
    }

    private boolean inClip(double my) {
        return my >= clipTop && my < clipBottom;
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (!inClip(my)) return false;
        if (mx >= x && mx <= x + width && my >= y && my <= y + headerHeight) {
            mouseDown = true;
            if (NiaRow.left(button) && master != null && NiaRow.Toggle.overSwitch(mx, my, headerSwitchX(), headerSwitchY())) {
                boolean v = !master.value();
                master.writeValue(v);
                TeslaMapsConfig.save();
                GuiSounds.toggle(v);
                s.sectionsDirty = true;
                return true;
            }
            if (NiaRow.left(button) && !rows.isEmpty()) {
                setExpanded(!expanded);
                GuiSounds.fold(expanded);
            }
            return true;
        }
        if (expanded && getHeight() > headerHeight + 1) {
            for (NiaRow r : rows) {
                if (r.visible && r.mouseClicked(mx, my, button)) return true;
            }
        }
        return false;
    }

    boolean mouseDragged(double mx, double my) {
        for (NiaRow r : rows) if (r.mouseDragged(mx, my)) return true;
        return false;
    }

    boolean mouseReleased(double mx, double my) {
        mouseDown = false;
        boolean any = false;
        for (NiaRow r : rows) any |= r.mouseReleased(mx, my);
        return any;
    }

    private int headerTextWidth() {
        int right = master != null ? headerSwitchX() - HEADER_SWITCH_TEXT_GAP : x + width - HEADER_TEXT_X;
        return Math.max(1, right - (x + HEADER_TEXT_X));
    }

    private int headerSwitchX() {
        return x + width - NiaRow.Toggle.SWITCH_WIDTH - HEADER_SWITCH_RIGHT_PADDING;
    }

    private int headerSwitchY() {
        return y + Math.round(headerHeight / 2f) - NiaRow.Toggle.SWITCH_HEIGHT / 2;
    }
}
