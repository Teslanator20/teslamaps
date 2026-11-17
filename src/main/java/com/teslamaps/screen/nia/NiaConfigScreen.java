package com.teslamaps.screen.nia;

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.screen.HudEditScreen;
import com.teslamaps.screen.KeybindMessageScreen;
import com.teslamaps.screen.MapConfigScreen;
import com.teslamaps.screen.ScreenFit;
import com.teslamaps.screen.ShortcutScreen;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Ported from NiaMod's gui/screen/ConfigScreen.java
public class NiaConfigScreen extends Screen {
    static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    private static final int SIDEBAR_W = 124;
    private static final int ROUND = 12;
    private static final int MODULE_GAP = 7;
    private static final int SEARCH_BAR_HEIGHT = 24;
    private static final int MIN_PANEL_W = 320;
    private static final int MIN_PANEL_H = 260;
    private static final int MAX_PANEL_W = 1000;
    private static final int MAX_PANEL_H = 800;
    private static final int SCREEN_MARGIN = 12;
    private static final int TAB_H = 24;
    private static final int TAB_STRIDE = 30;
    private static final int CAPTION_H = 18;
    private static final int TABS_TOP = 18;
    private static final String SEARCH = "Search";
    private static final long DESC_DELAY_MS = 500;

    private String fontName;
    public static final String[] THEMES = NiaTheme.NAMES, FONTS = NiaTheme.FONTS;

    private final Screen parent;
    final NiaModel model;
    private int seenBuild = -1;

    private final Animation scaleAnim = new Animation(Animation.Easing.EASE_OUT_EXPO, 300);
    private final Animation opacityAnim = new Animation(Animation.Easing.EASE_OUT_EXPO, 300);
    private boolean closing;
    private double animTime;

    private int panelW, panelH, moduleW;
    private float panelX, panelY;
    private boolean dragging;
    private double dragOffX, dragOffY;
    private int resizeEdge;
    private float resizeStartW, resizeStartH;

    private final List<String> sideItems = new ArrayList<>();
    private final List<Boolean> sideCaption = new ArrayList<>();
    private final Map<String, Animation> tabAnims = new HashMap<>();
    private double sidebarScroll, sidebarScrollTarget;
    private int sidebarMax;
    private String selected = "Profiles & Interface";
    private boolean searchMode;

    private final Map<String, List<NiaSection>> catCards = new LinkedHashMap<>();
    private List<NiaSection> activeComps = new ArrayList<>();
    private final List<NiaSection> searchResults = new ArrayList<>();
    boolean sectionsDirty;
    private EditBox searchBox;
    private String searchQuery = "";
    private static String lastSearch = "";
    private double scroll, scrollTarget;
    private long lastFrameTime;

    private EditBox editor;
    private NiaRow editTarget;
    private boolean editorPlaced;
    private Consumer<String> editListener;

    boolean frameClicked, frameMouseDown;
    private boolean wasDown, wasRightDown;
    NiaRow hoverRow;
    private NiaRow descRow;
    private long descStart;
    private String tipText, tipFor;
    private List<FormattedCharSequence> tipLines;
    private long resetArmedAt;
    private boolean contentHovered;
    private String jumpTab;

    public static Screen create(Screen parent) {
        return new NiaConfigScreen(parent, MapConfigScreen.View.OLD);
    }

    public static Screen create(Screen parent, MapConfigScreen.View view) {
        return new NiaConfigScreen(parent, view);
    }

    public static Screen withSearch(MapConfigScreen.View view, String query) {
        lastSearch = query == null ? "" : query;
        return new NiaConfigScreen(null, view);
    }

    public boolean isBindingKey() {
        return model.isBindingKey() || editTarget != null;
    }

    /** Rows rebuilt from the config, e.g. after a backup restore. */
    public void rebuild() {
        rebuildModel();
    }

    private NiaConfigScreen(Screen parent, MapConfigScreen.View view) {
        super(Component.literal("TeslaMaps"));
        this.parent = parent;
        this.model = new NiaModel(view);
    }

    private NiaTheme theme() {
        double mul = animTime < 1.0 ? opacityAnim.getValue() : 1.0;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return NiaTheme.base(c).configured(c.niaOpacity, mul);
    }

    @Override
    protected void init() {
        fontName = TeslaMapsConfig.get().niaFont;
        NiaRender.style = NiaRender.fontStyle(fontName);
        if (seenBuild < 0) model.init(width, height);
        setPanelSize(TeslaMapsConfig.get().niaPanelW, TeslaMapsConfig.get().niaPanelH);
        panelX = (width - panelW) / 2.0f;
        panelY = (height - panelH) / 2.0f;
        closing = false;
        resizeEdge = 0;

        searchBox = new EditBox(font, 0, 0, Math.max(10, moduleW - 58), 9, Component.literal("Search"));
        searchBox.setBordered(false);
        NiaRender.applyFont(searchBox);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setHint(NiaRender.styled("Search..."));
        searchBox.setResponder(q -> {
            searchQuery = q;
            lastSearch = q;
            updateSearch();
        });
        searchBox.visible = false;
        addRenderableWidget(searchBox);

        editor = new EditBox(font, 0, 0, 30, 9, Component.literal("Value"));
        editor.setBordered(false);
        NiaRender.applyFont(editor);
        editor.setTextColor(0xFFFFFFFF);
        editor.setResponder(v -> { if (editListener != null) editListener.accept(v); });
        editor.visible = false;
        addRenderableWidget(editor);
        editTarget = null;
        searchBox.setValue(lastSearch);
        String[] jump = NiaModel.takeJump();
        if (jump != null) {
            String g = model.sidebarItemOf(jump[0]);
            if (g != null) jumpTab = g;
            String q = jump[1] == null ? "" : jump[1].toLowerCase();
            boolean hits = false;
            if (!q.isEmpty()) {
                for (List<MapConfigScreen.SettingsEntry> l : model.categories().values()) {
                    for (MapConfigScreen.SettingsEntry e : l) if (!e.isLabel() && hit(e, q)) { hits = true; break; }
                    if (hits) break;
                }
            }
            searchBox.setValue(hits ? jump[1] : "");
        }

        scaleAnim.setEasing(Animation.Easing.EASE_OUT_EXPO);
        scaleAnim.setDuration(300);
        scaleAnim.setValue(0);
        scaleAnim.run(1);
        opacityAnim.setEasing(Animation.Easing.EASE_OUT_EXPO);
        opacityAnim.setDuration(300);
        opacityAnim.setValue(0);
        opacityAnim.run(1);
        lastFrameTime = System.currentTimeMillis();

        ScreenFit.clamp(this);
    }

    // ---------------------------------------------------------------- model -> cards

    void rebuildModel() {
        model.init(width, height);
    }

    private void rebuildFromModel() {
        boolean first = seenBuild < 0;
        seenBuild = model.builds;
        catCards.clear();
        sideItems.clear();
        sideCaption.clear();
        for (Map.Entry<String, List<String>> sec : model.sections().entrySet()) {
            sideItems.add(sec.getKey().toUpperCase());
            sideCaption.add(true);
            for (String item : sec.getValue()) {
                sideItems.add(item);
                sideCaption.add(false);
            }
        }
        if (first) {
            String remembered = model.view() == MapConfigScreen.View.OLD ? TeslaMapsConfig.get().lastConfigCategory : null;
            String g = model.sidebarItemOf(remembered);
            if (g != null) selected = g;
            if (jumpTab != null) selected = jumpTab;
            if (NiaModel.newMode()) selected = NiaModel.NEW_OPTIONS;
            jumpTab = null;
        }
        if (!model.groups().containsKey(selected) && !SEARCH.equals(selected)) selected = firstItem();
        if (!searchQuery.isEmpty()) {
            selected = SEARCH;
        }
        selectTab(selected, false);
    }

    private String firstItem() {
        if (model.groups().containsKey("Profiles & Interface")) return "Profiles & Interface";
        for (int i = 0; i < sideItems.size(); i++) {
            if (!sideCaption.get(i) && !NiaModel.TURNED_ON.equals(sideItems.get(i))) return sideItems.get(i);
        }
        return NiaModel.TURNED_ON;
    }

    private List<NiaSection> cardsOf(String cat) {
        List<NiaSection> cached = catCards.get(cat);
        if (cached != null) return cached;
        List<NiaSection> out = new ArrayList<>();
        List<MapConfigScreen.SettingsEntry> entries = model.categories().get(cat);
        if (entries != null) {
            int start = 0;
            MapConfigScreen.ToggleEntry master = null;
            if (!entries.isEmpty() && entries.get(0) instanceof MapConfigScreen.ToggleEntry t && t.getLabel().equals("Enable " + cat)) {
                master = t;
                start = 1;
            }
            NiaSection cur = new NiaSection(this, cat + "#head", cat, null, master, false);
            int ord = 0;
            for (int i = start; i < entries.size(); i++) {
                MapConfigScreen.SettingsEntry e = entries.get(i);
                if (e.isLabel()) {
                    if (!cur.rows.isEmpty() || cur.master != null) out.add(cur);
                    cur = new NiaSection(this, cat + "#" + (ord++), e.getLabel().trim(), e.description(), null, false);
                } else {
                    cur.rows.add(NiaRow.of(this, e));
                }
            }
            if (!cur.rows.isEmpty() || cur.master != null) out.add(cur);
        }
        for (NiaSection sec : out) sec.syncStateImmediately(font);
        catCards.put(cat, out);
        return out;
    }

    private void selectTab(String tab, boolean resetScroll) {
        endEdit();
        selected = tab;
        searchMode = SEARCH.equals(tab);
        if (resetScroll) scroll = scrollTarget = 0;
        if (searchMode) {
            searchBox.visible = true;
            searchBox.active = true;
            setFocused(searchBox);
            searchBox.setFocused(true);
            updateSearch();
            activeComps = searchResults;
            return;
        }
        searchBox.visible = false;
        searchBox.setFocused(false);
        if (getFocused() == searchBox) setFocused(null);
        if (model.view() == MapConfigScreen.View.OLD && !NiaModel.TURNED_ON.equals(tab) && !NiaModel.NEW_OPTIONS.equals(tab)) {
            TeslaMapsConfig.get().lastConfigCategory = tab;
        }
        scrollSidebarTo(tab);
        rebuildActive();
    }

    private void rebuildActive() {
        sectionsDirty = false;
        if (searchMode) return;
        List<NiaSection> list = new ArrayList<>();
        if (NiaModel.TURNED_ON.equals(selected)) {
            for (String cat : model.categories().keySet()) {
                List<MapConfigScreen.SettingsEntry> on = model.turnedOn(cat);
                if (on.isEmpty()) continue;
                NiaSection sec = new NiaSection(this, "on#" + cat, cat, null, null, true);
                for (MapConfigScreen.SettingsEntry e : on) sec.rows.add(NiaRow.of(this, e));
                sec.syncStateImmediately(font);
                list.add(sec);
            }
            activeComps = list;
            return;
        }
        if (NiaModel.NEW_OPTIONS.equals(selected)) {
            NiaSection sec = null;
            for (MapConfigScreen.SettingsEntry e : model.newRows()) {
                if (e.isLabel()) {
                    if (sec != null && !sec.rows.isEmpty()) list.add(sec);
                    sec = new NiaSection(this, "new#" + e.getLabel(), e.getLabel().trim(), null, null, true);
                } else {
                    if (sec == null) sec = new NiaSection(this, "new#", NiaModel.NEW_OPTIONS, null, null, true);
                    sec.rows.add(NiaRow.of(this, e));
                }
            }
            if (sec != null && !sec.rows.isEmpty()) list.add(sec);
            for (NiaSection s : list) s.syncStateImmediately(font);
            activeComps = list;
            return;
        }
        List<String> cats = model.groups().getOrDefault(selected, List.of(selected));
        for (String cat : cats) {
            List<NiaSection> cards = cardsOf(cat);
            if (cards.isEmpty()) continue;
            NiaSection first = cards.getFirst();
            if (first.master != null && !first.master.value()) {
                list.add(first);
                continue;
            }
            list.addAll(cards);
        }
        if (list.size() == 1 && !list.getFirst().isExpanded()) {
            list.getFirst().setExpanded(true);
            list.getFirst().syncStateImmediately(font);
        }
        activeComps = list;
    }

    private void updateSearch() {
        endEdit();
        searchResults.clear();
        String q = searchQuery.trim().toLowerCase();
        if (!q.isEmpty()) {
            for (Map.Entry<String, List<MapConfigScreen.SettingsEntry>> c : model.categories().entrySet()) {
                String cat = c.getKey();
                NiaSection cur = new NiaSection(this, "", cat, null, null, true);
                boolean carry = false, groupHit = false;
                for (MapConfigScreen.SettingsEntry e : c.getValue()) {
                    if (e.isLabel()) {
                        flushSearch(cur);
                        cur = new NiaSection(this, "", e.getLabel().trim(), cat, null, true);
                        carry = false;
                        groupHit = hit(e, q);
                        continue;
                    }
                    boolean child = e.getLabel().startsWith(" ");
                    if (groupHit || hit(e, q)) {
                        cur.rows.add(NiaRow.of(this, e));
                        carry = !child;
                    } else if (child && carry) {
                        cur.rows.add(NiaRow.of(this, e));
                    } else if (!child) {
                        carry = false;
                    }
                }
                flushSearch(cur);
            }
        }
        if (searchMode) {
            activeComps = searchResults;
            scroll = scrollTarget = 0;
        }
    }

    private void flushSearch(NiaSection sec) {
        if (sec.rows.isEmpty()) return;
        sec.syncStateImmediately(font);
        searchResults.add(sec);
    }

    private static boolean hit(MapConfigScreen.SettingsEntry e, String q) {
        return e.matchesSearch(q) || e.matchesMeta(q);
    }

    // ---------------------------------------------------------------- shared inline editor

    boolean editing(NiaRow row) {
        return editTarget == row;
    }

    void beginEdit(NiaRow row, String value, int maxLength, Consumer<String> listener) {
        endEdit();
        editTarget = row;
        editListener = null;
        editor.setMaxLength(maxLength);
        editor.setValue(value);
        editor.moveCursorToEnd(false);
        editor.setHighlightPos(0);
        editListener = listener;
        editor.visible = true;
        editor.active = true;
        setFocused(editor);
        editor.setFocused(true);
        GuiSounds.press();
    }

    void placeEditor(int x, int y, int w, int h) {
        editor.setX(x);
        editor.setY(y + (h - 8) / 2);
        editor.setWidth(Math.max(4, w));
        editorPlaced = true;
    }

    void endEdit() {
        if (editTarget == null) return;
        editTarget = null;
        editListener = null;
        editor.visible = false;
        editor.setFocused(false);
        if (getFocused() == editor) setFocused(null);
        TeslaMapsConfig.save();
    }

    void tip(String text) {
        tipText = text;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        NiaRender.resetClips();
        String f = TeslaMapsConfig.get().niaFont;
        if (f != fontName) {
            fontName = f;
            NiaRender.style = NiaRender.fontStyle(f);
            for (NiaSection sec : activeComps) sec.syncStateImmediately(font);
        }
        if (model.builds != seenBuild) rebuildFromModel();
        if (sectionsDirty) rebuildActive();

        boolean down = com.teslamaps.utils.MouseState.left();
        boolean rightDown = com.teslamaps.utils.MouseState.right();
        frameClicked = down && !wasDown && !dragging && resizeEdge == 0;
        frameMouseDown = down && !dragging && resizeEdge == 0;
        wasDown = down;
        model.beginFrame(rightDown && !wasRightDown);
        wasRightDown = rightDown;

        moduleW = moduleWidth();
        long now = System.currentTimeMillis();
        updateScroll(now - lastFrameTime);
        lastFrameTime = now;

        if (closing) {
            scaleAnim.setEasing(Animation.Easing.LINEAR);
            scaleAnim.setDuration(100);
            scaleAnim.run(0);
            opacityAnim.setEasing(Animation.Easing.LINEAR);
            opacityAnim.setDuration(100);
            opacityAnim.run(0);
        }
        animTime = scaleAnim.getValue();
        if (closing && scaleAnim.isFinished()) {
            minecraft.gui.setScreen(parent);
            return;
        }
        if (animTime <= 0) return;

        NiaTheme theme = theme();
        int px = Math.round(panelX), py = Math.round(panelY);
        g.pose().pushMatrix();
        if (animTime != 1) {
            double cx = px + panelW / 2.0, cy = py + panelH / 2.0;
            g.pose().translate((float) (cx * (1 - animTime)), (float) (cy * (1 - animTime)));
            g.pose().scale((float) animTime, (float) animTime);
        }
        renderPanelFrame(g, mouseX, mouseY, dt, px, py, theme);
        g.pose().popMatrix();

        String tip = tipText;
        tipText = null;
        if (tip == null && hoverRow != null && hoverRow == descRow && !down
                && now - descStart >= DESC_DELAY_MS) {
            tip = hoverRow.entry.description();
        }
        if (hoverRow != descRow) {
            descRow = hoverRow;
            descStart = now;
        }
        if (tip != null && !closing) {
            if (!tip.equals(tipFor)) {
                tipFor = tip;
                tipLines = font.split(Component.literal(tip), 220);
            }
            g.setTooltipForNextFrame(font, tipLines, mouseX, mouseY);
        }
    }

    private void renderPanelFrame(GuiGraphicsExtractor g, int mx, int my, float dt, int px, int py, NiaTheme theme) {
        NiaRender.dropShadow(g, px, py, panelW, panelH, 6, 0x26000000, ROUND);
        NiaRender.roundedRect(g, px, py, panelW, panelH, ROUND, theme.background());
        NiaRender.roundedRect(g, px, py, SIDEBAR_W, panelH, ROUND, theme.secondary());
        g.nextStratum();

        NiaRender.pushClip(g, px + 1, py + 1, px + panelW - 1, py + panelH - 1);
        int contentX = px + SIDEBAR_W + 8;
        int contentW = panelW - SIDEBAR_W - 12;
        int viewH = panelH - 14;
        int maxScroll = maxScroll(viewH);
        clampScroll(maxScroll);
        hoverRow = null;
        editorPlaced = false;
        renderContentArea(g, px, py, contentX, contentW, mx, my, theme);
        if (editTarget != null && !editorPlaced) endEdit();

        super.extractRenderState(g, mx, my, dt);

        renderSidebar(g, px, py, mx, my, theme);
        renderScrollbar(g, px + panelW - 4, py + 7 + (searchMode ? 35 : 0), viewH - (searchMode ? 35 : 0), maxScroll, theme);
        NiaRender.popClip(g);
    }

    private void renderContentArea(GuiGraphicsExtractor g, int px, int py, int contentX, int contentW, int mx, int my, NiaTheme theme) {
        int top = searchMode ? py + 42 : py + 7;
        if (searchMode) renderSearchBar(g, contentX, py + 10, mx, my, theme);
        contentHovered = mx >= contentX && mx < px + panelW - 4 && my >= top && my < py + panelH - 1;
        NiaRender.pushClip(g, contentX, top, px + panelW - 4, py + panelH - 1);
        renderModules(g, contentX, top, contentW, top, py + panelH - 1, mx, my, theme);
        if (activeComps.isEmpty()) {
            String msg = searchMode ? (searchQuery.isBlank() ? "Type to search every option" : "No results found.")
                    : NiaModel.TURNED_ON.equals(selected) ? "Nothing turned on yet"
                    : NiaModel.NEW_OPTIONS.equals(selected) ? "No new options" : "";
            if (!msg.isEmpty()) {
                int w = NiaRender.width(font, msg);
                NiaRender.text(g, font, msg, contentX + (moduleW - w) / 2, top + 20, theme.trinaryText());
            }
        }
        NiaRender.popClip(g);
    }

    private void renderModules(GuiGraphicsExtractor g, int x, int y, int w, int viewTop, int viewBottom, int mx, int my, NiaTheme theme) {
        double moduleY = y + scroll;
        int width = Math.min(moduleW, w);
        for (NiaSection sec : activeComps) {
            int sy = (int) Math.round(moduleY);
            sec.setPosition(x, sy, width);
            sec.setViewportClip(viewTop, viewBottom);
            if (sy + sec.getHeight() > viewTop && sy < viewBottom) sec.render(g, font, mx, my, theme);
            else sec.update(font);
            moduleY += sec.getHeight() + MODULE_GAP;
        }
    }

    private void renderSearchBar(GuiGraphicsExtractor g, int x, int y, int mx, int my, NiaTheme theme) {
        boolean focused = searchBox.isFocused();
        boolean hovered = mx >= x && mx <= x + moduleW && my >= y && my <= y + SEARCH_BAR_HEIGHT;
        int fill = NiaRender.withAlpha(theme.secondary(), focused ? 245 : hovered ? 228 : 214);
        int border = focused ? NiaRender.withAlpha(theme.accentColor(), 105) : 0x20FFFFFF;
        NiaRender.surface(g, x, y, moduleW, SEARCH_BAR_HEIGHT, 8, fill, border);
        NiaRender.circle(g, x + 12, y + SEARCH_BAR_HEIGHT / 2f, 5, focused ? NiaRender.withAlpha(theme.accentColor(), 220) : 0x66FFFFFF);
        searchBox.setX(x + 22);
        searchBox.setY(y + (SEARCH_BAR_HEIGHT - 8) / 2);
        searchBox.setWidth(Math.max(10, moduleW - 58));
        searchBox.visible = true;
        searchBox.active = true;
        String count = Integer.toString(searchResults.size());
        NiaRender.text(g, font, count, x + moduleW - NiaRender.width(font, count) - 10, y + 8, 0x96FFFFFF);
    }

    private void renderSidebar(GuiGraphicsExtractor g, int px, int py, int mx, int my, NiaTheme theme) {
        g.fill(px + SIDEBAR_W - 1, py + 10, px + SIDEBAR_W, py + panelH - 10, 0x18FFFFFF);
        int bx = px + 10, bw = SIDEBAR_W - 20;
        renderTab(g, SEARCH, bx, py + TABS_TOP, bw, mx, my, theme, true);

        int listTop = sidebarListTop(py), listBottom = sidebarListBottom(py);
        NiaRender.pushClip(g, px, listTop, px + SIDEBAR_W, listBottom);
        int yy = listTop - (int) Math.round(sidebarScroll);
        for (int i = 0; i < sideItems.size(); i++) {
            String item = sideItems.get(i);
            if (sideCaption.get(i)) {
                if (yy + CAPTION_H > listTop && yy < listBottom) {
                    NiaRender.text(g, font, item, bx + 4, yy + 6, theme.trinaryText());
                }
                yy += CAPTION_H;
            } else {
                if (yy + TAB_H > listTop && yy < listBottom) {
                    boolean inside = my >= listTop && my < listBottom;
                    renderTab(g, item, bx, yy, bw, mx, inside ? my : Integer.MIN_VALUE, theme, false);
                }
                yy += TAB_STRIDE;
            }
        }
        NiaRender.popClip(g);
        int contentH = yy + (int) Math.round(sidebarScroll) - listTop;
        int sideBg = NiaRender.withAlpha(theme.secondary(), 255);
        if (sidebarScroll > 0.5) g.fillGradient(px + 1, listTop, px + SIDEBAR_W - 1, listTop + 10, sideBg, sideBg & 0x00FFFFFF);
        if (sidebarScroll < sidebarMax - 0.5) g.fillGradient(px + 1, listBottom - 12, px + SIDEBAR_W - 1, listBottom, sideBg & 0x00FFFFFF, sideBg);
        sidebarMax = Math.max(0, contentH - (listBottom - listTop));
        if (sidebarScrollTarget > sidebarMax) sidebarScrollTarget = sidebarMax;
        if (sidebarMax > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(15, Math.round(trackH * (float) trackH / contentH));
            int thumbY = listTop + Math.round((trackH - thumbH) * (float) (sidebarScroll / sidebarMax));
            g.fill(px + SIDEBAR_W - 4, thumbY, px + SIDEBAR_W - 3, thumbY + thumbH, theme.scrollbarColor());
        }

        String[] actions = actionLabels();
        for (int i = 0; i < actions.length; i++) {
            renderSidebarActionButton(g, actions[i], bx, sidebarActionY(py, i), bw, mx, my, theme);
        }
    }

    private void renderTab(GuiGraphicsExtractor g, String tab, int bx, int by, int bw, int mx, int my, NiaTheme theme, boolean fixed) {
        boolean sel = tab.equals(selected);
        boolean hovered = mx >= bx && mx <= bx + bw && my >= by && my <= by + TAB_H;
        Animation a = tabAnims.get(tab);
        if (a == null) {
            a = new Animation(Animation.Easing.LINEAR, 200);
            a.setValue(sel ? 255 : 0);
            tabAnims.put(tab, a);
        }
        a.run(sel ? 255 : 0);
        double sa = a.getValue();
        if (hovered) NiaRender.roundedRect(g, bx, by, bw, TAB_H, 7, NiaRender.withAlpha(0xFFFFFF, sel ? 24 : 10));
        if (sel) {
            NiaRender.roundedRect(g, bx, by, bw, TAB_H, 7, NiaRender.withAlpha(theme.accentColor(), 32));
            NiaRender.roundedRect(g, bx + 7, by + 10, 4, 4, 2, theme.accentColor());
        }
        int textColor = sel ? 0xFFFFFFFF : 0xC8FFFFFF;
        float xOff = sel ? (float) (sa / 140.0) : 0f;
        int tx = (int) (bx + 16 + xOff);
        int avail = bw - 20;
        int w = NiaRender.width(font, tab);
        if (w <= avail) {
            NiaRender.text(g, font, tab, tx, by + 8, textColor);
        } else {
            float scale = avail / (float) w;
            g.pose().pushMatrix();
            g.pose().translate(tx, by + 8 + font.lineHeight * (1 - scale) / 2f);
            g.pose().scale(scale, scale);
            NiaRender.text(g, font, tab, 0, 0, textColor);
            g.pose().popMatrix();
        }
    }

    private static final String[] ACTIONS = {"HUD Editor", "Hotkeys", "Shortcuts"};
    private static final String[] ACTIONS_RESET = {"Reset Defaults", "HUD Editor", "Hotkeys", "Shortcuts"};
    private static final String[] ACTIONS_ARMED = {"Click to confirm", "HUD Editor", "Hotkeys", "Shortcuts"};

    private String[] actionLabels() {
        if (TeslaMapsConfig.get().configResetOptions && !searchMode && !NiaModel.TURNED_ON.equals(selected)
                && !NiaModel.NEW_OPTIONS.equals(selected)) {
            return System.currentTimeMillis() - resetArmedAt < 3000 ? ACTIONS_ARMED : ACTIONS_RESET;
        }
        return ACTIONS;
    }

    private void renderSidebarActionButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int mx, int my, NiaTheme theme) {
        int h = actionH();
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int fill = NiaRender.withAlpha(theme.secondary(), hovered ? 242 : 224);
        int border = NiaRender.withAlpha(0xFFFFFF, hovered ? 56 : 26);
        NiaRender.surface(g, x, y, w, h, 7, fill, border);
        int tw = NiaRender.width(font, label);
        NiaRender.text(g, font, label, x + (w - tw) / 2, y + (h - font.lineHeight) / 2 + 1, hovered ? 0xFFFFFFFF : 0xD6FFFFFF);
    }

    /** Short panels get slimmer action buttons so the tab list keeps some room. */
    private int actionH() {
        return panelH < 330 ? 18 : TAB_H;
    }

    private int sidebarActionY(int py, int indexFromBottom) {
        int h = actionH();
        return py + panelH - 11 - h - indexFromBottom * (h + 6);
    }

    private int sidebarListTop(int py) {
        return py + TABS_TOP + TAB_STRIDE;
    }

    private int sidebarListBottom(int py) {
        return sidebarActionY(py, actionLabels().length - 1) - 6;
    }

    private void renderScrollbar(GuiGraphicsExtractor g, int sx, int sy, int sh, int maxScroll, NiaTheme theme) {
        if (maxScroll <= 0) return;
        int thumbH = Math.max(15, Math.round(sh * (float) sh / (sh + maxScroll)));
        float progress = (float) (-scroll / maxScroll);
        int thumbY = sy + Math.round((sh - thumbH) * progress);
        g.fill(sx, thumbY, sx + 1, thumbY + thumbH, theme.scrollbarColor());
    }

    private int totalHeight() {
        int total = 0;
        for (int i = 0; i < activeComps.size(); i++) {
            total += activeComps.get(i).getHeight();
            if (i < activeComps.size() - 1) total += MODULE_GAP;
        }
        return total;
    }

    private int maxScroll(int viewH) {
        return Math.max(0, totalHeight() - viewH + (searchMode ? 42 : 7));
    }

    private void updateScroll(long frameDt) {
        double lerp = Math.min(1.0 - Math.pow(0.005, frameDt / 1000.0), 0.6);
        scroll += (scrollTarget - scroll) * lerp;
        if (Math.abs(scrollTarget - scroll) < 0.3) scroll = scrollTarget;
        sidebarScroll += (sidebarScrollTarget - sidebarScroll) * lerp;
        if (Math.abs(sidebarScrollTarget - sidebarScroll) < 0.3) sidebarScroll = sidebarScrollTarget;
    }

    private void clampScroll(int maxScroll) {
        scrollTarget = Math.clamp(scrollTarget, -maxScroll, 0);
        scroll = Math.clamp(scroll, -maxScroll, 0);
    }

    private void setPanelSize(int w, int h) {
        int vw = Math.max(1, width - SCREEN_MARGIN * 2), vh = Math.max(1, height - SCREEN_MARGIN * 2);
        int minW = Math.min(MIN_PANEL_W, vw), minH = Math.min(MIN_PANEL_H, vh);
        panelW = Math.clamp(w, minW, Math.clamp(vw, minW, MAX_PANEL_W));
        panelH = Math.clamp(h, minH, Math.clamp(vh, minH, MAX_PANEL_H));
        moduleW = moduleWidth();
    }

    private int moduleWidth() {
        return Math.max(1, panelW - SIDEBAR_W - 17);
    }

    private void updateDragPosition(double mx, double my) {
        panelX = Math.clamp((float) (mx + dragOffX), -panelW + 50, width - 50);
        panelY = Math.clamp((float) (my + dragOffY), 0, height - 30);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (closing) return true;
        if (model.listeningAny()) {
            model.mouseClicked(click, doubled);
            return true;
        }
        double mx = click.x(), my = click.y();
        int btn = click.button();
        int px = Math.round(panelX), py = Math.round(panelY);

        if (editTarget != null && !editor.isMouseOver(mx, my)) endEdit();
        if (editTarget != null) return super.mouseClicked(click, doubled);

        if (searchMode && mx >= px + SIDEBAR_W + 8 && mx <= px + SIDEBAR_W + 8 + moduleW && my >= py + 10 && my <= py + 10 + SEARCH_BAR_HEIGHT) {
            if (btn == RIGHT) {
                searchBox.setValue("");
            }
            setFocused(searchBox);
            searchBox.setFocused(true);
            if (searchBox.isMouseOver(mx, my)) super.mouseClicked(click, doubled);
            return true;
        }
        if (searchBox.isFocused()) {
            searchBox.setFocused(false);
            setFocused(null);
        }

        if (startResizeIfNearEdge(mx, my, px, py)) return true;
        if (btn == LEFT && handleSidebarActionClick(mx, my, px, py)) return true;
        if (btn == LEFT && selectClickedTab(mx, my, px, py)) return true;
        if (btn == LEFT && startPanelDrag(mx, my, px, py)) return true;

        if (btn == RIGHT && hoverRow != null && TeslaMapsConfig.get().configResetOptions
                && hoverRow.entry.readValue() != null && !(hoverRow instanceof NiaRow.Delegate)) {
            if (model.reset(List.of(hoverRow.entry)) > 0) GuiSounds.press();
            return true;
        }
        for (NiaSection sec : activeComps) {
            if (sec.mouseClicked(mx, my, btn)) return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean startResizeIfNearEdge(double mx, double my, int left, int top) {
        int margin = 6;
        boolean right = mx >= left + panelW - margin && mx <= left + panelW + margin;
        boolean bottom = my >= top + panelH - margin && my <= top + panelH + margin;
        if ((!right && !bottom) || mx < left || my < top || mx > left + panelW + margin || my > top + panelH + margin) return false;
        resizeEdge = (right ? 1 : 0) | (bottom ? 2 : 0);
        dragging = false;
        resizeStartW = panelW;
        resizeStartH = panelH;
        dragOffX = mx;
        dragOffY = my;
        return true;
    }

    private boolean handleSidebarActionClick(double mx, double my, int px, int py) {
        int x = px + 10, w = SIDEBAR_W - 20;
        String[] actions = actionLabels();
        for (int i = 0; i < actions.length; i++) {
            int y = sidebarActionY(py, i);
            if (mx < x || mx > x + w || my < y || my > y + actionH()) continue;
            GuiSounds.press();
            switch (actions[i]) {
                case "HUD Editor" -> minecraft.gui.setScreen(new HudEditScreen(this));
                case "Hotkeys" -> minecraft.gui.setScreen(new KeybindMessageScreen());
                case "Shortcuts" -> minecraft.gui.setScreen(new ShortcutScreen());
                case "Reset Defaults" -> resetArmedAt = System.currentTimeMillis();
                default -> resetSelected();
            }
            return true;
        }
        return false;
    }

    private void resetSelected() {
        resetArmedAt = 0;
        List<MapConfigScreen.SettingsEntry> all = new ArrayList<>();
        for (String cat : model.groups().getOrDefault(selected, List.of(selected))) {
            List<MapConfigScreen.SettingsEntry> l = model.categories().get(cat);
            if (l != null) all.addAll(l);
        }
        int n = model.reset(all);
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + selected + ": " + n + " option(s) reset"));
        }
    }

    private boolean selectClickedTab(double mx, double my, int px, int py) {
        int bx = px + 10, bw = SIDEBAR_W - 20;
        if (mx < bx || mx > bx + bw) return false;
        if (my >= py + TABS_TOP && my <= py + TABS_TOP + TAB_H) {
            if (!searchMode) GuiSounds.select();
            selectTab(SEARCH, true);
            return true;
        }
        int listTop = sidebarListTop(py), listBottom = sidebarListBottom(py);
        if (my < listTop || my >= listBottom) return false;
        int yy = listTop - (int) Math.round(sidebarScroll);
        for (int i = 0; i < sideItems.size(); i++) {
            if (sideCaption.get(i)) {
                yy += CAPTION_H;
                continue;
            }
            if (my >= yy && my <= yy + TAB_H) {
                String item = sideItems.get(i);
                if (!item.equals(selected)) GuiSounds.select();
                selectTab(item, true);
                return true;
            }
            yy += TAB_STRIDE;
        }
        return false;
    }

    private boolean startPanelDrag(double mx, double my, int px, int py) {
        boolean top = mx >= px && mx <= px + panelW && my >= py && my <= py + 8;
        boolean side = mx >= px && mx <= px + SIDEBAR_W && my >= py && my <= py + panelH;
        if (!top && !side) return false;
        dragging = true;
        dragOffX = panelX - mx;
        dragOffY = panelY - my;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (closing) return true;
        if (resizeEdge != 0) {
            int w = panelW, h = panelH;
            if ((resizeEdge & 1) != 0) w = (int) (resizeStartW + (event.x() - dragOffX));
            if ((resizeEdge & 2) != 0) h = (int) (resizeStartH + (event.y() - dragOffY));
            setPanelSize(w, h);
            return true;
        }
        if (dragging) {
            updateDragPosition(event.x(), event.y());
            return true;
        }
        for (NiaSection sec : activeComps) {
            if (sec.mouseDragged(event.x(), event.y())) return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (closing) return true;
        if (resizeEdge != 0) {
            resizeEdge = 0;
            TeslaMapsConfig.get().niaPanelW = panelW;
            TeslaMapsConfig.get().niaPanelH = panelH;
            TeslaMapsConfig.save();
            return true;
        }
        if (dragging) {
            dragging = false;
            return true;
        }
        boolean any = false;
        for (NiaSection sec : activeComps) any |= sec.mouseReleased(event.x(), event.y());
        return any || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (closing) return true;
        int px = Math.round(panelX), py = Math.round(panelY);
        if (mx >= px && mx < px + SIDEBAR_W && my >= py && my <= py + panelH) {
            sidebarScrollTarget = Math.clamp(sidebarScrollTarget - v * 30, 0, sidebarMax);
            return true;
        }
        if (mx >= px + SIDEBAR_W && mx <= px + panelW && my >= py && my <= py + panelH) {
            if (contentHovered && hoverRow != null && hoverRow.scroll(mx, my, v)) return true;
            scrollTarget += v * 30;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        if (model.isBindingKey()) {
            model.keyPressed(event);
            return true;
        }
        int key = event.key();
        if (editTarget != null && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE)) {
            endEdit();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (searchMode && !searchQuery.isEmpty()) {
                searchBox.setValue("");
                return true;
            }
            onClose();
            return true;
        }
        if (!searchMode && editTarget == null && key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z
                && !event.hasControlDown() && !event.hasAltDown()) {
            selectTab(SEARCH, true);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (model.isBindingKey()) {
            model.charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (closing) return;
        dragging = false;
        resizeEdge = 0;
        endEdit();
        model.cancelEdits();
        NiaModel.finishNew();
        closing = true;
        GuiSounds.close();
    }

    @Override
    public void removed() {
        super.removed();
        TeslaMapsConfig.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void scrollSidebarTo(String tab) {
        int py = Math.round(panelY);
        int top = sidebarListTop(py), visible = sidebarListBottom(py) - top;
        int yy = 0;
        for (int i = 0; i < sideItems.size(); i++) {
            if (sideCaption.get(i)) { yy += CAPTION_H; continue; }
            if (sideItems.get(i).equals(tab)) {
                if (yy < sidebarScrollTarget || yy + TAB_H > sidebarScrollTarget + visible) {
                    sidebarScrollTarget = Math.max(0, yy - visible / 2 + TAB_H / 2);
                }
                return;
            }
            yy += TAB_STRIDE;
        }
    }
}
