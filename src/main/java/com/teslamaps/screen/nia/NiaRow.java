package com.teslamaps.screen.nia;

import com.teslamaps.config.ConfigProfiles;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.screen.ClassFilterScreen;
import com.teslamaps.screen.MapConfigScreen;
import com.teslamaps.screen.SoundPickerScreen;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Ported from NiaMod's gui/component/*Component.java and WrappedText.java
abstract class NiaRow {
    static final int LINE_GAP = 2;
    static final int VERTICAL_PADDING = 4;

    final NiaConfigScreen s;
    final MapConfigScreen.SettingsEntry entry;
    final String label;
    final int indent;
    boolean visible = true;
    int x, y, width;
    int height = 18;
    private List<FormattedCharSequence> lines = List.of();
    private int linesFor = -1;

    NiaRow(NiaConfigScreen s, MapConfigScreen.SettingsEntry entry) {
        this.s = s;
        this.entry = entry;
        String l = entry.getLabel();
        int i = 0;
        while (i < l.length() && l.charAt(i) == ' ') i++;
        this.indent = i;
        this.label = l.trim();
    }

    static NiaRow of(NiaConfigScreen s, MapConfigScreen.SettingsEntry e) {
        if (e instanceof MapConfigScreen.ToggleEntry t) return new Toggle(s, t);
        if (e instanceof MapConfigScreen.SliderEntry t) return new Slider(s, t);
        if (e instanceof MapConfigScreen.DropdownEntry t) return new Choice(s, t, t.options(), false);
        if (e instanceof MapConfigScreen.SoundDropdownEntry t) return new Choice(s, t, t.options(), true);
        if (e instanceof MapConfigScreen.ColorEntry t) return new ColorPicker(s, t);
        if (e instanceof MapConfigScreen.ButtonEntry t) return new Button(s, t);
        if (e instanceof MapConfigScreen.ButtonRowEntry t) return new Buttons(s, t);
        if (e instanceof MapConfigScreen.KeybindEntry t) return new Keybind(s, t);
        if (e instanceof MapConfigScreen.ClassFilterEntry t) return new ClassFilter(s, t);
        if (e instanceof MapConfigScreen.InfoEntry) return new Info(s, e);
        if (e instanceof MapConfigScreen.ProfileTilesEntry) return new ProfileTiles(s, e);
        return new Delegate(s, e);
    }

    int minHeight() { return 18; }

    int labelMaxWidth(int w) { return w; }

    final List<FormattedCharSequence> lines() { return lines; }

    void layout(Font font, int w) {
        width = w;
        if (w != linesFor) {
            lines = NiaRender.wrap(font, label, labelMaxWidth(w));
            linesFor = w;
        }
        height = rowHeight(font, lines, minHeight());
    }

    int getHeight() { return height; }

    void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my < y + height;
    }

    abstract void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity);

    boolean mouseClicked(double mx, double my, int button) { return false; }

    boolean mouseDragged(double mx, double my) { return false; }

    boolean mouseReleased(double mx, double my) { return false; }

    void drawLabel(GuiGraphicsExtractor g, Font font, int lx, int rowH, int color) {
        int ty = centeredY(y, rowH, textHeight(font, lines));
        for (FormattedCharSequence line : lines) {
            g.text(font, line, lx, ty, color, false);
            ty += font.lineHeight + LINE_GAP;
        }
    }

    static int textHeight(Font font, List<FormattedCharSequence> lines) {
        if (lines.isEmpty()) return 0;
        return lines.size() * font.lineHeight + (lines.size() - 1) * LINE_GAP;
    }

    static int rowHeight(Font font, List<FormattedCharSequence> lines, int minimum) {
        int th = textHeight(font, lines);
        if (th == 0) return minimum;
        return Math.max(minimum, th + VERTICAL_PADDING * 2);
    }

    static int centeredY(int y, int rowH, int textH) {
        return y + Math.max(0, (rowH - textH) / 2);
    }

    static boolean left(int button) { return button == NiaConfigScreen.LEFT; }

    static int textColor(int opacity) { return (Math.min(220, opacity) << 24) | 0xFFFFFF; }

    // ------------------------------------------------------------------ toggle

    static final class Toggle extends NiaRow {
        static final int SWITCH_WIDTH = 24;
        static final int SWITCH_HEIGHT = 12;
        private static final int SWITCH_RIGHT_PADDING = 4;
        private static final int LABEL_CONTROL_GAP = 8;
        static final String CHEAT_TIP = "Cheat - may break Hypixel rules (ban risk)";

        final MapConfigScreen.ToggleEntry t;
        private float anim = -1;

        Toggle(NiaConfigScreen s, MapConfigScreen.ToggleEntry t) {
            super(s, t);
            this.t = t;
        }

        @Override
        int labelMaxWidth(int w) {
            return Math.max(1, w - SWITCH_WIDTH - SWITCH_RIGHT_PADDING - LABEL_CONTROL_GAP - (t.isCheat() ? 10 : 0));
        }

        static void renderToggle(GuiGraphicsExtractor g, NiaTheme theme, int sx, int sy, float anim, int opacity, boolean dim) {
            int trackColor = NiaRender.withAlpha(theme.secondary(), Math.min(240, opacity));
            int onColor = NiaRender.withAlpha(dim ? 0x6A6F78 : theme.accentColor(), Math.min(255, opacity));
            int current = NiaRender.lerpColor(trackColor, onColor, anim);
            NiaRender.surface(g, sx, sy, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2f, current,
                    NiaRender.withAlpha(0xFFFFFF, Math.min(dim ? 20 : 40, opacity)));
            int knob = SWITCH_HEIGHT - 2;
            int kx0 = sx + 1;
            int kx1 = sx + SWITCH_WIDTH - 1 - knob;
            int kx = Math.round(kx0 + anim * (kx1 - kx0));
            int knobAlpha = dim ? Math.min(110, opacity) : Math.min(255, opacity + 50);
            NiaRender.roundedRect(g, kx, sy + 1, knob, knob, knob / 2f, NiaRender.withAlpha(0xFFFFFF, knobAlpha));
        }

        static boolean overSwitch(double mx, double my, int sx, int sy) {
            return mx >= sx && mx <= sx + SWITCH_WIDTH && my >= sy && my <= sy + SWITCH_HEIGHT;
        }

        static float animate(float a, boolean on) {
            if (a < 0) return on ? 1 : 0;
            return (a * 4f + (on ? 1f : 0f)) / 5f;
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            boolean on = t.value();
            String gate = t.gateReason();
            anim = animate(anim, on);
            int alpha = Math.min(220, opacity);
            int color;
            if (gate != null) color = NiaRender.withAlpha(0x8A8A8A, Math.min(140, opacity));
            else if (t.isCheat()) color = NiaRender.withAlpha(0xFF5555, alpha);
            else color = on ? NiaRender.withAlpha(theme.accentColor(), alpha) : NiaRender.withAlpha(0xFFFFFF, alpha);
            int lx = x;
            if (t.isCheat()) {
                NiaRender.text(g, font, "⚠", lx, centeredY(y, height, font.lineHeight), color);
                lx += 10;
            }
            drawLabel(g, font, lx, height, color);
            renderToggle(g, theme, x + width - SWITCH_WIDTH - SWITCH_RIGHT_PADDING,
                    y + height / 2 - SWITCH_HEIGHT / 2, anim, opacity, gate != null);
            if (contains(mx, my)) {
                if (gate != null) s.tip(gate);
                else if (t.isCheat()) s.tip(CHEAT_TIP);
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button) || !contains(mx, my)) return false;
            if (t.gateReason() != null) return true;
            boolean v = !t.value();
            t.writeValue(v);
            TeslaMapsConfig.save();
            GuiSounds.toggle(v);
            return true;
        }
    }

    // ------------------------------------------------------------------ slider

    static final class Slider extends NiaRow {
        private static final int TRACK_HEIGHT = 2;
        private static final int GRABBER_SIZE = 5;

        final MapConfigScreen.SliderEntry t;
        private final float min, max;
        private final int decimals;
        private int trackX, trackWidth, valueX, valueWidth;
        private boolean dragging;
        private float dragSquish;
        private int lastNotch = Integer.MIN_VALUE;
        private float cachedFor = Float.NaN;
        private String cachedText = "";

        Slider(NiaConfigScreen s, MapConfigScreen.SliderEntry t) {
            super(s, t);
            this.t = t;
            this.min = t.min();
            this.max = t.max();
            this.decimals = t.decimals();
        }

        @Override
        int labelMaxWidth(int w) { return Math.max(1, (int) (w * 0.45f) - 10); }

        private float value() { return (Float) t.readValue(); }

        private float progress() {
            if (max == min) return 0;
            return Math.clamp((value() - min) / (max - min), 0, 1);
        }

        private String format() {
            float v = value();
            if (v != cachedFor) {
                cachedFor = v;
                cachedText = String.format(Locale.ROOT, "%." + decimals + "f", v);
            }
            return cachedText;
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            int textColor = textColor(opacity);
            int centerY = y + height / 2;
            drawLabel(g, font, x, height, textColor);

            float pct = progress();
            dragSquish *= 0.82f;
            String valueText = format();
            int minBox = Math.max(30, NiaRender.width(font, valueText) + 8);
            valueWidth = Math.max(minBox, (int) (width * 0.15f));
            valueX = x + width - valueWidth;
            int controlX = x + (int) (width * 0.45f);
            trackWidth = Math.max(50, valueX - 8 - controlX);
            trackX = controlX;
            int trackY = centerY - TRACK_HEIGHT / 2;

            g.fill(trackX, trackY, trackX + trackWidth, trackY + TRACK_HEIGHT, NiaRender.withAlpha(theme.background(), Math.min(254, opacity)));
            g.fill(trackX, trackY, trackX + Math.round(pct * trackWidth), trackY + TRACK_HEIGHT,
                    NiaRender.withAlpha(theme.accentColor(), Math.min(180, opacity)));
            float gcx = trackX + pct * trackWidth;
            int kw = Math.max(2, GRABBER_SIZE - Math.round(dragSquish));
            int kh = GRABBER_SIZE + Math.round(dragSquish * 0.65f);
            int gx = Math.round(gcx - kw / 2.0f);
            int gy = centerY - kh / 2;
            g.fill(gx, gy, gx + kw, gy + kh, NiaRender.withAlpha(theme.accentColor(), Math.min(255, opacity)));

            int boxH = Math.max(16, font.lineHeight + 4);
            int boxY = centerY - boxH / 2;
            boolean editing = s.editing(this);
            int border = editing ? NiaRender.withAlpha(theme.accentColor(), Math.min(120, opacity + 30))
                    : NiaRender.withAlpha(0xFFFFFF, Math.min(34, opacity / 6 + 18));
            NiaRender.surface(g, valueX, boxY, valueWidth, boxH, 5, NiaRender.withAlpha(theme.secondary(), Math.min(200, opacity)), border);
            if (editing) {
                s.placeEditor(valueX + 6, boxY, valueWidth - 12, boxH);
            } else {
                NiaRender.text(g, font, valueText, valueX + 6, centerY - font.lineHeight / 2, textColor);
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button) || !contains(mx, my)) return false;
            if (mx >= valueX) {
                if (!s.editing(this)) s.beginEdit(this, format(), 12, this::typed);
                return true;
            }
            if (mx >= trackX - 3 && mx <= trackX + trackWidth + 3) {
                dragging = true;
                fromMouse(mx);
                return true;
            }
            return false;
        }

        private void typed(String text) {
            try {
                float v = Math.clamp(Float.parseFloat(text.trim()), min, max);
                t.writeValue(v);
            } catch (NumberFormatException ignored) {
            }
        }

        @Override
        boolean mouseDragged(double mx, double my) {
            if (!dragging) return false;
            dragSquish = Math.min(3.0f, dragSquish + 0.6f);
            fromMouse(mx);
            return true;
        }

        @Override
        boolean mouseReleased(double mx, double my) {
            if (!dragging) return false;
            dragging = false;
            TeslaMapsConfig.save();
            return true;
        }

        private void fromMouse(double mx) {
            float pct = Math.clamp((float) ((mx - trackX) / trackWidth), 0, 1);
            float v = min + pct * (max - min);
            if (decimals == 0) v = Math.round(v);
            t.writeValue(v);
            float step = (float) Math.pow(10, -decimals);
            int notch = Math.round(v / step);
            if (notch != lastNotch) {
                lastNotch = notch;
                GuiSounds.step(pct);
            }
        }
    }

    // ------------------------------------------------------------------ choice (dropdown / sound)

    static final class Choice extends NiaRow {
        private static final int ARROW_WIDTH = 16;
        private static final int MIN_CONTROL_WIDTH = 104;
        private static final int CONTROL_HEIGHT = 14;
        private static final int ROW_SIDE_PADDING = 4;
        private static final int LABEL_GAP = 10;

        private final String[] options;
        private final boolean sound;
        private int cx, cy, cw;

        Choice(NiaConfigScreen s, MapConfigScreen.SettingsEntry e, String[] options, boolean sound) {
            super(s, e);
            this.options = options;
            this.sound = sound;
        }

        @Override
        int minHeight() { return 24; }

        @Override
        int labelMaxWidth(int w) { return Math.max(1, (int) (w * 0.45f) - LABEL_GAP - 1); }

        private String value() {
            Object v = entry.readValue();
            return v == null ? "" : v.toString();
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            int textAlpha = Math.min(220, opacity);
            int textColor = textColor(opacity);
            drawLabel(g, font, x + 1, height, textColor);

            cx = x + (int) (width * 0.45f);
            cw = Math.max(MIN_CONTROL_WIDTH, x + width - ROW_SIDE_PADDING - cx);
            cy = y + (height - CONTROL_HEIGHT) / 2;
            boolean hovered = mx >= cx && mx <= cx + cw && my >= cy && my <= cy + CONTROL_HEIGHT;
            int fill = hovered ? NiaRender.withAlpha(theme.secondary(), Math.min(245, opacity + 24))
                    : NiaRender.withAlpha(theme.secondary(), Math.min(225, opacity));
            int border = hovered ? NiaRender.withAlpha(theme.accentColor(), Math.min(96, opacity + 18))
                    : NiaRender.withAlpha(0xFFFFFF, Math.min(28, textAlpha));
            NiaRender.surface(g, cx, cy, cw, CONTROL_HEIGHT, 5, fill, border);

            int ty = cy + Math.max(0, (CONTROL_HEIGHT - font.lineHeight) / 2);
            int arrowColor = NiaRender.withAlpha(0xFFFFFF, hovered ? Math.min(255, opacity) : Math.min(200, opacity));
            int inner = cw - 4;
            int labelLeft = cx + 2;
            if (!sound) {
                int divider = NiaRender.withAlpha(0xFFFFFF, Math.min(36, textAlpha));
                g.fill(cx + ARROW_WIDTH, cy + 2, cx + ARROW_WIDTH + 1, cy + CONTROL_HEIGHT - 2, divider);
                g.fill(cx + cw - ARROW_WIDTH - 1, cy + 2, cx + cw - ARROW_WIDTH, cy + CONTROL_HEIGHT - 2, divider);
                NiaRender.text(g, font, "<", cx + 5, ty, arrowColor);
                NiaRender.text(g, font, ">", cx + cw - 9, ty, arrowColor);
                inner = cw - ARROW_WIDTH * 2 - 4;
                labelLeft = cx + ARROW_WIDTH + 2;
            } else {
                NiaRender.text(g, font, "♪", cx + cw - 10, ty, arrowColor);
                inner = cw - 18;
            }
            String v = NiaRender.ellipsize(font, value(), inner);
            int lw = NiaRender.width(font, v);
            NiaRender.text(g, font, v, labelLeft + (inner - lw) / 2, ty, textColor);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button)) return false;
            if (mx < cx || mx > cx + cw || my < cy || my > cy + CONTROL_HEIGHT) return contains(mx, my);
            if (sound) {
                GuiSounds.press();
                Minecraft.getInstance().gui.setScreen(new SoundPickerScreen(s, label, options, value(),
                        v -> { entry.writeValue(v); TeslaMapsConfig.save(); }));
                return true;
            }
            if (options.length == 0) return true;
            int i = 0;
            String cur = value();
            for (int k = 0; k < options.length; k++) if (options[k].equals(cur)) i = k;
            i = mx <= cx + ARROW_WIDTH ? (i - 1 + options.length) % options.length : (i + 1) % options.length;
            entry.writeValue(options[i]);
            TeslaMapsConfig.save();
            GuiSounds.select();
            return true;
        }
    }

    // ------------------------------------------------------------------ colour

    static final class ColorPicker extends NiaRow {
        private static final int CLOSED_HEIGHT = 18;
        private static final int SWATCH_SIZE = 12;
        private static final int SWATCH_RIGHT_PADDING = 4;
        private static final int HEX_WIDTH = 66;
        private static final int HEX_GAP = 8;
        private static final int LABEL_CONTROL_GAP = 8;
        private static final int PICKER_PANEL_GAP = 2;
        private static final int PICKER_BOTTOM_PADDING = 4;
        private static final int SV_H = 70;
        private static final int BAR_H = 8;
        private static final int PANEL_H = 4 + SV_H + 6 + BAR_H + 6 + BAR_H + 4;
        private static final int[] HUES = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

        final MapConfigScreen.ColorEntry t;
        boolean expanded;
        private float hue, sat, val;
        private int alpha = 255;
        private boolean dragSV, dragHue, dragAlpha;
        private String syncedFrom;
        private int closedHeight = CLOSED_HEIGHT;

        ColorPicker(NiaConfigScreen s, MapConfigScreen.ColorEntry t) {
            super(s, t);
            this.t = t;
            syncFromSetting();
        }

        @Override
        int labelMaxWidth(int w) {
            return Math.max(1, w - SWATCH_SIZE - SWATCH_RIGHT_PADDING - HEX_WIDTH - HEX_GAP - LABEL_CONTROL_GAP);
        }

        @Override
        void layout(Font font, int w) {
            super.layout(font, w);
            closedHeight = height;
            if (expanded) height = closedHeight + PICKER_PANEL_GAP + PANEL_H + PICKER_BOTTOM_PADDING;
        }

        private int argb() {
            return (alpha << 24) | NiaRender.hsvToRgb(hue, sat, val);
        }

        private int hexFor;
        private String hexText;

        private String hex(int argb) {
            if (hexText == null || argb != hexFor) {
                hexFor = argb;
                hexText = (argb >>> 24) == 255 ? String.format("#%06X", argb & 0xFFFFFF) : String.format("#%08X", argb);
            }
            return hexText;
        }

        private void syncFromSetting() {
            String v = (String) t.readValue();
            if (v == syncedFrom) return;
            syncedFrom = v;
            int c = TeslaMapsConfig.parseColor(v);
            if (c == argb()) return;
            float[] hsv = NiaRender.rgbToHsv(c & 0xFFFFFF);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            alpha = c >>> 24;
        }

        private void commit() {
            int c = argb();
            String v = alpha == 255 ? String.format("%06X", c & 0xFFFFFF) : String.format("%08X", c);
            t.writeValue(v);
            syncedFrom = (String) t.readValue();
        }

        private int swatchX() { return x + width - SWATCH_SIZE - SWATCH_RIGHT_PADDING; }

        private int hexX() { return swatchX() - HEX_WIDTH - HEX_GAP; }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            if (!dragSV && !dragHue && !dragAlpha && !s.editing(this)) syncFromSetting();
            int textColor = textColor(opacity);
            int centerY = y + closedHeight / 2;
            drawLabel(g, font, x, closedHeight, textColor);

            int current = argb();
            int sx = swatchX();
            int sy = centerY - SWATCH_SIZE / 2;
            NiaRender.surface(g, sx - 1, sy - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 4,
                    NiaRender.withAlpha(current, Math.min(opacity, current >>> 24)), NiaRender.withAlpha(0xFFFFFF, Math.min(72, opacity)));

            int hx = hexX();
            int hh = Math.max(16, font.lineHeight + 4);
            int hy = centerY - hh / 2;
            boolean editing = s.editing(this);
            int border = editing ? NiaRender.withAlpha(theme.accentColor(), Math.min(120, opacity + 30))
                    : NiaRender.withAlpha(0xFFFFFF, Math.min(34, opacity / 6 + 18));
            NiaRender.surface(g, hx, hy, HEX_WIDTH, hh, 5, NiaRender.withAlpha(theme.secondary(), Math.min(200, opacity)), border);
            if (editing) s.placeEditor(hx + 6, hy, HEX_WIDTH - 12, hh);
            else NiaRender.text(g, font, hex(current), hx + 6, centerY - font.lineHeight / 2, textColor);

            if (!expanded) return;

            int px = x + 4;
            int py = y + closedHeight + PICKER_PANEL_GAP;
            int pw = width - 8;
            NiaRender.surface(g, px, py, pw, PANEL_H, 6, NiaRender.withAlpha(theme.secondary(), opacity),
                    NiaRender.withAlpha(0xFFFFFF, Math.min(28, opacity / 6 + 16)));

            int svX = px + 4, svY = py + 4, svW = pw - 8;
            NiaRender.horizontalGradient(g, svX, svY, svX + svW, svY + SV_H, 0xFFFFFFFF, 0xFF000000 | NiaRender.hsvToRgb(hue, 1, 1));
            g.fillGradient(svX, svY, svX + svW, svY + SV_H, 0x00000000, 0xFF000000);
            NiaRender.outline(g, svX - 1, svY - 1, svW + 2, SV_H + 2, NiaRender.withAlpha(0xFFFFFF, Math.min(40, opacity)));
            int crossX = svX + Math.round(sat * svW);
            int crossY = svY + Math.round((1 - val) * SV_H);
            NiaRender.circle(g, crossX, crossY, 6, NiaRender.withAlpha(0xFFFFFF, opacity));
            NiaRender.circle(g, crossX, crossY, 4, NiaRender.withAlpha(current, opacity));

            int barX = px + 4, barW = pw - 8;
            int hueY = svY + SV_H + 6;
            float seg = barW / 6f;
            for (int i = 0; i < 6; i++) {
                NiaRender.horizontalGradient(g, barX + seg * i, hueY, barX + seg * (i + 1), hueY + BAR_H, HUES[i], HUES[i + 1]);
            }
            NiaRender.outline(g, barX - 1, hueY - 1, barW + 2, BAR_H + 2, NiaRender.withAlpha(0xFFFFFF, Math.min(40, opacity)));
            int hm = barX + Math.round(hue / 360f * barW);
            NiaRender.roundedRect(g, hm - 2, hueY - 1, 4, BAR_H + 2, 2, NiaRender.withAlpha(0xFFFFFF, opacity));

            int alphaY = hueY + BAR_H + 6;
            for (int cx = 0; cx < barW; cx += 4) {
                g.fill(barX + cx, alphaY, Math.min(barX + barW, barX + cx + 4), alphaY + BAR_H / 2, (cx / 4) % 2 == 0 ? 0xFFCCCCCC : 0xFF888888);
                g.fill(barX + cx, alphaY + BAR_H / 2, Math.min(barX + barW, barX + cx + 4), alphaY + BAR_H, (cx / 4) % 2 == 0 ? 0xFF888888 : 0xFFCCCCCC);
            }
            NiaRender.horizontalGradient(g, barX, alphaY, barX + barW, alphaY + BAR_H, current & 0x00FFFFFF, current | 0xFF000000);
            NiaRender.outline(g, barX - 1, alphaY - 1, barW + 2, BAR_H + 2, NiaRender.withAlpha(0xFFFFFF, Math.min(40, opacity)));
            int am = barX + Math.round(alpha / 255f * barW);
            NiaRender.roundedRect(g, am - 2, alphaY - 1, 4, BAR_H + 2, 2, NiaRender.withAlpha(0xFFFFFF, opacity));
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button)) return false;
            if (mx >= x && mx <= x + width && my >= y && my <= y + closedHeight) {
                if (mx >= hexX() && mx <= hexX() + HEX_WIDTH) {
                    if (!s.editing(this)) s.beginEdit(this, hex(argb()).substring(1), 8, this::typed);
                    return true;
                }
                expanded = !expanded;
                GuiSounds.fold(expanded);
                return true;
            }
            if (!expanded || !contains(mx, my)) return false;
            int px = x + 4, pw = width - 8;
            int svX = px + 4, svY = y + closedHeight + PICKER_PANEL_GAP + 4, svW = pw - 8;
            int hueY = svY + SV_H + 6;
            int alphaY = hueY + BAR_H + 6;
            if (my >= svY && my <= svY + SV_H) dragSV = true;
            else if (my >= hueY - 2 && my <= hueY + BAR_H + 2) dragHue = true;
            else if (my >= alphaY - 2 && my <= alphaY + BAR_H + 2) dragAlpha = true;
            else return true;
            mouseDragged(mx, my);
            return true;
        }

        private void typed(String text) {
            String h = text.startsWith("#") ? text.substring(1) : text;
            if ((h.length() == 6 || h.length() == 8) && h.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                t.writeValue(h.length() == 8 && h.toUpperCase(Locale.ROOT).startsWith("FF") ? h.substring(2).toUpperCase(Locale.ROOT) : h.toUpperCase(Locale.ROOT));
                syncedFrom = null;
                syncFromSetting();
            }
        }

        @Override
        boolean mouseDragged(double mx, double my) {
            if (!dragSV && !dragHue && !dragAlpha) return false;
            int px = x + 4, pw = width - 8;
            int svX = px + 4, svY = y + closedHeight + PICKER_PANEL_GAP + 4, svW = pw - 8;
            if (dragSV) {
                sat = Math.clamp((float) (mx - svX) / svW, 0, 1);
                val = Math.clamp(1.0f - (float) (my - svY) / SV_H, 0, 1);
            } else if (dragHue) {
                hue = Math.clamp((float) (mx - svX) / svW * 360.0f, 0, 359.9f);
            } else {
                alpha = Math.round(Math.clamp((float) (mx - svX) / svW, 0, 1) * 255);
            }
            commit();
            return true;
        }

        @Override
        boolean mouseReleased(double mx, double my) {
            if (!dragSV && !dragHue && !dragAlpha) return false;
            dragSV = dragHue = dragAlpha = false;
            TeslaMapsConfig.save();
            return true;
        }
    }

    // ------------------------------------------------------------------ buttons

    static final class Button extends NiaRow {
        final MapConfigScreen.ButtonEntry t;

        Button(NiaConfigScreen s, MapConfigScreen.ButtonEntry t) {
            super(s, t);
            this.t = t;
        }

        @Override
        int minHeight() { return 22; }

        @Override
        void layout(Font font, int w) {
            width = w;
            height = 22;
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            int bx = x + 2, by = y + 2, bw = width - 4, bh = 18;
            boolean hovered = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            int fill = NiaRender.withAlpha(theme.secondary(), Math.min(hovered ? 242 : 224, opacity));
            int border = t.isActive() ? NiaRender.withAlpha(theme.accentColor(), Math.min(200, opacity))
                    : NiaRender.withAlpha(0xFFFFFF, Math.min(hovered ? 56 : 26, opacity));
            NiaRender.surface(g, bx, by, bw, bh, 5, fill, border);
            String text = NiaRender.ellipsize(font, label, bw - 24);
            int tw = NiaRender.width(font, text);
            int color = t.isActive() ? NiaRender.withAlpha(theme.accentColor(), Math.min(255, opacity))
                    : NiaRender.withAlpha(0xFFFFFF, Math.min(hovered ? 255 : 214, opacity));
            int tx = bx + (bw - tw) / 2;
            NiaRender.text(g, font, text, tx, by + (bh - font.lineHeight) / 2 + 1, color);
            if (t.isActive()) TeslaGui.checkIcon(g, tx - 13, by + 4, 9, color);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button) || !contains(mx, my)) return false;
            GuiSounds.press();
            t.press();
            return true;
        }
    }

    static final class Buttons extends NiaRow {
        private static final int GAP = 6;
        final MapConfigScreen.ButtonRowEntry t;
        private final String[] labels;

        Buttons(NiaConfigScreen s, MapConfigScreen.ButtonRowEntry t) {
            super(s, t);
            this.t = t;
            this.labels = t.labels();
        }

        @Override
        void layout(Font font, int w) {
            width = w;
            height = 22;
        }

        private int buttonWidth() {
            return Math.min(150, (width - 4 - GAP * (labels.length - 1)) / labels.length);
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            int bw = buttonWidth(), by = y + 2, bh = 18;
            for (int i = 0; i < labels.length; i++) {
                int bx = x + 2 + i * (bw + GAP);
                boolean hovered = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
                NiaRender.surface(g, bx, by, bw, bh, 5, NiaRender.withAlpha(theme.secondary(), Math.min(hovered ? 242 : 224, opacity)),
                        NiaRender.withAlpha(0xFFFFFF, Math.min(hovered ? 56 : 26, opacity)));
                String text = NiaRender.ellipsize(font, labels[i], bw - 8);
                int tw = NiaRender.width(font, text);
                NiaRender.text(g, font, text, bx + (bw - tw) / 2, by + (bh - font.lineHeight) / 2 + 1,
                        NiaRender.withAlpha(0xFFFFFF, Math.min(hovered ? 255 : 214, opacity)));
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button) || !contains(mx, my)) return false;
            int bw = buttonWidth();
            for (int i = 0; i < labels.length; i++) {
                int bx = x + 2 + i * (bw + GAP);
                if (mx >= bx && mx <= bx + bw) {
                    GuiSounds.press();
                    t.press(i);
                    return true;
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ keybind / class filter (ButtonComponent)

    abstract static class SideButton extends NiaRow {
        private static final int BUTTON_HEIGHT = 14;
        private static final int BUTTON_RIGHT_PADDING = 4;
        private static final int LABEL_CONTROL_GAP = 8;
        private final int buttonWidth;
        int bx, by;

        SideButton(NiaConfigScreen s, MapConfigScreen.SettingsEntry e, int buttonWidth) {
            super(s, e);
            this.buttonWidth = buttonWidth;
        }

        @Override
        int labelMaxWidth(int w) { return Math.max(1, w - buttonWidth - BUTTON_RIGHT_PADDING - LABEL_CONTROL_GAP); }

        abstract String buttonText();

        int buttonTextColor(int opacity) { return textColor(opacity); }

        boolean highlighted() { return false; }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            drawLabel(g, font, x, height, textColor(opacity));
            bx = x + width - buttonWidth - BUTTON_RIGHT_PADDING;
            by = y + height / 2 - BUTTON_HEIGHT / 2;
            boolean hovered = overButton(mx, my);
            int fill = NiaRender.withAlpha(theme.secondary(), Math.min(hovered ? 242 : 224, opacity));
            int border = highlighted() ? NiaRender.withAlpha(theme.accentColor(), Math.min(160, opacity))
                    : NiaRender.withAlpha(0xFFFFFF, Math.min(hovered ? 56 : 26, opacity));
            NiaRender.surface(g, bx, by, buttonWidth, BUTTON_HEIGHT, 4, fill, border);
            String text = NiaRender.ellipsize(font, buttonText(), buttonWidth - 6);
            int tw = NiaRender.width(font, text);
            NiaRender.text(g, font, text, bx + (buttonWidth - tw) / 2, by + (BUTTON_HEIGHT - font.lineHeight) / 2 + 1, buttonTextColor(opacity));
        }

        boolean overButton(double mx, double my) {
            return mx >= bx && mx <= bx + buttonWidth && my >= by && my <= by + BUTTON_HEIGHT;
        }
    }

    static final class Keybind extends SideButton {
        final MapConfigScreen.KeybindEntry t;
        private int shownKey = Integer.MIN_VALUE;
        private String shownText = "";

        Keybind(NiaConfigScreen s, MapConfigScreen.KeybindEntry t) {
            super(s, t, 70);
            this.t = t;
        }

        @Override
        String buttonText() {
            if (s.model.listening(t)) return "press a key...";
            int k = (Integer) t.readValue();
            if (k != shownKey) {
                shownKey = k;
                shownText = KeyNames.display(k);
            }
            return shownText;
        }

        @Override
        boolean highlighted() { return s.model.listening(t); }

        @Override
        int buttonTextColor(int opacity) {
            return t.conflicts() != null ? NiaRender.withAlpha(0xFF5555, Math.min(255, opacity)) : textColor(opacity);
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            super.render(g, font, mx, my, theme, opacity);
            if (overButton(mx, my)) {
                List<String> c = t.conflicts();
                if (c != null) s.tip("Also bound to: " + String.join(", ", c));
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button) || !contains(mx, my)) return false;
            if (overButton(mx, my)) {
                GuiSounds.press();
                s.model.listen(t);
            }
            return true;
        }
    }

    static final class ClassFilter extends SideButton {
        final MapConfigScreen.ClassFilterEntry t;

        ClassFilter(NiaConfigScreen s, MapConfigScreen.ClassFilterEntry t) {
            super(s, t, 96);
            this.t = t;
        }

        @Override
        String buttonText() { return t.filter().summary(); }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button) || !contains(mx, my)) return false;
            if (overButton(mx, my)) {
                GuiSounds.press();
                Minecraft.getInstance().gui.setScreen(new ClassFilterScreen(s, label, t.filter()));
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ info

    static final class Info extends NiaRow {
        Info(NiaConfigScreen s, MapConfigScreen.SettingsEntry e) {
            super(s, e);
        }

        @Override
        void layout(Font font, int w) {
            super.layout(font, w);
            height = Math.max(12, textHeight(font, lines()) + 4);
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            int ty = y + 2;
            int color = NiaRender.withAlpha(theme.trinaryText(), Math.min(150, opacity));
            for (FormattedCharSequence line : lines()) {
                g.text(font, line, x, ty, color, false);
                ty += font.lineHeight + LINE_GAP;
            }
        }
    }

    // ------------------------------------------------------------------ profile tiles

    static final class ProfileTiles extends NiaRow {
        private static final int TILE_H = 20;
        private static final int TILE_MAX_W = 150;
        private static final int GAP = 6;
        private final List<String> names;
        private String confirm;

        ProfileTiles(NiaConfigScreen s, MapConfigScreen.SettingsEntry e) {
            super(s, e);
            names = new ArrayList<>(ConfigProfiles.list());
        }

        private int columns() { return Math.max(2, Math.min(5, (width - 4) / (TILE_MAX_W + GAP))); }

        private int tileWidth() {
            int cols = columns();
            return Math.min(TILE_MAX_W, (width - 4 - GAP * (cols - 1)) / cols);
        }

        @Override
        void layout(Font font, int w) {
            width = w;
            int rows = (names.size() + columns() - 1) / columns();
            height = Math.max(1, rows) * (TILE_H + GAP) + 2;
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            String active = ConfigProfiles.active();
            int cols = columns(), tw = tileWidth();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                int tx = x + 2 + (i % cols) * (tw + GAP);
                int ty = y + 2 + (i / cols) * (TILE_H + GAP);
                boolean isActive = name.equals(active);
                boolean deletable = !isActive && !ConfigProfiles.isPreset(name);
                boolean confirming = name.equals(confirm);
                boolean hovered = mx >= tx && mx <= tx + tw && my >= ty && my <= ty + TILE_H;
                boolean onDelete = deletable && hovered && mx >= tx + tw - 16;
                int border = confirming ? NiaRender.withAlpha(0xFF5555, Math.min(200, opacity))
                        : isActive ? NiaRender.withAlpha(theme.accentColor(), Math.min(200, opacity))
                        : NiaRender.withAlpha(0xFFFFFF, Math.min(hovered ? 56 : 26, opacity));
                NiaRender.surface(g, tx, ty, tw, TILE_H, 6, NiaRender.withAlpha(theme.secondary(), Math.min(hovered ? 242 : 224, opacity)), border);
                int textColor = confirming ? NiaRender.withAlpha(0xFF5555, Math.min(255, opacity))
                        : isActive ? NiaRender.withAlpha(theme.accentColor(), Math.min(255, opacity)) : textColor(opacity);
                int lx = tx + (isActive ? 18 : 7);
                int right = deletable ? tx + tw - 16 : tx + tw - 6;
                NiaRender.text(g, font, NiaRender.ellipsize(font, confirming ? "delete?" : name, right - lx), lx,
                        ty + (TILE_H - font.lineHeight) / 2 + 1, textColor);
                if (isActive) TeslaGui.checkIcon(g, tx + 6, ty + 5, 9, textColor);
                if (deletable) {
                    NiaRender.text(g, font, "x", tx + tw - 11, ty + (TILE_H - font.lineHeight) / 2 + 1,
                            onDelete || confirming ? NiaRender.withAlpha(0xFF5555, Math.min(255, opacity)) : NiaRender.withAlpha(theme.trinaryText(), Math.min(200, opacity)));
                }
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            if (!left(button) || !contains(mx, my)) return false;
            String active = ConfigProfiles.active();
            int cols = columns(), tw = tileWidth();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                int tx = x + 2 + (i % cols) * (tw + GAP);
                int ty = y + 2 + (i / cols) * (TILE_H + GAP);
                if (mx < tx || mx > tx + tw || my < ty || my > ty + TILE_H) continue;
                boolean isActive = name.equals(active);
                boolean deletable = !isActive && !ConfigProfiles.isPreset(name);
                if (deletable && mx >= tx + tw - 16) {
                    if (name.equals(confirm)) {
                        GuiSounds.press();
                        ConfigProfiles.delete(name);
                        confirm = null;
                        s.rebuildModel();
                    } else {
                        GuiSounds.fold(true);
                        confirm = name;
                    }
                    return true;
                }
                confirm = null;
                if (!isActive) {
                    GuiSounds.select();
                    ConfigProfiles.switchTo(name);
                    s.rebuildModel();
                }
                return true;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ everything else: drawn by the row itself

    static final class Delegate extends NiaRow {
        Delegate(NiaConfigScreen s, MapConfigScreen.SettingsEntry e) {
            super(s, e);
        }

        @Override
        void layout(Font font, int w) {
            width = w;
            entry.reposition(x, w);
            height = entry.getHeight();
        }

        @Override
        void render(GuiGraphicsExtractor g, Font font, int mx, int my, NiaTheme theme, int opacity) {
            if (opacity < 128) return;
            entry.reposition(x, width);
            entry.render(g, s.model, y, mx, my, s.frameClicked && contains(mx, my), s.frameMouseDown);
        }

        @Override
        boolean mouseClicked(double mx, double my, int button) {
            return contains(mx, my);
        }
    }
}
