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
import com.teslamaps.dungeon.puzzle.RubixTerminal;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

public class RubixTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Not used for this terminal
    }

    @Override
    public void renderTerminal(GuiGraphicsExtractor context, int slotCount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return;

        renderBackground(context, slotCount, 3, 2);

        Map<Integer, Integer> clickMap = RubixTerminal.getClickMap();

        for (Map.Entry<Integer, Integer> entry : clickMap.entrySet()) {
            int slot = entry.getKey();
            int clicksRequired = entry.getValue();

            if (clicksRequired == 0) continue;

            int color = getColor(clicksRequired);

            float[] pos = renderSlot(context, slot, color);

            float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
            String text = String.valueOf(Math.abs(clicksRequired));
            drawTextCentered(context, text, pos[0], pos[1], slotSize, 0xFFFFFFFF);
        }
    }

    private int getColor(int clicksRequired) {
        if (clicksRequired == 1) {
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor1);
        } else if (clicksRequired == 2) {
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor2);
        } else if (clicksRequired == -1) {
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor1);
        } else {
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor2);
        }
    }
}
