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
package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.SelectAllTerminal;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

public class SelectAllTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Not used for this terminal
    }

    @Override
    public void renderTerminal(GuiGraphicsExtractor context, int slotCount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return;

        renderBackground(context, slotCount, 9, 2);

        List<Integer> correctSlots = SelectAllTerminal.getCorrectSlots();
        Set<Integer> clickedSlots = SelectAllTerminal.getClickedSlots();
        String targetColor = SelectAllTerminal.getTargetColor();

        for (int slot : correctSlots) {
            boolean isClicked = clickedSlots.contains(slot);

            int color;
            if (isClicked) {
                color = 0x44555555;
            } else {
                color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiSelectColor);
            }

            float[] pos = renderSlot(context, slot, color);

            if (targetColor != null && TeslaMapsConfig.get().terminalGuiShowNumbers) {
                float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
                String text = targetColor.substring(0, 1);
                drawTextCentered(context, text, pos[0], pos[1], slotSize,
                    isClicked ? 0x77FFFFFF : 0xFFFFFFFF);
            }
        }
    }
}
