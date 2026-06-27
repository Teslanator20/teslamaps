/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Classic config GUI (left sidebar + right content area, Apple-style widgets),
 * but with the right-side section headers (LabelEntry rows) made COLLAPSIBLE.
 *
 * Reuses MapConfigScreen verbatim: the same buildCategories() data, the same
 * sidebar layout/sections, the same entry.reposition/render + config wiring,
 * fitLabel, drawBorder and AppleColors. The only difference from the classic
 * GUI is that clicking a section header hides/shows its entries.
 *
 * Section collapse supports 2 levels where a category has a label hierarchy:
 * a label is treated as level 1 (nested) when its text starts with whitespace,
 * otherwise level 0 (top-level). Collapsing a level-0 header hides everything
 * (including nested labels) up to the next level-0 header; collapsing a level-1
 * header hides only its own entries up to the next label of equal-or-higher
 * level. Categories with a single label level (the common case) collapse
 * per-label, which is the intended fallback.
 */
public class MapConfigScreen5 extends MapConfigScreen {

    // Collapsed RIGHT-side section state, keyed by "category#labelOrdinal", kept for the session.
    private static final Set<String> COLLAPSED = new HashSet<>();

    // Collapsed LEFT-side super-category state, keyed by super-category name, kept for the session.
    private static final Set<String> SIDEBAR_COLLAPSED = new HashSet<>();

    // Bold super-categories grouping the flat category list in the sidebar. The user can re-map
    // this later; any buildCategories() category not listed here is appended to "Misc" so nothing
    // is ever dropped.
    private static final Map<String, List<String>> SUPER_CATEGORIES = new LinkedHashMap<>();
    static {
        SUPER_CATEGORIES.put("Dungeon", List.of(
                "Map", "Score & Splits", "Puzzles", "Timers", "Blood Camp",
                "Waypoints", "Leap", "Slayer", "ESP", "Dragons", "Hide"));
        SUPER_CATEGORIES.put("Visual", List.of("Render", "Colors", "Sounds"));
        SUPER_CATEGORIES.put("Items", List.of("Croesus", "Inventory", "Tooltips & Value"));
        SUPER_CATEGORIES.put("Comms", List.of("Party", "Chat"));
        SUPER_CATEGORIES.put("Misc", List.of("Auto"));
    }

    // One shared sidebar layout pass drives BOTH the sidebar render and its click hit-testing, so
    // the rows the user sees are exactly the rows that respond to clicks (no render-vs-click drift).
    private static final class SideRow {
        final String superCat;   // non-null => bold collapsible super-category header
        final String category;   // non-null => selectable category row under a super-category
        final int y, h;          // absolute (pre-scroll) layout within the sidebar list area
        SideRow(String superCat, String category, int y, int h) {
            this.superCat = superCat; this.category = category; this.y = y; this.h = h;
        }
        boolean isHeader() { return superCat != null; }
    }
    private final List<SideRow> sideRows = new ArrayList<>();
    private int sidebarContentH = 0;

    // One shared layout pass drives both rendering and click hit-testing, so the
    // collapse-aware rows the user sees are exactly the rows that get clicked.
    private static final class Row {
        final SettingsEntry entry;
        final String collapseKey;   // non-null only for a LabelEntry (collapsible header)
        final int level;            // 0 = top-level header, 1 = nested header (labels only)
        final int y, h;             // absolute (pre-scroll) layout within the content area
        Row(SettingsEntry entry, String collapseKey, int level, int y, int h) {
            this.entry = entry; this.collapseKey = collapseKey; this.level = level; this.y = y; this.h = h;
        }
        boolean isHeader() { return collapseKey != null; }
    }
    private final List<Row> rows = new ArrayList<>();
    private int contentHeight = 0;

    private static boolean defaultsSeeded = false;

    @Override
    protected void init() {
        buildCategories();

        // Collapse all right-side sections by default (once per session); user toggles persist after.
        if (!defaultsSeeded) {
            for (Map.Entry<String, java.util.List<SettingsEntry>> c : categories.entrySet()) {
                int ord = 0;
                for (SettingsEntry e : c.getValue()) {
                    if (e.isLabel()) COLLAPSED.add(c.getKey() + "#" + (ord++));
                }
            }
            defaultsSeeded = true;
        }

        // Same search field placement as the classic GUI.
        int sfX = SIDEBAR_WIDTH + 14;
        int sfW = this.width - SIDEBAR_WIDTH - 28;
        searchField = new EditBox(font, sfX + 18, 13, sfW - 26, 12, Component.literal("Search"));
        searchField.setHint(Component.literal("Search settings..."));
        searchField.setBordered(false);
        searchField.setResponder(query -> {
            searchQuery = query.toLowerCase();
            scrollOffset = 0;
            expandedColorEntry = null;
        });
        addRenderableWidget(searchField);
    }

    private static int labelLevel(String text) {
        // Indented label text => nested (level 1); otherwise top-level (level 0).
        return (!text.isEmpty() && Character.isWhitespace(text.charAt(0))) ? 1 : 0;
    }

    // Build the effective super-category -> categories grouping for the categories that actually
    // exist after buildCategories(). Preserves SUPER_CATEGORIES order, drops categories that don't
    // exist, and appends any leftover (unlisted) category to "Misc" so none are ever lost.
    private Map<String, List<String>> effectiveGrouping() {
        Map<String, List<String>> grouping = new LinkedHashMap<>();
        Set<String> placed = new HashSet<>();
        for (Map.Entry<String, List<String>> sc : SUPER_CATEGORIES.entrySet()) {
            List<String> members = new ArrayList<>();
            for (String cat : sc.getValue()) {
                if (categories.containsKey(cat)) {
                    members.add(cat);
                    placed.add(cat);
                }
            }
            grouping.put(sc.getKey(), members);
        }
        List<String> leftovers = new ArrayList<>();
        for (String cat : categories.keySet()) {
            if (!placed.contains(cat)) leftovers.add(cat);
        }
        if (!leftovers.isEmpty()) {
            grouping.computeIfAbsent("Misc", k -> new ArrayList<>()).addAll(leftovers);
        }
        return grouping;
    }

    // Rebuild the collapse-aware sidebar row list (super-category headers + visible category rows).
    private void buildSidebarLayout() {
        sideRows.clear();
        int y = 0;
        for (Map.Entry<String, List<String>> sc : effectiveGrouping().entrySet()) {
            String superCat = sc.getKey();
            sideRows.add(new SideRow(superCat, null, y, SECTION_HEADER_H));
            y += SECTION_HEADER_H;
            if (!SIDEBAR_COLLAPSED.contains(superCat)) {
                for (String cat : sc.getValue()) {
                    sideRows.add(new SideRow(null, cat, y, CATEGORY_ROW_H));
                    y += CATEGORY_ROW_H;
                }
            }
            y += SECTION_GAP;
        }
        sidebarContentH = y;
    }

    // Collapse-aware category hit-test (replaces the inherited categoryAtY which assumes the flat
    // parent layout). Returns the category clicked, or null if a header / empty space was clicked.
    private String categoryAtYTree(int my) {
        int top = SIDEBAR_TOP;
        for (SideRow r : sideRows) {
            if (r.isHeader()) continue;
            int drawY = top + r.y - sidebarScroll;
            if (my >= drawY && my < drawY + r.h) return r.category;
        }
        return null;
    }

    // Super-category header hit-test; returns the super-category name clicked, or null.
    private String superCategoryAtY(int my) {
        int top = SIDEBAR_TOP;
        for (SideRow r : sideRows) {
            if (!r.isHeader()) continue;
            int drawY = top + r.y - sidebarScroll;
            if (my >= drawY && my < drawY + r.h) return r.superCat;
        }
        return null;
    }

    // Rebuild the collapse-aware row list for the selected category (or search results).
    private void buildLayout() {
        rows.clear();
        int y = 0;
        boolean searching = !searchQuery.isEmpty();

        if (searching) {
            // Flat list of matching entries; no headers, nothing collapses.
            for (List<SettingsEntry> entries : categories.values()) {
                for (SettingsEntry e : entries) {
                    if (e.isLabel()) continue;
                    if (!e.matchesSearch(searchQuery)) continue;
                    int h = e.getHeight();
                    rows.add(new Row(e, null, 0, y, h));
                    y += h;
                }
            }
            contentHeight = y;
            return;
        }

        List<SettingsEntry> entries = categories.get(selectedCategory);
        if (entries == null) { contentHeight = 0; return; }

        int collapseLevel = Integer.MAX_VALUE; // levels strictly above this are hidden
        int ord = 0;
        for (SettingsEntry e : entries) {
            if (e.isLabel()) {
                int lvl = labelLevel(e.getLabel());
                String key = selectedCategory + "#" + (ord++);
                if (lvl <= collapseLevel) {
                    // This header is visible; whether its body is hidden depends on its own collapsed state.
                    collapseLevel = COLLAPSED.contains(key) ? lvl : Integer.MAX_VALUE;
                    int h = e.getHeight();
                    rows.add(new Row(e, key, lvl, y, h));
                    y += h;
                }
                // else: this label is inside a collapsed higher-level section -> skip it.
            } else if (collapseLevel == Integer.MAX_VALUE) {
                int h = e.getHeight();
                rows.add(new Row(e, null, 0, y, h));
                y += h;
            }
            // else: entry is inside a collapsed section -> skip it.
        }
        contentHeight = y;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        // Build the collapse-aware sidebar tree first; both click hit-testing and rendering below
        // read from this single layout pass so they can never disagree.
        buildSidebarLayout();

        // ----- Sidebar click handling: super-category headers toggle, category rows select -----
        if (clicked && mouseX < SIDEBAR_WIDTH && mouseY >= SIDEBAR_TOP && mouseY < sidebarBottom()) {
            String superHit = superCategoryAtY(mouseY);
            if (superHit != null) {
                if (!SIDEBAR_COLLAPSED.remove(superHit)) SIDEBAR_COLLAPSED.add(superHit);
                buildSidebarLayout(); // height changed; keep render in sync this frame
                int sbViewport = sidebarBottom() - SIDEBAR_TOP;
                sidebarMaxScroll = Math.max(0, sidebarContentH - sbViewport);
                if (sidebarScroll > sidebarMaxScroll) sidebarScroll = sidebarMaxScroll;
            } else {
                String hit = categoryAtYTree(mouseY);
                if (hit != null) {
                    selectedCategory = hit;
                    searchQuery = "";
                    searchField.setValue("");
                    scrollOffset = 0;
                    expandedColorEntry = null;
                }
            }
        }

        int shortcutBtnY = this.height - 89;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= shortcutBtnY && mouseY < shortcutBtnY + 22) {
            minecraft.setScreen(new ShortcutScreen());
            wasMouseDown = isMouseDown;
            return;
        }

        int msgBtnY = this.height - 62;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= msgBtnY && mouseY < msgBtnY + 22) {
            minecraft.setScreen(new KeybindMessageScreen());
            wasMouseDown = isMouseDown;
            return;
        }

        int tmapBtnY = this.height - 35;
        if (clicked && mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22) {
            if (minecraft.player != null) minecraft.player.connection.sendCommand("tmap");
        }

        int doneX = this.width - 70;
        int doneY = this.height - 30;
        if (clicked && mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22) {
            onClose();
            wasMouseDown = isMouseDown;
            return;
        }

        // ----- Background -----
        context.fill(0, 0, this.width, this.height, 0xFF1C1C1E);

        // ----- Search field chrome (matches classic) -----
        int sfX = SIDEBAR_WIDTH + 14, sfY = 8, sfW = this.width - SIDEBAR_WIDTH - 28, sfH = 22;
        context.fill(sfX, sfY, sfX + sfW, sfY + sfH, 0xFF242426);
        boolean sfFocus = searchField != null && searchField.isFocused();
        drawBorder(context, sfX, sfY, sfW, sfH, sfFocus ? AppleColors.ACCENT_GREEN : 0xFF3A3A3C);
        int gx = sfX + 7, gy = sfY + 7;
        context.fill(gx, gy, gx + 5, gy + 1, 0xFF8E8E93);
        context.fill(gx, gy, gx + 1, gy + 5, 0xFF8E8E93);
        context.fill(gx + 4, gy, gx + 5, gy + 5, 0xFF8E8E93);
        context.fill(gx, gy + 4, gx + 5, gy + 5, 0xFF8E8E93);
        context.fill(gx + 5, gy + 5, gx + 8, gy + 8, 0xFF8E8E93);

        // ----- Sidebar -----
        renderSidebar(context, mouseX, mouseY, shortcutBtnY, msgBtnY, tmapBtnY);

        // ----- Right content area with collapsible sections -----
        int contentTop = 50;
        int contentBottom = this.height - 40;
        int contentLeft = SIDEBAR_WIDTH;
        int contentRight = this.width - 10;

        String header = searchQuery.isEmpty() ? selectedCategory : "Search Results";
        context.fill(SIDEBAR_WIDTH, 30, this.width, contentTop, 0xFF1C1C1E);
        context.text(font, header, SIDEBAR_WIDTH + 20, 35, 0xFFFFFFFF);

        buildLayout();

        int visibleHeight = contentBottom - contentTop;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        context.enableScissor(contentLeft, contentTop, contentRight, contentBottom);
        for (Row row : rows) {
            int drawY = contentTop + row.y - scrollOffset;
            if (drawY + row.h < contentTop - 50 || drawY > contentBottom + 50) continue;
            if (row.isHeader()) {
                renderHeader(context, row, drawY, mouseX, mouseY, clicked);
            } else {
                row.entry.render(context, this, drawY, mouseX, mouseY, clicked, isMouseDown);
            }
        }
        context.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = this.width - 8;
            int scrollbarW = 4;
            float scrollRatio = (float) scrollOffset / maxScroll;
            float thumbRatio = (float) visibleHeight / contentHeight;
            int thumbH = Math.max(20, (int) (visibleHeight * thumbRatio));
            int thumbY = contentTop + (int) ((visibleHeight - thumbH) * scrollRatio);
            context.fill(scrollbarX, contentTop, scrollbarX + scrollbarW, contentBottom, 0xFF2C2C2E);
            context.fill(scrollbarX, thumbY, scrollbarX + scrollbarW, thumbY + thumbH, 0xFF5A5A5C);
        }

        // ----- Footer + Done button (matches classic) -----
        context.fill(SIDEBAR_WIDTH, this.height - 40, this.width, this.height, 0xFF1C1C1E);
        boolean doneHovered = mouseX >= doneX && mouseX < doneX + 60 && mouseY >= doneY && mouseY < doneY + 22;
        context.fill(doneX, doneY, doneX + 60, doneY + 22, doneHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, doneX, doneY, 60, 22, doneHovered ? AppleColors.ACCENT_GREEN : 0xFF48484A);
        context.centeredText(font, "Done", doneX + 30, doneY + 7, 0xFFFFFFFF);

        wasMouseDown = isMouseDown;
        renderWidgetsOnly(context, mouseX, mouseY, delta);
    }

    // Collapsible section header: a header row showing a chevron + the label, click toggles collapse.
    private void renderHeader(GuiGraphicsExtractor ctx, Row row, int drawY, int mouseX, int mouseY, boolean clicked) {
        int contentLeft = SIDEBAR_WIDTH;
        int contentRight = this.width - 10;
        boolean hovered = mouseX >= contentLeft && mouseX < contentRight && mouseY >= drawY && mouseY < drawY + row.h;
        if (clicked && hovered) {
            if (!COLLAPSED.remove(row.collapseKey)) COLLAPSED.add(row.collapseKey);
            expandedColorEntry = null;
        }
        boolean collapsed = COLLAPSED.contains(row.collapseKey);

        if (hovered) ctx.fill(contentLeft + 8, drawY, contentRight, drawY + row.h, 0x18FFFFFF);

        // Indent nested (level 1) headers a little so the hierarchy reads visually.
        int textX = SIDEBAR_WIDTH + 25 + (row.level == 1 ? 12 : 0);
        ctx.text(font, (collapsed ? "▶ " : "▼ ") + row.entry.getLabel().trim(),
                textX, drawY + 8, AppleColors.ACCENT_GREEN);
    }

    // Sidebar drawing, copied from the classic GUI so gui5 looks identical.
    private void renderSidebar(GuiGraphicsExtractor context, int mouseX, int mouseY,
                               int shortcutBtnY, int msgBtnY, int tmapBtnY) {
        context.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xFF2C2C2E);
        context.text(font, "TeslaMaps", 10, 8, 0xFFFFFFFF);
        context.text(font, "Categories", 10, 22, AppleColors.ACCENT_GREEN);

        int sbTop = SIDEBAR_TOP, sbBottom = sidebarBottom();
        // Clamp scroll to the (possibly just-changed) collapsible tree height before drawing.
        int sbViewport = sbBottom - sbTop;
        sidebarMaxScroll = Math.max(0, sidebarContentH - sbViewport);
        if (sidebarScroll > sidebarMaxScroll) sidebarScroll = sidebarMaxScroll;

        context.enableScissor(0, sbTop, SIDEBAR_WIDTH, sbBottom);
        for (SideRow r : sideRows) {
            int rowY = sbTop + r.y - sidebarScroll;
            if (rowY + r.h <= sbTop || rowY >= sbBottom) continue; // culled (vertical only)
            if (r.isHeader()) {
                boolean collapsed = SIDEBAR_COLLAPSED.contains(r.superCat);
                boolean hovered = mouseX < SIDEBAR_WIDTH && mouseY >= rowY && mouseY < rowY + r.h
                        && mouseY >= sbTop && mouseY < sbBottom;
                if (hovered) context.fill(3, rowY, SIDEBAR_WIDTH - 3, rowY + r.h, 0x18FFFFFF);
                // Bold-look header: chevron + super-category name, drawn twice (1px offset) to fake weight.
                int hc = hovered ? 0xFFFFFFFF : 0xFFB0B0B4;
                String label = (collapsed ? "▶ " : "▼ ") + r.superCat.toUpperCase();
                context.text(font, label, 8, rowY + 8, hc);
                context.text(font, label, 9, rowY + 8, hc);
            } else {
                String category = r.category;
                boolean selected = category.equals(selectedCategory) && searchQuery.isEmpty();
                boolean hovered = mouseX < SIDEBAR_WIDTH && mouseY >= rowY && mouseY < rowY + r.h
                        && mouseY >= sbTop && mouseY < sbBottom;
                if (selected) context.fill(5, rowY, SIDEBAR_WIDTH - 5, rowY + r.h, 0x40FFFFFF);
                else if (hovered) context.fill(5, rowY, SIDEBAR_WIDTH - 5, rowY + r.h, 0x20FFFFFF);
                int textColor = selected ? AppleColors.ACCENT_GREEN : (hovered ? 0xFFFFFFFF : 0xFF8E8E93);
                // Indent member categories under their super-category.
                context.text(font, category, 22, rowY + 8, textColor);
            }
        }
        context.disableScissor();
        if (sidebarMaxScroll > 0) {
            int trackH = sbViewport;
            int thumbH = Math.max(20, (int) ((float) trackH * trackH / sidebarContentH));
            int thumbY = sbTop + (int) ((trackH - thumbH) * ((float) sidebarScroll / sidebarMaxScroll));
            context.fill(SIDEBAR_WIDTH - 3, thumbY, SIDEBAR_WIDTH - 1, thumbY + thumbH, 0xFF5A5A5C);
        }

        boolean shortcutHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= shortcutBtnY && mouseY < shortcutBtnY + 22;
        context.fill(8, shortcutBtnY, SIDEBAR_WIDTH - 8, shortcutBtnY + 22, shortcutHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, 8, shortcutBtnY, SIDEBAR_WIDTH - 16, 22, shortcutHovered ? AppleColors.ACCENT_GREEN : 0xFF48484A);
        context.centeredText(font, "/tmap shortcut", SIDEBAR_WIDTH / 2, shortcutBtnY + 7, 0xFF8E8E93);

        boolean msgHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= msgBtnY && mouseY < msgBtnY + 22;
        context.fill(8, msgBtnY, SIDEBAR_WIDTH - 8, msgBtnY + 22, msgHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, 8, msgBtnY, SIDEBAR_WIDTH - 16, 22, msgHovered ? AppleColors.ACCENT_GREEN : 0xFF48484A);
        context.centeredText(font, "/tmap hotkeys", SIDEBAR_WIDTH / 2, msgBtnY + 7, 0xFF8E8E93);

        boolean tmapHovered = mouseX >= 8 && mouseX < SIDEBAR_WIDTH - 8 && mouseY >= tmapBtnY && mouseY < tmapBtnY + 22;
        context.fill(8, tmapBtnY, SIDEBAR_WIDTH - 8, tmapBtnY + 22, tmapHovered ? 0xFF3A3A3C : 0xFF2C2C2E);
        drawBorder(context, 8, tmapBtnY, SIDEBAR_WIDTH - 16, 22, tmapHovered ? AppleColors.ACCENT_GREEN : 0xFF48484A);
        context.centeredText(font, "/tmap gui", SIDEBAR_WIDTH / 2, tmapBtnY + 7, 0xFF8E8E93);
    }

    private void renderWidgetsOnly(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        for (var w : this.children()) {
            if (w instanceof net.minecraft.client.gui.components.Renderable r) {
                r.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (mouseX > SIDEBAR_WIDTH) {
            int contentTop = 50;
            // Forward to a hovered slider/expanded entry first (scroll-to-adjust), else scroll content.
            for (Row row : rows) {
                if (row.isHeader()) continue;
                int drawY = contentTop + row.y - scrollOffset;
                if (mouseY >= drawY && mouseY < drawY + row.h) {
                    if (row.entry.scrollAt((int) mouseX, (int) mouseY, drawY, v)) return true;
                    break;
                }
            }
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - v * 15));
            return true;
        }
        sidebarScroll = (int) Math.max(0, Math.min(sidebarMaxScroll, sidebarScroll - v * 18));
        return true;
    }

    @Override
    public void onClose() {
        com.teslamaps.config.TeslaMapsConfig.save();
        super.onClose();
    }
}
