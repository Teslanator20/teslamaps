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
import com.teslamaps.dungeon.puzzle.MelodyTerminal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

public class MelodyTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Not used for this terminal
    }

    @Override
    public void renderTerminal(GuiGraphicsExtractor context, int slotCount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return;

        renderBackground(context, 44, 7, 3);

        int currentLane = MelodyTerminal.getCurrentLane();
        int lastGreenPos = MelodyTerminal.getLastGreenPosition();
        int purplePos = MelodyTerminal.getPurplePosition();

        for (int index = 0; index < 44; index++) {
            int column = index % 9;
            int row = index / 9;

            int color;

            if (row == 0) {
                if (purplePos != -1 && index == purplePos) {
                    color = 0xFFFF00FF; // Bright magenta
                } else {
                    continue; // Skip other top row slots
                }
            } else if (column == 7 && row >= 1 && row <= 4) {
                if (row - 1 == currentLane && purplePos != -1) {
                    color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiMelodyColor);
                } else {
                    color = 0x44888888; // Gray background for inactive lanes
                }
            } else if (column >= 1 && column <= 6 && row >= 1 && row <= 4) {
                int currentLaneRow = currentLane + 1; // Lane 0 = row 1, lane 1 = row 2, etc.
                if (row == currentLaneRow && lastGreenPos != -1 && column == lastGreenPos) {
                    color = 0xFF00FF00; // Bright green
                } else if (row == currentLaneRow) {
                    color = 0x44888888;
                } else {
                    continue; // Skip other lanes
                }
            } else {
                continue; // Skip other slots
            }

            renderSlot(context, index, color);
        }
    }
}
