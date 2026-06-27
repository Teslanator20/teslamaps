/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue.Line;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ItemValueOverlay {

    private record PanelLine(String left, String right) {}

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().itemValueGui) return false;
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Slot hovered = acc.getFocusedSlot();
        if (hovered == null || hovered.getItem().isEmpty()) return;

        List<PanelLine> lines = buildLines(hovered.getItem());
        if (lines == null) return;
        drawPanelBeside(ctx, mc, lines, acc.getX(), acc.getY(), acc.getImageWidth());
    }

    private static List<PanelLine> buildLines(ItemStack item) {
        if (!TeslaMapsConfig.get().itemValueGui || item == null || item.isEmpty()) return null;
        PriceManager.ensureFresh();
        List<Line> rows = EstimatedValue.breakdown(item);
        if (rows.isEmpty()) return null;

        List<PanelLine> lines = new ArrayList<>();
        lines.add(new PanelLine(item.getHoverName().getString(), "§a§l" + EstimatedValue.fmt(EstimatedValue.compute(item))));
        lines.add(null); // separator
        for (Line r : rows) {
            lines.add(new PanelLine(r.indent() ? "  " + r.label() : r.label(), r.value()));
        }
        return lines;
    }

    // fixed panel just left of the inventory GUI (flips to the right if there's no room)
    private static void drawPanelBeside(GuiGraphicsExtractor ctx, Minecraft mc, List<PanelLine> lines, int guiX, int guiY, int guiW) {
        int pad = 5;
        int gap = 16;
        int contentW = 0;
        for (PanelLine l : lines) {
            if (l == null) continue;
            contentW = Math.max(contentW, mc.font.width(l.left()) + gap + mc.font.width(l.right()));
        }
        int panelW = contentW + pad * 2;
        int panelH = lines.size() * 10 + pad * 2;

        int screenH = mc.getWindow().getGuiScaledHeight();
        int panelX = guiX - panelW - 6;
        if (panelX < 2) panelX = guiX + guiW + 6;
        int panelY = guiY;
        if (panelY + panelH > screenH - 2) panelY = Math.max(2, screenH - 2 - panelH);

        drawPanel(ctx, mc, lines, panelX, panelY, panelW, panelH);
    }

    private static void drawPanel(GuiGraphicsExtractor ctx, Minecraft mc, List<PanelLine> lines, int panelX, int panelY, int panelW, int panelH) {
        int pad = 5;
        // background + subtle purple frame (SkyHanni-ish)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF0141118);
        drawBorder(ctx, panelX, panelY, panelW, panelH, 0xFF6A4AC0);

        int y = panelY + pad;
        boolean drewSeparator = false;
        for (PanelLine l : lines) {
            if (l == null) {
                // separator line under the header
                if (!drewSeparator) {
                    ctx.fill(panelX + pad, y + 4, panelX + panelW - pad, y + 5, 0x40FFFFFF);
                    drewSeparator = true;
                }
                y += 10;
                continue;
            }
            ctx.text(mc.font, l.left(), panelX + pad, y, 0xFFFFFFFF);
            int rw = mc.font.width(l.right());
            ctx.text(mc.font, l.right(), panelX + panelW - pad - rw, y, 0xFFFFFFFF);
            y += 10;
        }
    }

    private static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
