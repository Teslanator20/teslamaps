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

import com.teslamaps.config.TeslaMapsConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Odin-style config GUI: a single vertical, scrollable column of collapsible
 * category dropdowns stacked top-to-bottom. Reuses MapConfigScreen's category /
 * entry definitions and config wiring verbatim; only the layout/chrome differs.
 */
public class MapConfigScreen2 extends MapConfigScreen {

    private static final int TOP_BAR_H = 30;     // title + search + buttons
    private static final int COLUMN_TOP = TOP_BAR_H + 8;
    private static final int BOTTOM_PAD = 12;

    private static final int COL_WIDTH = 250;    // scaled px; the dropdown column width
    private static final int COL_LEFT = 16;
    private static final int HEADER_H = 24;      // dropdown header row height
    private static final int ENTRY_INDENT = 8;   // entries sit slightly indented under their header
    private static final int CAT_GAP = 4;        // gap between consecutive dropdowns

    // Which category dropdowns are expanded. Collapsed by default (starts compact).
    private final Set<String> openCategories = new HashSet<>();

    // One shared layout pass drives both rendering and click hit-testing.
    private static final class Row {
        final String category;         // non-null => header row for this category
        final SettingsEntry entry;     // non-null => hosted entry row
        final int x, w, y, h;          // absolute (pre-scroll) layout
        Row(String category, SettingsEntry entry, int x, int w, int y, int h) {
            this.category = category; this.entry = entry; this.x = x; this.w = w; this.y = y; this.h = h;
        }
        boolean isHeader() { return category != null; }
    }
    private final List<Row> rows = new ArrayList<>();
    private int contentHeight = 0;

    @Override
    protected void init() {
        buildCategories();

        // Search box lives in the top bar, above the column.
        int sfX = COL_LEFT;
        int sfW = COL_WIDTH;
        searchField = new EditBox(font, sfX + 14, 9, sfW - 22, 12, Component.literal("Search"));
        searchField.setHint(Component.literal("Search..."));
        searchField.setBordered(false);
        searchField.setResponder(query -> {
            searchQuery = query.toLowerCase();
            scrollOffset = 0;
            expandedColorEntry = null;
        });
        addRenderableWidget(searchField);
    }

    // Rebuild the row layout from the open/closed state (and search filter).
    // Header rows + (when open) the category's entries, stacked top to bottom.
    private void buildLayout() {
        rows.clear();
        int x = COL_LEFT;
        int entryX = COL_LEFT + ENTRY_INDENT;
        int entryW = COL_WIDTH - ENTRY_INDENT;
        int y = 0;

        boolean searching = !searchQuery.isEmpty();

        for (Map.Entry<String, List<SettingsEntry>> cat : categories.entrySet()) {
            String name = cat.getKey();
            List<SettingsEntry> entries = cat.getValue();

            // In search mode, only show categories that contain a match; force them open.
            List<SettingsEntry> shown;
            if (searching) {
                shown = new ArrayList<>();
                for (SettingsEntry e : entries) {
                    if (e.isLabel()) continue; // labels are sub-headers, not matchable rows
                    if (e.matchesSearch(searchQuery)) shown.add(e);
                }
                if (shown.isEmpty()) continue;
            } else {
                shown = entries;
            }

            boolean open = searching || openCategories.contains(name);

            rows.add(new Row(name, null, x, COL_WIDTH, y, HEADER_H));
            y += HEADER_H;

            if (open) {
                for (SettingsEntry e : shown) {
                    e.reposition(entryX, entryW);
                    int h = e.getHeight();
                    rows.add(new Row(null, e, entryX, entryW, y, h));
                    y += h;
                }
                y += 2;
            }
            y += CAT_GAP;
        }
        contentHeight = y;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        // Dim the game behind, no heavy window chrome.
        ctx.fill(0, 0, this.width, this.height, 0xC0101012);

        // ----- Top bar: title, search, buttons -----
        ctx.text(font, "TeslaMaps", COL_LEFT, 4, AppleColors.TEXT_PRIMARY);

        int sfX = COL_LEFT, sfY = 18, sfW = COL_WIDTH, sfH = 18;
        ctx.fill(sfX, sfY, sfX + sfW, sfY + sfH, AppleColors.INPUT_BACKGROUND);
        boolean sfFocus = searchField != null && searchField.isFocused();
        drawBorder(ctx, sfX, sfY, sfW, sfH, sfFocus ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.text(font, "S", sfX + 5, sfY + 5, AppleColors.TEXT_SECONDARY);
        if (searchField != null) searchField.setY(sfY + 5);

        // Done + Classic GUI buttons (right of the column, top, unobtrusive).
        int btnY = 4, btnH = 18;
        int doneW = 44, doneX = COL_LEFT + COL_WIDTH + 8;
        boolean doneHover = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH;
        ctx.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHover ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
        drawBorder(ctx, doneX, btnY, doneW, btnH, doneHover ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.centeredText(font, "Done", doneX + doneW / 2, btnY + 5, AppleColors.TEXT_PRIMARY);

        int oldW = 80, oldX = doneX + doneW + 6;
        boolean oldHover = mouseX >= oldX && mouseX < oldX + oldW && mouseY >= btnY && mouseY < btnY + btnH;
        ctx.fill(oldX, btnY, oldX + oldW, btnY + btnH, oldHover ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
        drawBorder(ctx, oldX, btnY, oldW, btnH, oldHover ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.centeredText(font, "Classic GUI", oldX + oldW / 2, btnY + 5, AppleColors.TEXT_SECONDARY);

        if (clicked && doneHover) { onClose(); wasMouseDown = isMouseDown; return; }
        if (clicked && oldHover && minecraft.player != null) {
            minecraft.player.connection.sendCommand("tmap");
            wasMouseDown = isMouseDown;
            return;
        }

        // ----- Column -----
        buildLayout();

        int top = COLUMN_TOP;
        int bottom = this.height - BOTTOM_PAD;
        int visibleH = bottom - top;
        maxScroll = Math.max(0, contentHeight - visibleH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        ctx.enableScissor(0, top, this.width, bottom);
        for (Row row : rows) {
            int drawY = top + row.y - scrollOffset;
            if (drawY + row.h < top - 30 || drawY > bottom + 30) continue;
            if (row.isHeader()) {
                renderHeader(ctx, row, drawY, mouseX, mouseY, clicked);
            } else {
                row.entry.reposition(row.x, row.w);
                row.entry.render(ctx, this, drawY, mouseX, mouseY, clicked, isMouseDown);
            }
        }
        ctx.disableScissor();

        // ----- Scrollbar -----
        if (maxScroll > 0) {
            int sbX = COL_LEFT + COL_WIDTH + 2;
            int trackH = visibleH;
            int thumbH = Math.max(20, (int) ((float) trackH * trackH / contentHeight));
            int thumbY = top + (int) ((trackH - thumbH) * ((float) scrollOffset / maxScroll));
            ctx.fill(sbX, top, sbX + 3, bottom, 0x40000000);
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF5A5A5C);
        }

        wasMouseDown = isMouseDown;
        // Skip MapConfigScreen.extractRenderState (its sidebar layout); render only our widgets.
        renderWidgetsOnly(ctx, mouseX, mouseY, delta);
    }

    // Render only the registered widgets (search box) without the parent's chrome.
    private void renderWidgetsOnly(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        for (var w : this.children()) {
            if (w instanceof net.minecraft.client.gui.components.Renderable r) {
                r.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
    }

    private void renderHeader(GuiGraphicsExtractor ctx, Row row, int drawY,
                              int mouseX, int mouseY, boolean clicked) {
        boolean searching = !searchQuery.isEmpty();
        boolean open = searching || openCategories.contains(row.category);
        boolean hovered = mouseX >= row.x && mouseX < row.x + row.w && mouseY >= drawY && mouseY < drawY + HEADER_H;

        // Click toggles expand/collapse (disabled while searching — everything is forced open).
        if (clicked && hovered && !searching) {
            if (open) openCategories.remove(row.category);
            else openCategories.add(row.category);
            expandedColorEntry = null;
        }

        ctx.fill(row.x, drawY, row.x + row.w, drawY + HEADER_H, hovered ? 0xFF323234 : AppleColors.CARD_BACKGROUND);
        if (open) ctx.fill(row.x, drawY, row.x + 2, drawY + HEADER_H, AppleColors.ACCENT_GREEN);
        ctx.text(font, open ? "▼" : "▶", row.x + 8, drawY + 8, AppleColors.TEXT_SECONDARY);
        ctx.text(font, row.category, row.x + 22, drawY + 8,
                open ? AppleColors.TEXT_PRIMARY : AppleColors.TEXT_SECONDARY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        int top = COLUMN_TOP;
        // Forward to a hovered slider/expanded entry first (scroll-to-adjust), else scroll column.
        for (Row row : rows) {
            if (row.isHeader()) continue;
            int drawY = top + row.y - scrollOffset;
            if (mouseY >= drawY && mouseY < drawY + row.h) {
                if (row.entry.scrollAt((int) mouseX, (int) mouseY, drawY, v)) return true;
                break;
            }
        }
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - v * 18));
        return true;
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        super.onClose();
    }
}
