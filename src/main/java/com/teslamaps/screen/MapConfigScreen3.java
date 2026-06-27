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
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Alternative Odin-style config GUI: category tabs across the top, settings shown
 * as a grid of cards. Reuses MapConfigScreen's category/entry definitions and
 * config wiring verbatim; only the layout/chrome differs.
 */
public class MapConfigScreen3 extends MapConfigScreen {

    private static final int TOP_BAR_H = 30;
    private static final int TAB_BAR_Y = 34;
    private static final int TAB_BAR_H = 24;
    private static final int CONTENT_TOP = TAB_BAR_Y + TAB_BAR_H + 6;
    private static final int BOTTOM_BAR_H = 36;

    private static final int CARD_MIN_W = 188;
    private static final int CARD_H = 38;        // collapsed card height
    private static final int GAP = 8;
    private static final int MARGIN = 14;

    private int tabScroll = 0;
    private int tabMaxScroll = 0;
    private final List<int[]> tabHitboxes = new ArrayList<>(); // x,w per visible tab (parallel to tabNames)
    private final List<String> tabNames = new ArrayList<>();

    // Non-toggle entries the user has expanded (identity-based).
    private final Set<SettingsEntry> expanded = new HashSet<>();

    // Layout computed each frame: card rect + the entry it hosts.
    private static final class Card {
        final SettingsEntry entry;
        final int x, y, w, h;
        Card(SettingsEntry e, int x, int y, int w, int h) { this.entry = e; this.x = x; this.y = y; this.w = w; this.h = h; }
    }
    private final List<Card> layout = new ArrayList<>();
    private int layoutHeight = 0;

    @Override
    protected void init() {
        buildCategories();
        tabNames.clear();
        tabNames.addAll(categories.keySet());
        if (!tabNames.contains(selectedCategory) && !tabNames.isEmpty()) selectedCategory = tabNames.get(0);

        int sfW = 150;
        int sfX = this.width - MARGIN - sfW + 14;
        searchField = new EditBox(font, sfX, 11, sfW - 22, 12, Component.literal("Search"));
        searchField.setHint(Component.literal("Search..."));
        searchField.setBordered(false);
        searchField.setResponder(query -> {
            searchQuery = query.toLowerCase();
            scrollOffset = 0;
            expandedColorEntry = null;
        });
        addRenderableWidget(searchField);
    }

    private int columns(int contentW) {
        return Math.max(1, (contentW + GAP) / (CARD_MIN_W + GAP));
    }

    // Build the card layout for the active entry list. Labels span the full row and
    // force a new line; toggles and expandable cards flow left-to-right into columns.
    private void buildLayout(List<SettingsEntry> entries, int contentLeft, int contentW, int yStart) {
        layout.clear();
        int cols = columns(contentW);
        int cardW = (contentW - GAP * (cols - 1)) / cols;

        int x = contentLeft;
        int y = yStart;
        int col = 0;
        int rowMaxH = 0;

        for (SettingsEntry e : entries) {
            if (e.isLabel()) {
                if (col != 0) { y += rowMaxH + GAP; col = 0; rowMaxH = 0; }
                layout.add(new Card(e, contentLeft, y, contentW, 22));
                y += 22 + 2;
                continue;
            }

            int h = cardHeight(e);
            // Color picker needs the full content width while open.
            boolean fullWidth = (e instanceof ColorEntry) && expanded.contains(e);
            if (fullWidth && col != 0) { y += rowMaxH + GAP; col = 0; rowMaxH = 0; }

            int thisW = fullWidth ? contentW : cardW;
            int thisX = fullWidth ? contentLeft : (contentLeft + col * (cardW + GAP));
            layout.add(new Card(e, thisX, y, thisW, h));
            rowMaxH = Math.max(rowMaxH, h);

            if (fullWidth) {
                y += rowMaxH + GAP; col = 0; rowMaxH = 0;
            } else {
                col++;
                if (col >= cols) { y += rowMaxH + GAP; col = 0; rowMaxH = 0; }
            }
        }
        if (col != 0) y += rowMaxH + GAP;
        layoutHeight = y - yStart;
    }

    private int cardHeight(SettingsEntry e) {
        if (e.isToggle()) return CARD_H;
        if (!expanded.contains(e)) return CARD_H;
        // ColorEntry's inline HSV picker draws ~115px below the control origin.
        if (e instanceof ColorEntry) return CARD_H + 122;
        // Header + the entry's own control row (+ any dropdown expansion it manages itself).
        return CARD_H + Math.max(ROW_HEIGHT_(), e.getHeight());
    }

    // Mirrors base ROW_HEIGHT (kept private there); used as a min control-area height.
    private static int ROW_HEIGHT_() { return 26; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        ctx.fill(0, 0, this.width, this.height, AppleColors.BACKGROUND);

        // ----- Top bar -----
        ctx.fill(0, 0, this.width, TOP_BAR_H, 0xFF242426);
        ctx.text(font, "TeslaMaps", MARGIN, 6, AppleColors.TEXT_PRIMARY);
        ctx.text(font, "Settings", MARGIN, 17, AppleColors.ACCENT_GREEN);

        int sfW = 150, sfX = this.width - MARGIN - sfW, sfY = 6, sfH = 18;
        ctx.fill(sfX, sfY, sfX + sfW, sfY + sfH, AppleColors.INPUT_BACKGROUND);
        boolean sfFocus = searchField != null && searchField.isFocused();
        drawBorder(ctx, sfX, sfY, sfW, sfH, sfFocus ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.text(font, "S", sfX + 6, sfY + 5, AppleColors.TEXT_SECONDARY);

        // ----- Tab bar -----
        renderTabs(ctx, mouseX, mouseY, clicked);

        int contentLeft = MARGIN;
        int contentW = this.width - MARGIN * 2 - 6; // leave room for scrollbar
        int contentTop = CONTENT_TOP;
        int contentBottom = this.height - BOTTOM_BAR_H;

        List<SettingsEntry> entries = getEntriesToShow();
        buildLayout(entries, contentLeft, contentW, 0);

        int visibleH = contentBottom - contentTop;
        maxScroll = Math.max(0, layoutHeight - visibleH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        ctx.enableScissor(0, contentTop, this.width, contentBottom);
        for (Card card : layout) {
            int drawY = contentTop + card.y - scrollOffset;
            if (drawY + card.h < contentTop - 20 || drawY > contentBottom + 20) continue;
            renderCard(ctx, card, drawY, mouseX, mouseY, clicked, isMouseDown);
        }
        ctx.disableScissor();

        // ----- Scrollbar -----
        if (maxScroll > 0) {
            int sbX = this.width - 6;
            int trackH = visibleH;
            int thumbH = Math.max(20, (int) ((float) trackH * trackH / layoutHeight));
            int thumbY = contentTop + (int) ((trackH - thumbH) * ((float) scrollOffset / maxScroll));
            ctx.fill(sbX, contentTop, sbX + 4, contentBottom, 0xFF2C2C2E);
            ctx.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF5A5A5C);
        }

        // ----- Bottom bar -----
        renderBottomBar(ctx, mouseX, mouseY, clicked);

        wasMouseDown = isMouseDown;
        // Skip MapConfigScreen.extractRenderState (its sidebar layout); go straight to Screen.
        renderWidgetsOnly(ctx, mouseX, mouseY, delta);
    }

    // Render only the registered widgets (search box) without invoking the parent's chrome.
    private void renderWidgetsOnly(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        for (var w : this.children()) {
            if (w instanceof net.minecraft.client.gui.components.Renderable r) {
                r.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
    }

    private void renderTabs(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
        ctx.fill(0, TAB_BAR_Y, this.width, TAB_BAR_Y + TAB_BAR_H, 0xFF1C1C1E);

        // Measure total width.
        int total = MARGIN;
        for (String t : tabNames) total += font.width(t) + 22 + GAP;
        int avail = this.width - MARGIN * 2;
        tabMaxScroll = Math.max(0, total - avail - MARGIN);
        if (tabScroll > tabMaxScroll) tabScroll = tabMaxScroll;

        tabHitboxes.clear();
        ctx.enableScissor(0, TAB_BAR_Y, this.width, TAB_BAR_Y + TAB_BAR_H);
        int tx = MARGIN - tabScroll;
        int ty = TAB_BAR_Y + 2;
        int th = TAB_BAR_H - 4;
        for (String name : tabNames) {
            int tw = font.width(name) + 22;
            boolean selected = name.equals(selectedCategory) && searchQuery.isEmpty();
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + th
                    && mouseY >= TAB_BAR_Y && mouseY < TAB_BAR_Y + TAB_BAR_H;

            if (clicked && hovered) {
                selectedCategory = name;
                searchQuery = "";
                if (searchField != null) searchField.setValue("");
                scrollOffset = 0;
                expanded.clear();
                expandedColorEntry = null;
            }

            int bg = selected ? 0xFF2C2C2E : (hovered ? 0xFF262628 : 0xFF1C1C1E);
            ctx.fill(tx, ty, tx + tw, ty + th, bg);
            int textColor = selected ? AppleColors.ACCENT_GREEN : (hovered ? AppleColors.TEXT_PRIMARY : AppleColors.TEXT_SECONDARY);
            ctx.centeredText(font, name, tx + tw / 2, ty + (th - 8) / 2, textColor);
            if (selected) ctx.fill(tx + 4, ty + th - 2, tx + tw - 4, ty + th, AppleColors.ACCENT_GREEN);

            tabHitboxes.add(new int[]{tx, tw});
            tx += tw + GAP;
        }
        ctx.disableScissor();
    }

    private void renderCard(GuiGraphicsExtractor ctx, Card card, int drawY,
                            int mouseX, int mouseY, boolean clicked, boolean mouseDown) {
        SettingsEntry e = card.entry;

        if (e.isLabel()) {
            e.reposition(card.x, card.w);
            e.render(ctx, this, drawY, mouseX, mouseY, false, false);
            return;
        }

        boolean hovered = mouseX >= card.x && mouseX < card.x + card.w && mouseY >= drawY && mouseY < drawY + card.h;

        // Card background + border.
        ctx.fill(card.x, drawY, card.x + card.w, drawY + card.h, AppleColors.CARD_BACKGROUND);
        drawBorder(ctx, card.x, drawY, card.w, card.h,
                hovered ? 0xFF5A5A5C : AppleColors.INPUT_BORDER);

        if (e.isToggle()) {
            renderToggleCard(ctx, (ToggleEntry) e, card, drawY, mouseX, mouseY, clicked, hovered);
            return;
        }

        // Expandable card: header row (label + chevron), control area when expanded.
        boolean open = expanded.contains(e);
        int headerH = CARD_H;
        boolean headerHover = mouseX >= card.x && mouseX < card.x + card.w && mouseY >= drawY && mouseY < drawY + headerH;

        String label = e.getLabel();
        ctx.text(font, fitLabel(label, card.x + 10, card.x + card.w - 70), card.x + 10, drawY + 8, AppleColors.TEXT_PRIMARY);
        ctx.text(font, open ? "settings ▴" : "settings ▾", card.x + card.w - 56, drawY + 20, AppleColors.TEXT_SECONDARY);

        if (clicked && headerHover) {
            if (open) { expanded.remove(e); if (e instanceof ColorEntry) expandedColorEntry = null; }
            else { expanded.add(e); }
            return; // consume the click; don't pass to the inner control this frame
        }

        if (open) {
            // Render the underlying entry's control inside the card, below the header.
            int innerX = card.x + 4;
            int innerW = card.w - 8;
            e.reposition(innerX, innerW);
            int controlY = drawY + headerH - 2;
            ctx.fill(card.x + 6, drawY + headerH - 4, card.x + card.w - 6, drawY + headerH - 3, AppleColors.SEPARATOR);
            e.render(ctx, this, controlY, mouseX, mouseY, clicked, mouseDown);
        }
    }

    private void renderToggleCard(GuiGraphicsExtractor ctx, ToggleEntry t, Card card, int drawY,
                                  int mouseX, int mouseY, boolean clicked, boolean hovered) {
        boolean value = t.get();
        if (clicked && hovered) { t.toggle(); value = t.get(); }

        // Left accent strip reflects state.
        ctx.fill(card.x, drawY, card.x + 3, drawY + card.h, value ? AppleColors.ACCENT_GREEN : AppleColors.TOGGLE_OFF);

        String label = t.getLabel();
        ctx.text(font, fitLabel(label, card.x + 12, card.x + card.w - 38), card.x + 12, drawY + 8, AppleColors.TEXT_PRIMARY);

        // ON/OFF pill bottom-right.
        String state = value ? "ON" : "OFF";
        int stateW = font.width(state) + 12;
        int pillX = card.x + card.w - stateW - 8;
        int pillY = drawY + card.h - 16;
        ctx.fill(pillX, pillY, pillX + stateW, pillY + 11, value ? AppleColors.withAlpha(AppleColors.ACCENT_GREEN, 0.30f) : 0xFF3A3A3C);
        ctx.text(font, state, pillX + 6, pillY + 2, value ? AppleColors.ACCENT_GREEN : AppleColors.TEXT_SECONDARY);
    }

    private void renderBottomBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
        int barY = this.height - BOTTOM_BAR_H;
        ctx.fill(0, barY, this.width, this.height, 0xFF242426);
        ctx.fill(0, barY, this.width, barY + 1, AppleColors.SEPARATOR);

        // "Old GUI" switch (left).
        int oldX = MARGIN, oldY = barY + 7, oldW = 88, oldH = 22;
        boolean oldHover = mouseX >= oldX && mouseX < oldX + oldW && mouseY >= oldY && mouseY < oldY + oldH;
        if (clicked && oldHover && minecraft.player != null) {
            minecraft.player.connection.sendCommand("tmap");
            return;
        }
        ctx.fill(oldX, oldY, oldX + oldW, oldY + oldH, oldHover ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
        drawBorder(ctx, oldX, oldY, oldW, oldH, oldHover ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.centeredText(font, "Classic GUI", oldX + oldW / 2, oldY + 7, AppleColors.TEXT_SECONDARY);

        // Done (right).
        int doneW = 70, doneX = this.width - MARGIN - doneW, doneY = barY + 7, doneH = 22;
        boolean doneHover = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= doneY && mouseY < doneY + doneH;
        if (clicked && doneHover) { onClose(); return; }
        ctx.fill(doneX, doneY, doneX + doneW, doneY + doneH, doneHover ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
        drawBorder(ctx, doneX, doneY, doneW, doneH, doneHover ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.centeredText(font, "Done", doneX + doneW / 2, doneY + 7, AppleColors.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        // Tab bar: horizontal scroll.
        if (mouseY >= TAB_BAR_Y && mouseY < TAB_BAR_Y + TAB_BAR_H && tabMaxScroll > 0) {
            tabScroll = (int) Math.max(0, Math.min(tabMaxScroll, tabScroll - v * 24));
            return true;
        }
        // Content: forward to a hovered slider (scroll-to-adjust), else scroll the grid.
        int contentTop = CONTENT_TOP;
        for (Card card : layout) {
            if (card.entry.isLabel() || card.entry.isToggle()) continue;
            if (!expanded.contains(card.entry)) continue;
            int drawY = contentTop + card.y - scrollOffset;
            if (mouseY >= drawY && mouseY < drawY + card.h) {
                if (card.entry.scrollAt((int) mouseX, (int) mouseY, drawY + CARD_H - 2, v)) return true;
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
