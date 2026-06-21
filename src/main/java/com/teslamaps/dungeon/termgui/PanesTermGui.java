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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PanesTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Will be filled in renderTerminal
    }

    @Override
    public void renderTerminal(GuiGraphicsExtractor context, int slotCount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return;

        renderBackground(context, slotCount, 7, 2);

        List<Integer> redPanes = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.index >= slotCount) continue;

            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                redPanes.add(slot.index);
            }
        }

        for (int index = 9; index < slotCount; index++) {
            if ((index % 9) == 0 || (index % 9) == 8) continue;

            int color = redPanes.contains(index) ?
                       TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiPanesColor) :
                       0x00000000; // Transparent

            renderSlot(context, index, color);
        }
    }
}
