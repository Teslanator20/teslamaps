package com.teslamaps.screen.h2;

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.config.ConfigProfiles;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.render.gui.CustomizeGui;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.screen.ConfigModel;
import com.teslamaps.screen.MapConfigScreen;
import com.teslamaps.screen.MapConfigScreen.SettingsEntry;
import com.teslamaps.screen.ScreenFit;
import com.teslamaps.screen.SoundPickerScreen;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * TeslaMaps settings in H2TEAM's look: rounded panel with soft shadow, darker sidebar, one card per
 * setting, shader-rounded switches and sliders. Rows come from MapConfigScreen via ConfigModel.
 */
// Ported from h2team's H2Screen.java
public class H2ConfigScreen extends Screen {
    private static final int ROUND = 12, SIDEBAR = 140, ROW_H = 36, GAP = 6, PAD = 14;
    private static final int TABS_TOP = 70, TAB_H = 22, TAB_STEP = 26, CAPTION_H = 16, SECTION_GAP = 6;
    private static final int INDENT = 12;
    private static final String CHEAT_MARK = "⚠ ";
    private static final String CHEAT_TIP = "Cheat — may break Hypixel rules (ban risk)";
    private static final String PROFILES_TAB = "Profiles & Interface";

    private static String tab;
    private static String lastQuery = "";

    public static Screen create(Screen parent) {
        return new H2ConfigScreen(parent, MapConfigScreen.View.OLD);
    }

    public static Screen create(Screen parent, MapConfigScreen.View view) {
        return new H2ConfigScreen(parent, view);
    }

    private final Screen parent;
    private final ConfigModel model;
    private int panelW, panelH;

    private EditBox search;
    private String query = "";
    private List<SettingsEntry> rows = List.of();
    private boolean rowsDirty = true;
    private int rowsBuild = -1;
    private String subtitle = "";
    private int[] rowY = new int[0], rowH = new int[0];
    private int contentTop, contentBottom;

    private final List<String> sideItems = new ArrayList<>();   // "" prefix = section caption
    private int sideBuild = -1;

    private final Map<SettingsEntry, Anim> anims = new IdentityHashMap<>();
    private final Map<String, float[]> tabAnim = new HashMap<>();
    private float openT, scroll, scrollTarget, contentT, sideScroll, sideScrollTarget, sideMax;
    private long lastFrame;

    private MapConfigScreen.SliderEntry dragging;
    private MapConfigScreen.KeybindEntry listening;
    private long listenStart;
    private MapConfigScreen.SliderEntry editSlider;
    private String editText = "";
    private Popup popup;
    private boolean classicClick, classicRight;
    private SettingsEntry tipEntry;
    private long tipStart;

    private static final class Anim {
        float hover, t;
        int tx, tw;                   // slider track / control rect of the last frame
        int px, py, pw, ph;
        String label, labelFit, descFit;
        int labelFitW = -1, descFitW = -1;
        List<String> lines;
        int linesW = -1;
        List<FormattedCharSequence> tip;
    }

    private static final class Popup {
        final SettingsEntry entry;
        int x, y, w, h;
        float t, scroll;
        float hue, sat, bri;
        int alpha = 255, drag;
        boolean hexEdit;
        String hex = "";

        Popup(SettingsEntry entry) { this.entry = entry; }
    }

    private H2ConfigScreen(Screen parent, MapConfigScreen.View view) {
        super(Component.literal("TeslaMaps"));
        this.parent = parent;
        this.model = new ConfigModel(view);
    }

    @Override
    protected void init() {
        panelW = Math.min(560, width - 16);
        panelH = Math.min(340, height - 16);
        model.build(width, height);
        anims.clear();
        popup = null;

        search = new EditBox(font, 0, 0, 100, 10, Component.literal("Search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(H2Ui.TEXT);
        hint();
        search.addFormatter((text, cursor) -> H2Ui.smooth ? H2Ui.styled(text).getVisualOrderText()
                : FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY));
        search.setValue(lastQuery);
        query = lastQuery.trim();
        search.setResponder(q -> {
            lastQuery = q;
            String t = q.trim();
            if (!t.equals(query)) {
                query = t;
                scroll = scrollTarget = 0;
                contentT = 0;
                popup = null;
                rowsDirty = true;
            }
        });
        addWidget(search);

        buildSidebar();
        if (tab == null || !model.groups().containsKey(tab)) tab = model.groups().containsKey(PROFILES_TAB) ? PROFILES_TAB : firstTab();
        rowsDirty = true;
        ScreenFit.clamp(this);
    }

    private void hint() {
        search.setHint((H2Ui.smooth ? H2Ui.styled("Search…") : Component.literal("Search…")).withColor(H2Ui.MUTED));
    }

    private String firstTab() {
        for (String s : sideItems) if (!s.startsWith("\0")) return s;
        return ConfigModel.ACTIVE;
    }

    private void buildSidebar() {
        sideItems.clear();
        for (Map.Entry<String, List<String>> sec : model.sections().entrySet()) {
            sideItems.add("\0" + sec.getKey());
            for (String g : sec.getValue()) if (!ConfigModel.NEW.equals(g)) sideItems.add(g);
        }
        sideBuild = model.buildCount();
    }

    private void refreshRows() {
        if (model.buildCount() != sideBuild) {
            buildSidebar();
            anims.clear();
            if (!model.groups().containsKey(tab)) tab = firstTab();
        }
        rowsBuild = model.buildCount();
        rowsDirty = false;
        if (!query.isEmpty()) {
            rows = model.search(query);
            int n = 0;
            for (SettingsEntry e : rows) if (!e.isLabel()) n++;
            subtitle = n == 0 ? "Nothing matches \"" + query + "\"" : n + (n == 1 ? " result" : " results") + " for \"" + query + "\"";
        } else {
            rows = model.rows(tab);
            List<String> members = model.groups().getOrDefault(tab, List.of());
            if (ConfigModel.ACTIVE.equals(tab)) subtitle = "Everything you switched on";
            else if (members.size() > 1) subtitle = String.join(" · ", members);
            else {
                int n = 0;
                for (SettingsEntry e : rows) if (!e.isLabel() && !(e instanceof MapConfigScreen.InfoEntry)) n++;
                subtitle = n + (n == 1 ? " option" : " options");
            }
        }
        if (popup != null && !rows.contains(popup.entry)) popup = null;
    }

    private void selectTab(String t) {
        if (t.equals(tab) && query.isEmpty()) return;
        tab = t;
        if (!query.isEmpty()) search.setValue("");
        scroll = scrollTarget = 0;
        contentT = 0;
        popup = null;
        rowsDirty = true;
        GuiSounds.select();
    }

    private int px() { return (width - panelW) / 2; }
    private int py() { return (height - panelH) / 2; }

    private Anim anim(SettingsEntry e) {
        Anim a = anims.get(e);
        if (a == null) {
            a = new Anim();
            a.label = e.getLabel().trim();
            if (e instanceof MapConfigScreen.ToggleEntry te) a.t = ConfigModel.get(te) ? 1f : 0f;
            anims.put(e, a);
        }
        return a;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (rowsDirty || model.buildCount() != rowsBuild) refreshRows();
        openT = H2Ui.approach(openT, 1f, dt * 10f);
        contentT = H2Ui.approach(contentT, 1f, dt * 12f);
        scroll = H2Ui.approach(scroll, scrollTarget, dt * 18f);
        sideScroll = H2Ui.approach(sideScroll, sideScrollTarget, dt * 18f);
        float e = H2Ui.ease(openT);
        int x = px(), y = py();
        boolean mouseDown = minecraft.mouseHandler.isLeftPressed();
        if (!mouseDown) { dragging = null; if (popup != null) popup.drag = 0; }

        ctx.fill(0, 0, width, height, H2Ui.alpha(0x8C000000, e));

        ctx.pose().pushMatrix();
        float s = 0.96f + 0.04f * e;
        ctx.pose().translate(width / 2f, height / 2f);
        ctx.pose().scale(s, s);
        ctx.pose().translate(-width / 2f, -height / 2f);

        H2Ui.shadow(ctx, x, y, panelW, panelH, ROUND, 8, 0x40);
        H2Ui.round(ctx, x, y, panelW, panelH, ROUND, H2Ui.BG);
        H2Ui.round(ctx, x, y, SIDEBAR, panelH, ROUND, H2Ui.SIDEBAR);
        H2Ui.round(ctx, x + SIDEBAR - ROUND, y, ROUND, panelH, 0, H2Ui.SIDEBAR);
        H2Ui.round(ctx, x + SIDEBAR, y + 12, 1, panelH - 24, 0, H2Ui.LINE);

        sidebar(ctx, x, y, mx, my, dt);
        content(ctx, x + SIDEBAR + PAD, y, panelW - SIDEBAR - PAD * 2, mx, my, dt, mouseDown);
        if (popup != null) {
            ctx.nextStratum();
            if (popup.entry instanceof MapConfigScreen.ColorEntry) colorPopup(ctx, mx, my, dt, mouseDown);
            else dropdownPopup(ctx, mx, my, dt);
        }

        ctx.pose().popMatrix();
        tooltip(ctx, mx, my, now);
    }

    // ─── sidebar ─────────────────────────────────────────────────────────────

    private int searchX() { return px() + 8; }
    private int searchY() { return py() + 40; }
    private int searchW() { return SIDEBAR - 16; }
    private int tabsTop() { return py() + TABS_TOP; }
    private int tabsBottom() { return py() + panelH - 38; }

    private void sidebar(GuiGraphicsExtractor ctx, int x, int y, int mx, int my, float dt) {
        H2Ui.text(ctx, "Tesla", x + 14, y + 14, H2Ui.ACCENT, H2Ui.LOGO);
        H2Ui.text(ctx, "Maps", x + 14 + H2Ui.width("Tesla", H2Ui.LOGO) + 3, y + 14, H2Ui.TEXT, H2Ui.LOGO);

        int sx = searchX(), sy = searchY(), sw = searchW();
        boolean focused = search.isFocused();
        H2Ui.surface(ctx, sx, sy, sw, 20, 7, H2Ui.BG, focused ? H2Ui.ACCENT : H2Ui.LINE);
        lens(ctx, sx + 8, sy + 6, focused ? H2Ui.ACCENT : H2Ui.MUTED);
        search.setX(sx + 20);
        search.setY(sy + 6);
        search.setWidth(sw - 26);
        search.extractRenderState(ctx, mx, my, 0);

        int top = tabsTop(), bottom = tabsBottom();
        int total = 0;
        for (int i = 0; i < sideItems.size(); i++) total += itemH(i);
        sideMax = Math.max(0, total - (bottom - top));
        sideScrollTarget = Math.max(0, Math.min(sideMax, sideScrollTarget));

        H2Ui.scissor(ctx, x, top, x + SIDEBAR, bottom);
        int by = top - Math.round(sideScroll);
        boolean searching = !query.isEmpty();
        for (int i = 0; i < sideItems.size(); i++) {
            String item = sideItems.get(i);
            int h = itemH(i);
            if (by + h < top || by > bottom) { by += h; continue; }
            if (item.startsWith("\0")) {
                H2Ui.text(ctx, item.substring(1).toUpperCase(), x + 14, by + h - 11, H2Ui.DIM);
                by += h;
                continue;
            }
            int bx = x + 8, bw = SIDEBAR - 16;
            boolean hover = my >= top && my < bottom && mx >= bx && mx < bx + bw && my >= by && my < by + TAB_H;
            boolean sel = item.equals(tab) && !searching;
            float[] a = tabAnim.computeIfAbsent(item, k -> new float[1]);
            a[0] = H2Ui.approach(a[0], sel ? 1f : hover ? 0.45f : 0f, dt * 14f);
            if (a[0] > 0.01f) H2Ui.round(ctx, bx, by, bw, TAB_H, 7, sel ? H2Ui.withAlpha(H2Ui.ACCENT, (int) (36 * a[0])) : H2Ui.withAlpha(0xFFFFFF, (int) (22 * a[0])));
            if (sel) H2Ui.round(ctx, bx + 8, by + 9, 4, 4, 2, H2Ui.ACCENT);
            int tx = bx + (sel ? 18 : 10);
            H2Ui.text(ctx, H2Ui.fit(item, bx + bw - 4 - tx), tx, by + 7, sel ? H2Ui.TEXT : H2Ui.mix(H2Ui.MUTED, H2Ui.TEXT2, a[0]));
            by += h;
        }
        H2Ui.endScissor(ctx);
        if (sideMax > 0) scrollbar(ctx, x + SIDEBAR - 5, top, bottom, sideScroll, sideMax, total);

        boolean legit = LegitMode.blocksCheats();
        String label = legit ? "Legit mode" : "Profile · " + ConfigProfiles.active();
        int color = legit ? H2Ui.WARN : H2Ui.GOOD;
        int stx = x + 8, sty = y + panelH - 30, stw = SIDEBAR - 16;
        boolean over = mx >= stx && mx < stx + stw && my >= sty && my < sty + 20;
        H2Ui.surface(ctx, stx, sty, stw, 20, 7, over ? H2Ui.CARD : H2Ui.BG, H2Ui.LINE);
        float pulse = legit ? 1f : 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 320.0);
        H2Ui.circle(ctx, stx + 10, sty + 10, 5, H2Ui.alpha(color, 0.25f * pulse));
        H2Ui.circle(ctx, stx + 10, sty + 10, 3, color);
        H2Ui.text(ctx, H2Ui.fit(label, stw - 23), stx + 19, sty + 6, H2Ui.TEXT2);
    }

    private int itemH(int i) {
        if (!sideItems.get(i).startsWith("\0")) return TAB_STEP;
        return i == 0 ? CAPTION_H : CAPTION_H + SECTION_GAP;
    }

    private static void lens(GuiGraphicsExtractor ctx, int x, int y, int color) {
        H2Ui.surface(ctx, x, y, 7, 7, 4, 0, color);
        H2Ui.round(ctx, x + 1, y + 1, 5, 5, 3, H2Ui.BG);
        H2Ui.round(ctx, x + 6, y + 6, 2, 2, 1, color);
        H2Ui.round(ctx, x + 7, y + 7, 2, 2, 1, color);
    }

    private static void scrollbar(GuiGraphicsExtractor ctx, int x, int top, int bottom, float scroll, float max, int total) {
        int trackH = bottom - top;
        int barH = Math.max(24, (int) (trackH * (trackH / (float) total)));
        int barY = top + (int) ((trackH - barH) * (scroll / max));
        H2Ui.round(ctx, x, barY, 3, barH, 2, 0x30FFFFFF);
    }

    // ─── content ─────────────────────────────────────────────────────────────

    private int rowHeight(SettingsEntry e, int w) {
        if (e.isLabel()) return 22;
        if (e instanceof MapConfigScreen.InfoEntry) return lines(e, w).size() * 10 + 4;
        if (custom(e)) return ROW_H;
        return e.getHeight() + 8;
    }

    /** Rows drawn with H2 widgets; everything else is drawn by MapConfigScreen inside a card. */
    private static boolean custom(SettingsEntry e) {
        return e instanceof MapConfigScreen.ToggleEntry || e instanceof MapConfigScreen.SliderEntry
                || e instanceof MapConfigScreen.DropdownEntry || e instanceof MapConfigScreen.SoundDropdownEntry
                || e instanceof MapConfigScreen.ColorEntry || e instanceof MapConfigScreen.KeybindEntry
                || e instanceof MapConfigScreen.ButtonEntry;
    }

    private List<String> lines(SettingsEntry e, int w) {
        Anim a = anim(e);
        if (a.lines == null || a.linesW != w) {
            a.linesW = w;
            a.lines = new ArrayList<>();
            for (FormattedCharSequence seq : font.split(Component.literal(a.label), Math.max(40, w))) {
                StringBuilder sb = new StringBuilder();
                seq.accept((idx, style, cp) -> { sb.appendCodePoint(cp); return true; });
                a.lines.add(sb.toString());
            }
            if (a.lines.isEmpty()) a.lines.add("");
        }
        return a.lines;
    }

    private void content(GuiGraphicsExtractor ctx, int cx, int y, int cw, int mx, int my, float dt, boolean mouseDown) {
        String title = query.isEmpty() ? tab : "Search";
        int fontW = 20, fontX = cx + cw - fontW;
        H2Ui.text(ctx, H2Ui.fit("§l" + title, fontX - cx - 6), cx, y + 16, H2Ui.TEXT, H2Ui.TITLE);
        H2Ui.text(ctx, H2Ui.fit(subtitle, cw), cx, y + 32, H2Ui.MUTED);
        boolean fontHover = mx >= fontX && mx < fontX + fontW && my >= y + 12 && my < y + 28;
        H2Ui.surface(ctx, fontX, y + 12, fontW, 16, 6, H2Ui.smooth ? H2Ui.withAlpha(H2Ui.ACCENT, 60) : fontHover ? H2Ui.CARD_HOVER : H2Ui.SIDEBAR, H2Ui.LINE);
        H2Ui.textCenter(ctx, "Aa", fontX + fontW / 2, y + 16, H2Ui.smooth ? H2Ui.TEXT : H2Ui.TEXT2);

        int top = y + 50, bottom = y + panelH - 10;
        contentTop = top;
        contentBottom = bottom;
        int n = rows.size();
        if (rowY.length < n) { rowY = new int[n + 16]; rowH = new int[n + 16]; }
        int total = 0;
        for (int i = 0; i < n; i++) {
            SettingsEntry e = rows.get(i);
            int ind = e.isLabel() ? 0 : ConfigModel.indent(e) * INDENT;
            rowH[i] = rowHeight(e, cw - ind);
            total += rowH[i] + gapAfter(i);
        }
        total = Math.max(0, total - GAP);
        float maxScroll = Math.max(0, total - (bottom - top));
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));

        H2Ui.scissor(ctx, cx - 4, top, cx + cw + 4, bottom);
        float slide = (1f - H2Ui.ease(contentT)) * 8f;
        int ry = top - (int) scroll + (int) slide;
        boolean inside = my >= top && my < bottom;
        boolean click = classicClick, right = classicRight;
        classicClick = classicRight = false;
        for (int i = 0; i < n; i++) {
            SettingsEntry e = rows.get(i);
            int h = rowH[i];
            rowY[i] = ry;
            if (ry + h >= top && ry < bottom) {
                int ind = e.isLabel() ? 0 : ConfigModel.indent(e) * INDENT;
                int x = cx + ind, w = cw - ind;
                if (ind > 0) H2Ui.round(ctx, x - INDENT / 2 - 1, ry + 4, 1, h - 8, 0, H2Ui.LINE);
                boolean hover = inside && mx >= x && mx < x + w && my >= ry && my < ry + h;
                if (e.isLabel()) header(ctx, e, x, ry, w);
                else if (e instanceof MapConfigScreen.InfoEntry) {
                    List<String> ls = lines(e, w);
                    for (int l = 0; l < ls.size(); l++) H2Ui.text(ctx, ls.get(l), x + 2, ry + 2 + l * 10, H2Ui.MUTED);
                } else if (custom(e)) {
                    row(ctx, e, x, ry, w, h, hover, mx, my, dt, mouseDown);
                } else {
                    Anim a = anim(e);
                    a.hover = H2Ui.approach(a.hover, hover ? 1f : 0f, dt * 14f);
                    H2Ui.surface(ctx, x, ry, w, h, 8, H2Ui.mix(H2Ui.CARD, H2Ui.CARD_HOVER, a.hover), H2Ui.LINE);
                    if (model.renderClassic(e, ctx, x, w, ry + 4, mx, my, click && hover, right && hover, mouseDown)) rowsDirty = true;
                }
            }
            ry += h + gapAfter(i);
        }
        H2Ui.endScissor(ctx);

        if (n == 0) H2Ui.textCenter(ctx, query.isEmpty() ? "Nothing here" : "No setting matches", cx + cw / 2, top + 30, H2Ui.MUTED);
        if (maxScroll > 0) scrollbar(ctx, cx + cw + 6, top, bottom, scroll, maxScroll, total);
    }

    private int gapAfter(int i) {
        SettingsEntry e = rows.get(i);
        if (e.isLabel()) return 2;
        if (e instanceof MapConfigScreen.InfoEntry) return 4;
        return GAP;
    }

    private void header(GuiGraphicsExtractor ctx, SettingsEntry e, int x, int y, int w) {
        Anim a = anim(e);
        int ty = y + 9;
        H2Ui.text(ctx, a.label, x + 2, ty, H2Ui.ACCENT);
        int lx = x + 2 + H2Ui.width(a.label) + 8;
        if (lx < x + w) H2Ui.round(ctx, lx, ty + 3, x + w - lx, 1, 0, H2Ui.LINE);
    }

    private void row(GuiGraphicsExtractor ctx, SettingsEntry e, int x, int y, int w, int h, boolean hover,
                     int mx, int my, float dt, boolean mouseDown) {
        Anim a = anim(e);
        a.hover = H2Ui.approach(a.hover, hover ? 1f : 0f, dt * 14f);
        boolean gated = e instanceof MapConfigScreen.ToggleEntry te && ConfigModel.gateReason(te) != null;
        boolean cheat = e instanceof MapConfigScreen.ToggleEntry te2 && ConfigModel.isCheat(te2);
        H2Ui.surface(ctx, x, y, w, h, 8, H2Ui.mix(H2Ui.CARD, H2Ui.CARD_HOVER, gated ? a.hover * 0.4f : a.hover), H2Ui.LINE);

        int cw = controlWidth(e, w);
        int textX = x + 12, textW = w - 24 - cw;
        if (cheat) {
            H2Ui.text(ctx, CHEAT_MARK, textX, y + (e.description() == null ? (h - 8) / 2 : 8), gated ? H2Ui.DIM : H2Ui.BAD);
            int mw = H2Ui.width(CHEAT_MARK);
            textX += mw;
            textW -= mw;
        }
        if (a.labelFit == null || a.labelFitW != textW) { a.labelFit = H2Ui.fit(a.label, textW); a.labelFitW = textW; }
        int labelColor = gated ? H2Ui.DIM : cheat ? H2Ui.BAD : H2Ui.TEXT;
        String desc = e.description();
        if (desc == null || desc.isEmpty()) {
            H2Ui.text(ctx, a.labelFit, textX, y + (h - 8) / 2, labelColor);
        } else {
            if (a.descFit == null || a.descFitW != textW) { a.descFit = H2Ui.fit(desc, textW); a.descFitW = textW; }
            H2Ui.text(ctx, a.labelFit, textX, y + 8, labelColor);
            H2Ui.text(ctx, a.descFit, textX, y + 20, gated ? H2Ui.DIM : H2Ui.MUTED);
        }

        if (e instanceof MapConfigScreen.ToggleEntry te) toggle(ctx, te, a, x, y, w, h, dt, gated);
        else if (e instanceof MapConfigScreen.SliderEntry se) slider(ctx, se, a, x, y, w, h, mx, mouseDown);
        else if (e instanceof MapConfigScreen.DropdownEntry || e instanceof MapConfigScreen.SoundDropdownEntry) {
            String v = ConfigModel.getString(e);
            boolean open = popup != null && popup.entry == e;
            pill(ctx, a, x, y, w, h, cw - 14, H2Ui.fit(v, cw - 14 - 22), open, mx, my);
            chevron(ctx, a.px + a.pw - 12, a.py + 8, open ? H2Ui.ACCENT : H2Ui.MUTED, e instanceof MapConfigScreen.SoundDropdownEntry);
        } else if (e instanceof MapConfigScreen.ColorEntry) colorPill(ctx, e, a, x, y, w, h, cw - 14, mx, my);
        else if (e instanceof MapConfigScreen.KeybindEntry ke) keybind(ctx, ke, a, x, y, w, h);
        else if (e instanceof MapConfigScreen.ButtonEntry be) button(ctx, be, a, x, y, w, h, mx, my, dt);
    }

    private int controlWidth(SettingsEntry e, int w) {
        if (e instanceof MapConfigScreen.ToggleEntry) return 40;
        if (e instanceof MapConfigScreen.SliderEntry se) return track(w) + valueW(se) + 26;
        if (e instanceof MapConfigScreen.DropdownEntry || e instanceof MapConfigScreen.SoundDropdownEntry)
            return Math.min(w / 2, Math.max(70, H2Ui.width(ConfigModel.getString(e)) + 30)) + 14;
        if (e instanceof MapConfigScreen.ColorEntry) return Math.max(H2Ui.width("#" + ConfigModel.getString(e)) + 30, 70) + 14;
        if (e instanceof MapConfigScreen.KeybindEntry ke) return Math.max(70, H2Ui.width(keyText(ke)) + 20) + 14;
        if (e instanceof MapConfigScreen.ButtonEntry be) return H2Ui.width(buttonText(be)) + 36;
        return 0;
    }

    private static int track(int w) {
        return Math.max(40, Math.min(120, w / 3));
    }

    private int valueW(MapConfigScreen.SliderEntry s) {
        return Math.max(30, H2Ui.width(valueText(s)) + 12);
    }

    private String valueText(MapConfigScreen.SliderEntry s) {
        if (editSlider == s) return editText + "_";
        return String.format("%." + ConfigModel.decimals(s) + "f", ConfigModel.get(s));
    }

    private void toggle(GuiGraphicsExtractor ctx, MapConfigScreen.ToggleEntry te, Anim a, int x, int y, int w, int h, float dt, boolean gated) {
        a.t = H2Ui.approach(a.t, ConfigModel.get(te) ? 1f : 0f, dt * 16f);
        int sw = 24, sh = 12, sx = x + w - sw - 12, sy = y + (h - sh) / 2;
        int on = gated ? H2Ui.mix(H2Ui.SIDEBAR, H2Ui.ACCENT, 0.35f) : H2Ui.ACCENT;
        H2Ui.surface(ctx, sx, sy, sw, sh, 6, H2Ui.mix(H2Ui.SIDEBAR, on, a.t), H2Ui.withAlpha(0xFFFFFF, (int) (40 * (1f - a.t))));
        int knob = 8, kx = sx + 2 + Math.round(a.t * (sw - knob - 4)), ky = sy + 2;
        H2Ui.round(ctx, kx, ky, knob, knob, 4, gated ? 0x80FFFFFF : 0xFFFFFFFF);
    }

    private void slider(GuiGraphicsExtractor ctx, MapConfigScreen.SliderEntry s, Anim a, int x, int y, int w, int h, int mx, boolean mouseDown) {
        int track = track(w), vw = valueW(s);
        int tx = x + w - track - vw - 16, ty = y + h / 2 - 1;
        a.tx = tx;
        a.tw = track;
        float min = ConfigModel.min(s), max = ConfigModel.max(s);
        if (dragging == s && mouseDown) setSlider(s, mx);
        float f = Math.max(0f, Math.min(1f, (ConfigModel.get(s) - min) / (max - min)));
        H2Ui.round(ctx, tx, ty, track, 2, 1, H2Ui.SIDEBAR);
        H2Ui.round(ctx, tx, ty, Math.max(2, Math.round(track * f)), 2, 1, H2Ui.withAlpha(H2Ui.ACCENT, 200));
        boolean active = dragging == s;
        int gr = active ? 6 : 5;
        H2Ui.circle(ctx, tx + Math.round(track * f), ty + 1, gr + 2, H2Ui.withAlpha(H2Ui.ACCENT, active ? 70 : 0));
        H2Ui.circle(ctx, tx + Math.round(track * f), ty + 1, gr, 0xFFFFFFFF);
        int vx = x + w - vw - 10, vy = y + (h - 16) / 2;
        a.px = vx; a.py = vy; a.pw = vw; a.ph = 16;
        boolean editing = editSlider == s;
        H2Ui.surface(ctx, vx, vy, vw, 16, 6, H2Ui.SIDEBAR, editing ? H2Ui.ACCENT : H2Ui.LINE);
        H2Ui.textCenter(ctx, valueText(s), vx + vw / 2, vy + 4, editing ? H2Ui.TEXT : H2Ui.TEXT2);
    }

    private void setSlider(MapConfigScreen.SliderEntry s, double mx) {
        Anim a = anim(s);
        float f = (float) Math.max(0, Math.min(1, (mx - a.tx) / a.tw));
        float min = ConfigModel.min(s), max = ConfigModel.max(s);
        float step = (float) Math.pow(10, -ConfigModel.decimals(s));
        float v = Math.round((min + f * (max - min)) / step) * step;
        if (v != ConfigModel.get(s)) {
            ConfigModel.set(s, v);
            GuiSounds.step(f);
        }
    }

    private void pill(GuiGraphicsExtractor ctx, Anim a, int x, int y, int w, int h, int pw, String text, boolean active, int mx, int my) {
        int ph = 20, pxx = x + w - pw - 10, pyy = y + (h - ph) / 2;
        a.px = pxx; a.py = pyy; a.pw = pw; a.ph = ph;
        boolean over = mx >= pxx && mx < pxx + pw && my >= pyy && my < pyy + ph;
        H2Ui.surface(ctx, pxx, pyy, pw, ph, 6, over ? H2Ui.CARD : H2Ui.SIDEBAR, active ? H2Ui.ACCENT : H2Ui.LINE);
        H2Ui.text(ctx, text, pxx + 8, pyy + 6, H2Ui.TEXT);
    }

    /** Small chevron, or a speaker-ish dot pair for sound pickers. */
    private static void chevron(GuiGraphicsExtractor ctx, int x, int y, int color, boolean sound) {
        if (sound) {
            H2Ui.round(ctx, x, y + 1, 2, 3, 1, color);
            H2Ui.round(ctx, x + 3, y, 2, 5, 1, color);
            H2Ui.round(ctx, x + 6, y - 1, 2, 7, 1, color);
            return;
        }
        for (int i = 0; i < 3; i++) ctx.fill(x + i, y + i, x + 7 - i, y + i + 1, color);
    }

    private void colorPill(GuiGraphicsExtractor ctx, SettingsEntry e, Anim a, int x, int y, int w, int h, int pw, int mx, int my) {
        int argb = TeslaMapsConfig.parseColor(ConfigModel.getString(e));
        boolean open = popup != null && popup.entry == e;
        pill(ctx, a, x, y, w, h, pw, "", open, mx, my);
        H2Ui.round(ctx, a.px + 4, a.py + 4, 12, 12, 4, 0xFF000000 | argb);
        if ((argb >>> 24) != 0xFF) H2Ui.round(ctx, a.px + 4, a.py + 10, 12, 6, 0, argb);
        H2Ui.text(ctx, hexOf(argb), a.px + 22, a.py + 6, H2Ui.TEXT2);
    }

    private String keyText(MapConfigScreen.KeybindEntry k) {
        return listening == k ? "Press a key…" : KeyNames.display(ConfigModel.key(k));
    }

    private void keybind(GuiGraphicsExtractor ctx, MapConfigScreen.KeybindEntry k, Anim a, int x, int y, int w, int h) {
        boolean active = listening == k;
        String text = keyText(k);
        List<String> conflicts = active ? null : model.conflicts(k);
        int bw = Math.max(70, H2Ui.width(text) + 20), bh = 20, bx = x + w - bw - 10, by = y + (h - bh) / 2;
        a.px = bx; a.py = by; a.pw = bw; a.ph = bh;
        H2Ui.surface(ctx, bx, by, bw, bh, 6, H2Ui.SIDEBAR, active ? H2Ui.ACCENT : conflicts != null ? H2Ui.BAD : H2Ui.LINE);
        int color = active ? H2Ui.ACCENT : conflicts != null ? H2Ui.BAD : ConfigModel.key(k) < 0 ? H2Ui.MUTED : H2Ui.TEXT;
        H2Ui.textCenter(ctx, text, bx + bw / 2, by + 6, color);
    }

    private static String buttonText(MapConfigScreen.ButtonEntry b) {
        if (ConfigModel.isActive(b)) return "Active";
        String l = b.getLabel().trim().toLowerCase();
        return l.startsWith("open") || l.startsWith("edit") || l.startsWith("manage") || l.startsWith("browse")
                || l.startsWith("customize") || l.startsWith("configure") || l.endsWith("...") ? "Open" : "Run";
    }

    private void button(GuiGraphicsExtractor ctx, MapConfigScreen.ButtonEntry b, Anim a, int x, int y, int w, int h, int mx, int my, float dt) {
        String text = buttonText(b);
        int bw = H2Ui.width(text) + 22, bh = 20, bx = x + w - bw - 10, by = y + (h - bh) / 2;
        a.px = bx; a.py = by; a.pw = bw; a.ph = bh;
        boolean over = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
        a.t = H2Ui.approach(a.t, over ? 1f : 0f, dt * 14f);
        int base = ConfigModel.isActive(b) ? H2Ui.GOOD : H2Ui.ACCENT;
        H2Ui.round(ctx, bx, by, bw, bh, 7, H2Ui.mix(H2Ui.withAlpha(base, 200), base, a.t));
        H2Ui.textCenter(ctx, text, bx + bw / 2, by + 6, 0xFFFFFFFF);
    }

    // ─── popups ──────────────────────────────────────────────────────────────

    private static final int OPT_H = 18, OPT_VISIBLE = 8;

    private String[] popupOptions() {
        return popup.entry instanceof MapConfigScreen.DropdownEntry d ? ConfigModel.options(d) : new String[0];
    }

    private void dropdownPopup(GuiGraphicsExtractor ctx, int mx, int my, float dt) {
        Anim a = anim(popup.entry);
        String[] opts = popupOptions();
        String cur = ConfigModel.getString(popup.entry);
        int ow = a.pw;
        for (String o : opts) ow = Math.max(ow, H2Ui.width(o) + 30);
        ow = Math.min(ow, panelW - SIDEBAR - PAD * 2);
        int vis = Math.min(OPT_VISIBLE, opts.length), oh = vis * OPT_H + 8;
        int ox = a.px + a.pw - ow, oy = a.py + a.ph + 4;
        if (oy + oh > py() + panelH - 6) oy = a.py - oh - 4;
        oy = Math.max(4, Math.min(height - oh - 4, oy));
        popup.t = H2Ui.approach(popup.t, 1f, dt * 16f);
        oy += Math.round((1f - H2Ui.ease(popup.t)) * -4f);
        popup.x = ox; popup.y = oy; popup.w = ow; popup.h = oh;
        float maxScroll = Math.max(0, opts.length * OPT_H - vis * OPT_H);
        popup.scroll = Math.max(0, Math.min(maxScroll, popup.scroll));

        H2Ui.shadow(ctx, ox, oy, ow, oh, 8, 6, 0x50);
        H2Ui.surface(ctx, ox, oy, ow, oh, 8, H2Ui.BG, H2Ui.LINE);
        H2Ui.scissor(ctx, ox, oy + 4, ox + ow, oy + oh - 4);
        for (int i = 0; i < opts.length; i++) {
            int iy = oy + 4 + i * OPT_H - Math.round(popup.scroll);
            if (iy + OPT_H < oy || iy > oy + oh) continue;
            boolean sel = opts[i].equals(cur);
            boolean over = mx >= ox + 4 && mx < ox + ow - 4 && my >= iy && my < iy + OPT_H && my >= oy + 4 && my < oy + oh - 4;
            if (sel) H2Ui.round(ctx, ox + 4, iy, ow - 8, OPT_H, 6, H2Ui.withAlpha(H2Ui.ACCENT, 36));
            else if (over) H2Ui.round(ctx, ox + 4, iy, ow - 8, OPT_H, 6, H2Ui.withAlpha(0xFFFFFF, 18));
            if (sel) H2Ui.round(ctx, ox + 10, iy + 7, 4, 4, 2, H2Ui.ACCENT);
            H2Ui.text(ctx, H2Ui.fit(opts[i], ow - 30), ox + 20, iy + 5, sel ? H2Ui.TEXT : H2Ui.TEXT2);
        }
        H2Ui.endScissor(ctx);
        if (maxScroll > 0) scrollbar(ctx, ox + ow - 5, oy + 4, oy + oh - 4, popup.scroll, maxScroll, opts.length * OPT_H);
    }

    private static final int SV = 90, BAR = 10;

    private void colorPopup(GuiGraphicsExtractor ctx, int mx, int my, float dt, boolean mouseDown) {
        Anim a = anim(popup.entry);
        int pw = 8 + SV + 6 + BAR + 6 + BAR + 8, ph = 8 + SV + 8 + 18 + 8;
        int ox = a.px + a.pw - pw, oy = a.py + a.ph + 4;
        if (oy + ph > py() + panelH - 6) oy = a.py - ph - 4;
        oy = Math.max(4, Math.min(height - ph - 4, oy));
        popup.t = H2Ui.approach(popup.t, 1f, dt * 16f);
        oy += Math.round((1f - H2Ui.ease(popup.t)) * -4f);
        popup.x = ox; popup.y = oy; popup.w = pw; popup.h = ph;
        int sx = ox + 8, sy = oy + 8, hx = sx + SV + 6, ax = hx + BAR + 6;

        if (mouseDown && popup.drag != 0) {
            float fx = Math.max(0f, Math.min(1f, (mx - sx) / (float) SV));
            float fy = Math.max(0f, Math.min(1f, (my - sy) / (float) SV));
            if (popup.drag == 1) { popup.sat = fx; popup.bri = 1f - fy; }
            else if (popup.drag == 2) popup.hue = fy;
            else popup.alpha = Math.round((1f - fy) * 255);
            writeColor();
        }

        H2Ui.shadow(ctx, ox, oy, pw, ph, 8, 6, 0x50);
        H2Ui.surface(ctx, ox, oy, pw, ph, 8, H2Ui.BG, H2Ui.LINE);

        int hue = 0xFF000000 | java.awt.Color.HSBtoRGB(popup.hue, 1f, 1f);
        CustomizeGui.horizontalGradient(ctx, sx, sy, sx + SV, sy + SV, 0xFFFFFFFF, hue);
        ctx.fillGradient(sx, sy, sx + SV, sy + SV, 0x00000000, 0xFF000000);
        for (int i = 0; i < 6; i++) {
            int c0 = 0xFF000000 | java.awt.Color.HSBtoRGB(i / 6f, 1f, 1f);
            int c1 = 0xFF000000 | java.awt.Color.HSBtoRGB((i + 1) / 6f, 1f, 1f);
            ctx.fillGradient(hx, sy + SV * i / 6, hx + BAR, sy + SV * (i + 1) / 6, c0, c1);
        }
        for (int cy = 0; cy < SV; cy += 5) {
            for (int cx = 0; cx < BAR; cx += 5) {
                ctx.fill(ax + cx, sy + cy, ax + cx + 5, sy + Math.min(SV, cy + 5), ((cx + cy) / 5) % 2 == 0 ? 0xFFCCCCCC : 0xFF888888);
            }
        }
        int rgb = java.awt.Color.HSBtoRGB(popup.hue, popup.sat, popup.bri) & 0xFFFFFF;
        ctx.fillGradient(ax, sy, ax + BAR, sy + SV, 0xFF000000 | rgb, rgb);

        int cxp = sx + Math.round(popup.sat * SV), cyp = sy + Math.round((1f - popup.bri) * SV);
        H2Ui.circle(ctx, cxp, cyp, 4, 0xFFFFFFFF);
        H2Ui.circle(ctx, cxp, cyp, 3, 0xFF000000 | rgb);
        int hy = sy + Math.round(popup.hue * SV);
        H2Ui.round(ctx, hx - 1, hy - 1, BAR + 2, 3, 1, 0xFFFFFFFF);
        int ay = sy + Math.round((1f - popup.alpha / 255f) * SV);
        H2Ui.round(ctx, ax - 1, ay - 1, BAR + 2, 3, 1, 0xFFFFFFFF);

        int fy = sy + SV + 8, fw = pw - 16;
        H2Ui.surface(ctx, sx, fy, fw, 18, 6, H2Ui.SIDEBAR, popup.hexEdit ? H2Ui.ACCENT : H2Ui.LINE);
        int argb = (popup.alpha << 24) | rgb;
        H2Ui.round(ctx, sx + 4, fy + 4, 10, 10, 3, 0xFF000000 | rgb);
        String text = popup.hexEdit ? "#" + popup.hex + "_" : hexOf(argb);
        boolean valid = !popup.hexEdit || validHex(popup.hex);
        H2Ui.text(ctx, text, sx + 20, fy + 5, valid ? H2Ui.TEXT2 : H2Ui.BAD);
    }

    private void openColor(SettingsEntry e) {
        popup = new Popup(e);
        loadColor(TeslaMapsConfig.parseColor(ConfigModel.getString(e)));
    }

    private void loadColor(int argb) {
        float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        popup.hue = hsb[0];
        popup.sat = hsb[1];
        popup.bri = hsb[2];
        popup.alpha = (argb >>> 24) & 0xFF;
    }

    private void writeColor() {
        int rgb = java.awt.Color.HSBtoRGB(popup.hue, popup.sat, popup.bri) & 0xFFFFFF;
        String hex = popup.alpha == 255 ? String.format("%06X", rgb) : String.format("%02X%06X", popup.alpha, rgb);
        if (!hex.equals(ConfigModel.getString(popup.entry))) ConfigModel.setString(popup.entry, hex);
    }

    private static String hexOf(int argb) {
        int a = (argb >>> 24) & 0xFF;
        return a == 255 ? String.format("#%06X", argb & 0xFFFFFF) : String.format("#%08X", argb);
    }

    private static boolean validHex(String t) {
        if (t.length() != 6 && t.length() != 8) return false;
        for (int i = 0; i < t.length(); i++) if (Character.digit(t.charAt(i), 16) < 0) return false;
        return true;
    }

    private void commitHex() {
        if (!validHex(popup.hex)) return;
        String v = popup.hex.length() == 8 && popup.hex.startsWith("FF") ? popup.hex.substring(2) : popup.hex;
        ConfigModel.setString(popup.entry, v);
        loadColor(TeslaMapsConfig.parseColor(v));
        popup.hexEdit = false;
        GuiSounds.select();
    }

    // ─── tooltips ────────────────────────────────────────────────────────────

    private void tooltip(GuiGraphicsExtractor ctx, int mx, int my, long now) {
        SettingsEntry over = null;
        if (popup == null && my >= contentTop && my < contentBottom) {
            for (int i = 0; i < rows.size() && i < rowY.length; i++) {
                if (my >= rowY[i] && my < rowY[i] + rowH[i]) { over = rows.get(i); break; }
            }
        }
        if (over != tipEntry) { tipEntry = over; tipStart = now; }
        if (over == null || !custom(over)) return;
        Anim a = anim(over);
        if (over instanceof MapConfigScreen.ToggleEntry te) {
            String reason = ConfigModel.gateReason(te);
            if (reason != null) { ctx.setTooltipForNextFrame(font, Component.literal(reason).withColor(H2Ui.WARN), mx, my); return; }
            if (ConfigModel.isCheat(te)) { ctx.setTooltipForNextFrame(font, Component.literal(CHEAT_TIP).withColor(H2Ui.BAD), mx, my); return; }
        }
        if (over instanceof MapConfigScreen.KeybindEntry ke && mx >= a.px && mx < a.px + a.pw) {
            List<String> c = model.conflicts(ke);
            if (c != null) {
                List<FormattedCharSequence> tip = new ArrayList<>();
                tip.add(Component.literal("Also bound to:").withColor(H2Ui.BAD).getVisualOrderText());
                for (String s : c) tip.add(Component.literal(s).getVisualOrderText());
                ctx.setTooltipForNextFrame(font, tip, mx, my);
                return;
            }
        }
        String desc = over.description();
        boolean cut = desc != null && a.descFit != null && !a.descFit.equals(desc) || a.labelFit != null && !a.labelFit.equals(a.label);
        if (cut && now - tipStart > 500) {
            if (a.tip == null) {
                a.tip = new ArrayList<>();
                a.tip.add(Component.literal(a.label).getVisualOrderText());
                if (desc != null) a.tip.addAll(font.split(Component.literal(desc).withColor(0xFFAAAAAA), 220));
            }
            ctx.setTooltipForNextFrame(font, a.tip, mx, my);
        }
    }

    // ─── input ───────────────────────────────────────────────────────────────

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        boolean left = button == 1, rightBtn = button == 3;

        if (listening != null) {
            if (System.currentTimeMillis() - listenStart > 250) {
                ConfigModel.setKey(listening, KeyNames.forMouse(button));
                listening = null;
                GuiSounds.select();
            }
            return true;
        }
        if (popup != null) {
            if (in(mx, my, popup.x, popup.y, popup.w, popup.h)) {
                if (left) popupClick(mx, my);
                return true;
            }
            popup = null;
            return true;
        }
        if (editSlider != null) commitSlider();

        if (in(mx, my, searchX(), searchY(), searchW(), 20)) {
            model.stopClassicTyping();
            setFocused(search);
            search.setFocused(true);
            if (rightBtn) search.setValue("");
            return true;
        }
        if (search.isFocused()) { search.setFocused(false); setFocused(null); }
        if (!left && !rightBtn) return super.mouseClicked(event, doubled);

        int x = px(), y = py();
        int cx = x + SIDEBAR + PAD, cw = panelW - SIDEBAR - PAD * 2;
        if (left && in(mx, my, cx + cw - 20, y + 12, 20, 16)) {
            H2Ui.smooth = !H2Ui.smooth;
            hint();
            for (Anim a : anims.values()) { a.labelFitW = a.descFitW = a.linesW = -1; }
            GuiSounds.toggle(H2Ui.smooth);
            return true;
        }
        if (left && in(mx, my, x + 8, y + panelH - 30, SIDEBAR - 16, 20)) {
            if (model.groups().containsKey(PROFILES_TAB)) selectTab(PROFILES_TAB);
            return true;
        }
        if (left && in(mx, my, x, tabsTop(), SIDEBAR, tabsBottom() - tabsTop())) {
            int by = tabsTop() - Math.round(sideScroll);
            for (int i = 0; i < sideItems.size(); i++) {
                int h = itemH(i);
                String item = sideItems.get(i);
                if (!item.startsWith("\0") && in(mx, my, x + 8, by, SIDEBAR - 16, TAB_H)) {
                    selectTab(item);
                    return true;
                }
                by += h;
            }
            return true;
        }

        if (my >= contentTop && my < contentBottom && mx >= cx && mx < cx + cw) {
            model.stopClassicTyping();
            for (int i = 0; i < rows.size() && i < rowY.length; i++) {
                if (my < rowY[i] || my >= rowY[i] + rowH[i]) continue;
                SettingsEntry e = rows.get(i);
                if (!custom(e)) {
                    if (left) classicClick = true;
                    else classicRight = true;
                    rowsDirty = true;
                    return true;
                }
                if (left) rowClick(e, mx, my);
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private void rowClick(SettingsEntry e, double mx, double my) {
        Anim a = anim(e);
        rowsDirty = true;
        if (e instanceof MapConfigScreen.ToggleEntry te) {
            if (ConfigModel.gateReason(te) != null) return;
            boolean v = !ConfigModel.get(te);
            ConfigModel.set(te, v);
            GuiSounds.toggle(v);
        } else if (e instanceof MapConfigScreen.SliderEntry s) {
            if (in(mx, my, a.px, a.py, a.pw, a.ph)) {
                editSlider = s;
                editText = String.format("%." + ConfigModel.decimals(s) + "f", ConfigModel.get(s));
                GuiSounds.press();
            } else if (mx >= a.tx - 8 && mx <= a.tx + a.tw + 8) {
                dragging = s;
                setSlider(s, mx);
            }
        } else if (e instanceof MapConfigScreen.DropdownEntry) {
            popup = new Popup(e);
            String[] opts = popupOptions();
            String cur = ConfigModel.getString(e);
            for (int i = 0; i < opts.length; i++) if (opts[i].equals(cur)) popup.scroll = Math.max(0, (i - 3) * OPT_H);
            GuiSounds.fold(true);
        } else if (e instanceof MapConfigScreen.SoundDropdownEntry sd) {
            GuiSounds.press();
            minecraft.gui.setScreen(new SoundPickerScreen(this, a.label, ConfigModel.options(sd), ConfigModel.getString(sd),
                    v -> ConfigModel.setString(sd, v)));
        } else if (e instanceof MapConfigScreen.ColorEntry) {
            openColor(e);
            GuiSounds.fold(true);
        } else if (e instanceof MapConfigScreen.KeybindEntry k) {
            listening = k;
            listenStart = System.currentTimeMillis();
            GuiSounds.press();
        } else if (e instanceof MapConfigScreen.ButtonEntry b) {
            GuiSounds.press();
            ConfigModel.press(b);
        }
    }

    private void popupClick(double mx, double my) {
        if (popup.entry instanceof MapConfigScreen.ColorEntry) {
            int sx = popup.x + 8, sy = popup.y + 8, hx = sx + SV + 6, ax = hx + BAR + 6;
            if (in(mx, my, sx, sy, SV, SV)) popup.drag = 1;
            else if (in(mx, my, hx - 2, sy, BAR + 4, SV)) popup.drag = 2;
            else if (in(mx, my, ax - 2, sy, BAR + 4, SV)) popup.drag = 3;
            else if (in(mx, my, sx, sy + SV + 8, popup.w - 16, 18)) {
                popup.hexEdit = true;
                popup.hex = hexOf((popup.alpha << 24) | (java.awt.Color.HSBtoRGB(popup.hue, popup.sat, popup.bri) & 0xFFFFFF)).substring(1);
                GuiSounds.press();
            }
            return;
        }
        String[] opts = popupOptions();
        int i = (int) ((my - popup.y - 4 + popup.scroll) / OPT_H);
        if (i >= 0 && i < opts.length && my >= popup.y + 4 && my < popup.y + popup.h - 4) {
            ConfigModel.setString(popup.entry, opts[i]);
            GuiSounds.select();
            popup = null;
            rowsDirty = true;
        }
    }

    private void commitSlider() {
        try {
            ConfigModel.set(editSlider, Float.parseFloat(editText.trim()));
        } catch (NumberFormatException ignored) {
        }
        editSlider = null;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) { dragging = null; return true; }
        if (popup != null && popup.drag != 0) { popup.drag = 0; return true; }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (popup != null) {
            if (!(popup.entry instanceof MapConfigScreen.ColorEntry) && in(mx, my, popup.x, popup.y, popup.w, popup.h)) {
                popup.scroll -= (float) v * OPT_H;
                return true;
            }
            popup = null;
        }
        if (mx < px() + SIDEBAR) {
            sideScrollTarget = Math.max(0, Math.min(sideMax, sideScrollTarget - (float) v * 26f));
            return true;
        }
        if (my >= contentTop && my < contentBottom) {
            for (int i = 0; i < rows.size() && i < rowY.length; i++) {
                SettingsEntry e = rows.get(i);
                if (custom(e) || my < rowY[i] || my >= rowY[i] + rowH[i]) continue;
                if (e.scrollAt((int) mx, (int) my, rowY[i] + 4, v)) return true;
            }
        }
        scrollTarget -= (float) v * 26f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        boolean enter = k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER;
        if (listening != null) {
            ConfigModel.setKey(listening, k == InputConstants.KEY_ESCAPE ? -1 : k);
            listening = null;
            GuiSounds.select();
            return true;
        }
        if (editSlider != null) {
            if (enter) commitSlider();
            else if (k == InputConstants.KEY_ESCAPE) editSlider = null;
            else if (k == InputConstants.KEY_BACKSPACE && !editText.isEmpty()) editText = editText.substring(0, editText.length() - 1);
            return true;
        }
        if (popup != null && popup.hexEdit) {
            if (enter) commitHex();
            else if (k == InputConstants.KEY_ESCAPE) popup.hexEdit = false;
            else if (k == InputConstants.KEY_BACKSPACE && !popup.hex.isEmpty()) popup.hex = popup.hex.substring(0, popup.hex.length() - 1);
            return true;
        }
        if (popup != null && k == InputConstants.KEY_ESCAPE) { popup = null; return true; }
        if (model.classicTyping()) { model.classicKey(event); rowsDirty = true; return true; }
        if (search.isFocused() && k == InputConstants.KEY_ESCAPE && !search.getValue().isEmpty()) {
            search.setValue("");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (editSlider != null) {
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') editText += c;
            return true;
        }
        if (popup != null && popup.hexEdit) {
            c = Character.toUpperCase(c);
            if (((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) && popup.hex.length() < 8) popup.hex += c;
            return true;
        }
        if (model.classicTyping()) { model.classicChar(event); rowsDirty = true; return true; }
        if (!search.isFocused() && c > ' ') {
            setFocused(search);
            search.setFocused(true);
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (editSlider != null) commitSlider();
        TeslaMapsConfig.save();
        GuiSounds.close();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
