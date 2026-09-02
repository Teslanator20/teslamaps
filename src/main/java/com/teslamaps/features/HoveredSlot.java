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
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public class HoveredSlot {

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hoveredSlotOutline) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        for (Slot slot : cs.getMenu().slots) {
            if (!slot.isActive()) continue;
            int sx = left + slot.x, sy = top + slot.y;
            if (mouseX < sx || mouseX >= sx + 16 || mouseY < sy || mouseY >= sy + 16) continue;
            draw(ctx, sx, sy);
            return;
        }
    }

    public static void draw(GuiGraphicsExtractor ctx, int sx, int sy) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int col = TeslaMapsConfig.parseColor(c.colorHoveredSlot);
        int t = Math.max(1, c.hoveredSlotThickness);
        ctx.fill(sx - t, sy - t, sx + 16 + t, sy, col);
        ctx.fill(sx - t, sy + 16, sx + 16 + t, sy + 16 + t, col);
        ctx.fill(sx - t, sy, sx, sy + 16, col);
        ctx.fill(sx + 16, sy, sx + 16 + t, sy + 16, col);
        if (c.hoveredSlotFill > 0) {
            int a = (int) (Math.min(1f, c.hoveredSlotFill) * 255) << 24;
            ctx.fill(sx, sy, sx + 16, sy + 16, a | (col & 0x00FFFFFF));
        }
    }
}
