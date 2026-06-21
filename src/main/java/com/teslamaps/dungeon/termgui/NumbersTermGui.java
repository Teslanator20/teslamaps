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
import com.teslamaps.dungeon.puzzle.ClickInOrderTerminal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class NumbersTermGui extends CustomTermGui {
    private static final int TRANSPARENT = 0x00000000;

    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Will be filled in renderTerminal
    }

    @Override
    public void renderTerminal(GuiGraphicsExtractor context, int slotCount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return;

        renderBackground(context, slotCount, 7, 2);

        Map<Integer, Integer> slotToNumber = new HashMap<>();
        List<Integer> orderedSlots = new ArrayList<>();

        for (Slot slot : screen.getMenu().slots) {
            if (slot.index >= slotCount) continue; // Skip player inventory

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                int number = stack.getCount();
                if (number >= 1 && number <= 14) {
                    slotToNumber.put(slot.index, number);
                }
            }
        }

        for (int i = 1; i <= 14; i++) {
            for (Map.Entry<Integer, Integer> entry : slotToNumber.entrySet()) {
                if (entry.getValue() == i) {
                    orderedSlots.add(entry.getKey());
                    break;
                }
            }
        }

        for (int index = 9; index < slotCount; index++) {
            if ((index % 9) == 0 || (index % 9) == 8) continue;

            int solutionIndex = orderedSlots.indexOf(index);
            int amount = orderedSlots.size() - solutionIndex;

            int color;
            if (solutionIndex == 0) {
                color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiOrderColor1);
            } else if (solutionIndex == 1) {
                color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiOrderColor2);
            } else if (solutionIndex == 2) {
                color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiOrderColor3);
            } else {
                color = TRANSPARENT;
            }

            float[] pos = renderSlot(context, index, color);

            if (TeslaMapsConfig.get().terminalGuiShowNumbers && solutionIndex != -1) {
                float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
                drawTextCentered(context, String.valueOf(amount), pos[0], pos[1], slotSize, 0xFFFFFFFF);
            }
        }
    }
}
