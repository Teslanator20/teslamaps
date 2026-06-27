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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Odin-style ClickGUI: one free-floating, draggable Panel per category.
 *
 * Faithful to Odin's ClickGUI/Panel behavior (left-drag header to move,
 * right-click header to expand/collapse, per-panel scroll). Visuals are
 * approximated with vanilla GuiGraphicsExtractor rects + bitmap font — Odin's
 * NanoVG (rounded corners, drop shadows, SVG icons, custom fonts) is not
 * available, so corners are sharp and chevrons are text glyphs.
 *
 * Reuses MapConfigScreen's category/entry definitions and config wiring verbatim
 * (buildCategories, entry.reposition/render, fitLabel, drawBorder, AppleColors);
 * only the layout/chrome differs.
 */
public class MapConfigScreen4 extends MapConfigScreen {

    // Panel header dimensions (slightly thinner than Odin's 240 so a 5th column fits).
    private static final int PANEL_WIDTH = 200;
    private static final int HEADER_H = 32;

    // Single top row: panel i at x = 10 + 214*i, y = 10.
    private static final int PANEL_START_X = 10;
    private static final int PANEL_START_Y = 10;
    private static final int PANEL_STEP_X = 214;   // 200 width + 14 gap

    private static final int ENTRY_INDENT = 6;   // entries sit slightly inset from the panel edge
    private static final int PANEL_BG = 0xFF1A1A1A;   // Odin gray26 (26,26,26)
    private static final int PANEL_HEADER = 0xFF262626; // Odin gray38-ish header tint (38,38,38)

    // Consolidated super-panels: map of super-panel name -> ordered member category names.
    // Each member category becomes a bold section label inside the super-panel body.
    private final Map<String, List<String>> groups = new LinkedHashMap<>();
    // Built body entries per super-panel (section labels + member entries concatenated).
    private final Map<String, List<SettingsEntry>> groupEntries = new LinkedHashMap<>();

    // Persist panel positions/expansion across opens within a session (not saved to disk).
    private static final Map<String, float[]> SAVED_POS = new HashMap<>(); // name -> {x, y}
    private static final Map<String, Boolean> SAVED_OPEN = new HashMap<>();

    private final List<Panel> panels = new ArrayList<>();
    private Panel dragging = null;
    private double dragDX, dragDY;

    // Per-frame edge-trigger of the left button, mirroring gui2/gui3, so inherited
    // entry.render() can self-handle its left-click interactions.
    private boolean entryClickThisFrame = false;

    // Collapsible sections: right-click a section label to hide/show its entries.
    private static final java.util.Set<String> COLLAPSED = new java.util.HashSet<>(); // keys: panel#labelOrdinal
    private final java.util.Set<SettingsEntry> memberHeaders =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()); // gui4-inserted category labels = level 0
    private Panel rightClickPanel = null;   // panel whose body was right-clicked this frame
    private double rightClickX, rightClickY;

    private final class Panel {
        final String category;
        float x, y;
        boolean open;
        int scrollOffset = 0;   // <= 0, scrolls the body up
        int bodyContentH = 0;   // total height of all entries (pre-clip)
        int bodyVisibleH = 0;   // clamped/clipped body height actually drawn
        final List<SettingsEntry> shownEntries = new ArrayList<>(); // last-rendered rows (collapse-aware)
        final List<Integer> shownY = new ArrayList<>();             // their y relative to bodyTop (pre-scroll)

        Panel(String category, float x, float y, boolean open) {
            this.category = category; this.x = x; this.y = y; this.open = open;
        }

        List<SettingsEntry> visibleEntries() {
            List<SettingsEntry> all = groupEntries.get(category);
            if (all == null) return List.of();
            if (searchQuery.isEmpty()) return all;
            List<SettingsEntry> out = new ArrayList<>();
            for (SettingsEntry e : all) {
                if (e.isLabel()) continue;
                if (e.matchesSearch(searchQuery)) out.add(e);
            }
            return out;
        }

        boolean headerHovered(double mx, double my) {
            return mx >= x && mx < x + PANEL_WIDTH && my >= y && my < y + HEADER_H;
        }

        boolean bodyHovered(double mx, double my) {
            return open && mx >= x && mx < x + PANEL_WIDTH
                    && my >= y + HEADER_H && my < y + HEADER_H + bodyVisibleH;
        }
    }

    private static boolean defaultsSeeded = false;

    @Override
    protected void init() {
        buildCategories();
        buildGroups();
        buildPanels();
        searchQuery = ""; // no search bar in this layout — show everything

        // Collapse all sections by default (once per session); user toggles persist after.
        if (!defaultsSeeded) {
            for (Map.Entry<String, List<SettingsEntry>> g : groupEntries.entrySet()) {
                int ord = 0;
                for (SettingsEntry e : g.getValue()) {
                    if (e.isLabel()) COLLAPSED.add(g.getKey() + "#" + ord++);
                }
            }
            defaultsSeeded = true;
        }
    }

    // Consolidate buildCategories()'s ~20 categories into 4 super-panels (like Odin's ~5).
    // Each member category becomes a bold section label, then its own entries.
    private void buildGroups() {
        groups.clear();
        groupEntries.clear();

        groups.put("Map", new ArrayList<>(List.of(
                "Map", "Score & Splits", "Waypoints")));
        groups.put("Dungeon", new ArrayList<>(List.of(
                "Puzzles", "Timers", "Blood Camp", "Leap", "Slayer")));
        groups.put("ESP", new ArrayList<>(List.of(
                "ESP", "Dragons", "Hide")));
        groups.put("Visual", new ArrayList<>(List.of(
                "Render", "Sounds", "Colors")));
        groups.put("Items", new ArrayList<>(List.of(
                "Croesus", "Inventory", "Tooltips & Value")));
        groups.put("Misc", new ArrayList<>(List.of(
                "Auto", "Party", "Chat")));

        // Any category not assigned to a group falls into Misc so nothing is dropped.
        java.util.Set<String> assigned = new java.util.HashSet<>();
        for (List<String> members : groups.values()) assigned.addAll(members);
        for (String cat : categories.keySet()) {
            if (!assigned.contains(cat)) groups.get("Misc").add(cat);
        }

        // Build the concatenated body for each super-panel.
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            List<SettingsEntry> body = new ArrayList<>();
            for (String cat : g.getValue()) {
                List<SettingsEntry> catEntries = categories.get(cat);
                if (catEntries == null) continue;
                LabelEntry header = new LabelEntry(ENTRY_INDENT, cat); // level-0 section header
                memberHeaders.add(header);
                body.add(header);
                body.addAll(catEntries);
            }
            groupEntries.put(g.getKey(), body);
        }
    }

    private void buildPanels() {
        panels.clear();
        // Lay panels left-to-right, wrapping to a new row when they'd run off-screen (avoids the
        // clamp stacking them on top of each other when more panels than fit one row).
        int cols = Math.max(1, (this.width - PANEL_START_X) / PANEL_STEP_X);
        int rowStep = 150; // vertical spacing per wrapped row (panels start collapsed/short)
        int i = 0;
        for (String name : groupEntries.keySet()) {
            float dx, dy;
            float[] saved = SAVED_POS.get(name);
            if (saved != null) {
                dx = saved[0]; dy = saved[1];
            } else {
                dx = PANEL_START_X + PANEL_STEP_X * (i % cols);
                dy = PANEL_START_Y + rowStep * (i / cols);
            }
            // Keep panels on-screen even after a resolution change.
            dx = Math.max(0, Math.min(dx, this.width - PANEL_WIDTH));
            dy = Math.max(0, Math.min(dy, this.height - HEADER_H));
            boolean open = SAVED_OPEN.getOrDefault(name, Boolean.TRUE);
            panels.add(new Panel(name, dx, dy, open));
            i++;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        // Dim the world behind.
        ctx.fill(0, 0, this.width, this.height, 0xC0101012);

        // ----- Top strip: title + buttons -----
        ctx.text(font, "TeslaMaps", 14, 8, AppleColors.TEXT_PRIMARY);

        boolean clicked = renderTopButtons(ctx, mouseX, mouseY, isMouseDown);
        if (clicked) { wasMouseDown = isMouseDown; return; } // a top button consumed the click

        // Pass the left-click edge to inner entries only when we aren't dragging a panel.
        boolean entryClicked = entryClickThisFrame && dragging == null;
        entryClickThisFrame = false;

        // Draw all panels; the dragged one last so it sits on top.
        for (Panel p : panels) {
            if (p == dragging) continue;
            drawPanel(ctx, p, mouseX, mouseY, entryClicked, isMouseDown);
        }
        if (dragging != null) drawPanel(ctx, dragging, mouseX, mouseY, false, isMouseDown);

        rightClickPanel = null; // consumed by the panel layout pass above
        wasMouseDown = isMouseDown;
        renderWidgetsOnly(ctx, mouseX, mouseY, delta);
    }

    // Done + Classic GUI buttons, top-right. Returns true if a button was clicked this frame.
    private boolean renderTopButtons(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean isMouseDown) {
        boolean clickEdge = isMouseDown && !wasMouseDown;
        int btnY = 6, btnH = 18;

        int doneW = 60, doneX = this.width - 14 - doneW;
        boolean doneHover = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH;
        ctx.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHover ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
        drawBorder(ctx, doneX, btnY, doneW, btnH, doneHover ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.centeredText(font, "Done", doneX + doneW / 2, btnY + 5, AppleColors.TEXT_PRIMARY);

        int oldW = 80, oldX = doneX - oldW - 6;
        boolean oldHover = mouseX >= oldX && mouseX < oldX + oldW && mouseY >= btnY && mouseY < btnY + btnH;
        ctx.fill(oldX, btnY, oldX + oldW, btnY + btnH, oldHover ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
        drawBorder(ctx, oldX, btnY, oldW, btnH, oldHover ? AppleColors.ACCENT_GREEN : AppleColors.INPUT_BORDER);
        ctx.centeredText(font, "Classic GUI", oldX + oldW / 2, btnY + 5, AppleColors.TEXT_SECONDARY);

        if (clickEdge && doneHover) { onClose(); return true; }
        if (clickEdge && oldHover && minecraft.player != null) {
            minecraft.player.connection.sendCommand("tmap");
            return true;
        }
        return false;
    }

    private void drawPanel(GuiGraphicsExtractor ctx, Panel p, int mouseX, int mouseY,
                           boolean entryClicked, boolean isMouseDown) {
        int px = Math.round(p.x), py = Math.round(p.y);

        // Drop-shadow approximation: a soft dark rect offset behind the panel.
        ctx.fill(px + 2, py + 2, px + PANEL_WIDTH + 2, py + HEADER_H + 2, 0x60000000);

        // ----- Header -----
        boolean headerHover = p.headerHovered(mouseX, mouseY);
        ctx.fill(px, py, px + PANEL_WIDTH, py + HEADER_H, headerHover ? PANEL_HEADER : PANEL_BG);
        drawBorder(ctx, px, py, PANEL_WIDTH, HEADER_H, AppleColors.INPUT_BORDER);
        // Left accent strip when open.
        if (p.open) ctx.fill(px, py, px + 3, py + HEADER_H, AppleColors.ACCENT_GREEN);
        // Chevron + centered category name.
        ctx.text(font, p.open ? "[-]" : "[+]", px + 6, py + (HEADER_H - 8) / 2, AppleColors.TEXT_SECONDARY);
        ctx.centeredText(font, p.category, px + PANEL_WIDTH / 2, py + (HEADER_H - 8) / 2, AppleColors.TEXT_PRIMARY);

        if (!p.open) { p.bodyVisibleH = 0; return; }

        // ----- Body (entries) -----
        List<SettingsEntry> entries = p.visibleEntries();
        int entryX = px + ENTRY_INDENT;
        int entryW = PANEL_WIDTH - ENTRY_INDENT * 2;

        // Build the shown rows honoring 2-level section collapse:
        // level 0 = gui4-inserted category label, level 1 = a category's own internal sub-label.
        // A collapsed label hides following entries (and lower-level labels) until the next label of <= its level.
        p.shownEntries.clear();
        p.shownY.clear();
        List<String> shownKeys = new ArrayList<>();
        int collapseLevel = Integer.MAX_VALUE;
        int ord = 0, total = 0;
        for (SettingsEntry e : entries) {
            if (e.isLabel()) {
                int lvl = memberHeaders.contains(e) ? 0 : 1;
                String key = p.category + "#" + ord++;
                if (lvl <= collapseLevel) {
                    collapseLevel = COLLAPSED.contains(key) ? lvl : Integer.MAX_VALUE;
                    p.shownEntries.add(e); p.shownY.add(total); shownKeys.add(key);
                    total += e.getHeight();
                }
            } else if (collapseLevel == Integer.MAX_VALUE) {
                p.shownEntries.add(e); p.shownY.add(total); shownKeys.add(null);
                total += e.getHeight();
            }
        }
        p.bodyContentH = total;

        // Let the panel run long downward; only clip to whatever room is left below the header.
        int roomBelow = Math.max(HEADER_H, this.height - (py + HEADER_H) - 4);
        p.bodyVisibleH = Math.min(total, roomBelow);
        int minScroll = Math.min(0, p.bodyVisibleH - total);
        if (p.scrollOffset < minScroll) p.scrollOffset = minScroll;
        if (p.scrollOffset > 0) p.scrollOffset = 0;

        int bodyTop = py + HEADER_H;
        int bodyBottom = bodyTop + p.bodyVisibleH;

        ctx.fill(px, bodyTop, px + PANEL_WIDTH, bodyBottom, PANEL_BG);
        drawBorder(ctx, px, bodyTop, PANEL_WIDTH, p.bodyVisibleH, AppleColors.INPUT_BORDER);

        ctx.enableScissor(px, bodyTop, px + PANEL_WIDTH, bodyBottom);
        for (int i = 0; i < p.shownEntries.size(); i++) {
            SettingsEntry e = p.shownEntries.get(i);
            int h = e.getHeight();
            int dY = bodyTop + p.scrollOffset + p.shownY.get(i);
            if (dY + h < bodyTop - 30 || dY > bodyBottom + 30) continue;
            String key = shownKeys.get(i);
            boolean rowHover = mouseX >= entryX && mouseX < entryX + entryW && mouseY >= dY && mouseY < dY + h;
            if (key != null) {
                boolean lvl0 = memberHeaders.contains(e);
                String cat = e.getLabel();
                boolean gated = lvl0; // every category section has a runtime master (AND-gate)
                // right-click toggles collapse
                if (p == rightClickPanel && rightClickX >= px && rightClickX < px + PANEL_WIDTH
                        && rightClickY >= dY && rightClickY < dY + h) {
                    if (!COLLAPSED.remove(key)) COLLAPSED.add(key);
                }
                // left-click: gated header toggles its runtime master, otherwise collapses
                if (entryClicked && rowHover) {
                    if (gated) {
                        TeslaMapsConfig.get().sectionEnabled.put(cat, !TeslaMapsConfig.get().section(cat));
                        TeslaMapsConfig.save();
                    } else if (!COLLAPSED.remove(key)) {
                        COLLAPSED.add(key);
                    }
                }
                drawSectionHeader(ctx, entryX, entryW, dY, h, cat, COLLAPSED.contains(key), lvl0,
                        gated, !gated || TeslaMapsConfig.get().section(cat));
            } else if (e.isToggle()) {
                ToggleEntry te = (ToggleEntry) e;
                if (entryClicked && rowHover) { te.toggle(); TeslaMapsConfig.save(); }
                drawOdinToggle(ctx, entryX, entryW, dY, h, te.getLabel(), te.get(), rowHover);
            } else {
                e.reposition(entryX, entryW);
                e.render(ctx, this, dY, mouseX, mouseY, entryClicked, isMouseDown);
            }
        }
        ctx.disableScissor();

        // Per-panel scrollbar.
        if (total > p.bodyVisibleH) {
            int sbX = px + PANEL_WIDTH - 3;
            int trackH = p.bodyVisibleH;
            int thumbH = Math.max(16, (int) ((float) trackH * trackH / total));
            float prog = (float) (-p.scrollOffset) / (total - p.bodyVisibleH);
            int thumbY = bodyTop + (int) ((trackH - thumbH) * prog);
            ctx.fill(sbX, bodyTop, sbX + 3, bodyBottom, 0x40000000);
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFF5A5A5C);
        }
    }

    // Odin-style boolean: the whole row is a button that highlights green when enabled (click toggles).
    private void drawOdinToggle(GuiGraphicsExtractor ctx, int entryX, int entryW, int drawY, int h,
                                String label, boolean on, boolean hover) {
        int top = drawY + 1, bot = drawY + h - 1;
        if (on) {
            ctx.fill(entryX, top, entryX + entryW, bot, AppleColors.ACCENT_GREEN);
            if (hover) ctx.fill(entryX, top, entryX + entryW, bot, 0x22FFFFFF); // brighten on hover
        } else if (hover) {
            ctx.fill(entryX, top, entryX + entryW, bot, 0xFF2E2E30);
        }
        ctx.text(font, label, entryX + 5, drawY + (h - 8) / 2, AppleColors.TEXT_PRIMARY);
    }

    // Collapsible section header: chevron + label. Right-click = collapse; gated headers left-click = master on/off.
    // Gated header is a green button when its master is on, gray when off (its features only work when on).
    private void drawSectionHeader(GuiGraphicsExtractor ctx, int entryX, int entryW, int drawY, int h,
                                   String label, boolean collapsed, boolean level0, boolean gated, boolean masterOn) {
        int bg = gated ? (masterOn ? AppleColors.ACCENT_GREEN : 0xFF3A3A3C)
                       : (level0 ? 0xFF2A2A2C : 0xFF202022);
        ctx.fill(entryX, drawY, entryX + entryW, drawY + h, bg);
        String chev = collapsed ? "[+]" : "[-]";
        int color = gated ? AppleColors.TEXT_PRIMARY : (level0 ? AppleColors.ACCENT_GREEN : AppleColors.TEXT_SECONDARY);
        ctx.text(font, chev + " " + label, entryX + 3, drawY + (h - 8) / 2, color);
    }

    private void renderWidgetsOnly(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        for (var w : this.children()) {
            if (w instanceof net.minecraft.client.gui.components.Renderable r) {
                r.extractRenderState(ctx, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x(), my = event.y();
        int button = event.button(); // 0 = left, 1 = right

        // Topmost panel first (panels later in the list draw on top).
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (p.headerHovered(mx, my)) {
                if (button == 0) {
                    dragging = p;
                    dragDX = p.x - mx;
                    dragDY = p.y - my;
                    bringToFront(i);
                } else if (button == 1) {
                    p.open = !p.open;
                    SAVED_OPEN.put(p.category, p.open);
                    expandedColorEntry = null;
                }
                if (searchField != null) searchField.setFocused(false);
                return true;
            }
            if (p.bodyHovered(mx, my) && button == 0) {
                // Defer to inner entry handling in the next render frame (edge-triggered).
                entryClickThisFrame = true;
                bringToFront(i);
                return super.mouseClicked(event, bl);
            }
            if (p.bodyHovered(mx, my) && button == 1) {
                // Right-click a section label to collapse/expand it (handled in the render layout pass).
                rightClickPanel = p;
                rightClickX = mx;
                rightClickY = my;
                bringToFront(i);
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    private void bringToFront(int idx) {
        Panel p = panels.remove(idx);
        panels.add(p);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            SAVED_POS.put(dragging.category, new float[]{dragging.x, dragging.y});
            dragging = null;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragging.x = (float) Math.max(0, Math.min(event.x() + dragDX, this.width - PANEL_WIDTH));
            dragging.y = (float) Math.max(0, Math.min(event.y() + dragDY, this.height - HEADER_H));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        // Scroll only affects the hovered panel's body. Forward to a hovered slider first.
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (!p.bodyHovered(mouseX, mouseY)) continue;

            // Try scroll-to-adjust on the entry under the cursor (uses the last-rendered collapse-aware layout).
            int entryX = Math.round(p.x) + ENTRY_INDENT;
            int entryW = PANEL_WIDTH - ENTRY_INDENT * 2;
            int bodyTop = Math.round(p.y) + HEADER_H;
            for (int j = 0; j < p.shownEntries.size(); j++) {
                SettingsEntry e = p.shownEntries.get(j);
                int drawY = bodyTop + p.scrollOffset + p.shownY.get(j);
                int eh = e.getHeight();
                if (mouseY >= drawY && mouseY < drawY + eh) {
                    e.reposition(entryX, entryW);
                    if (e.scrollAt((int) mouseX, (int) mouseY, drawY, v)) return true;
                    break;
                }
            }

            // Otherwise scroll the panel body.
            if (p.bodyContentH > p.bodyVisibleH) {
                int minScroll = Math.min(0, p.bodyVisibleH - p.bodyContentH);
                p.scrollOffset = (int) Math.max(minScroll, Math.min(0, p.scrollOffset + v * 18));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public void resize(int w, int h) {
        // Persist current positions before the rebuild (init re-clamps to the new size).
        for (Panel p : panels) SAVED_POS.put(p.category, new float[]{p.x, p.y});
        super.resize(w, h);
    }

    @Override
    public void onClose() {
        for (Panel p : panels) {
            SAVED_POS.put(p.category, new float[]{p.x, p.y});
            SAVED_OPEN.put(p.category, p.open);
        }
        TeslaMapsConfig.save();
        super.onClose();
    }
}
