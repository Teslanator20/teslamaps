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
package com.teslamaps.dungeon.termsim;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PanesSimulator extends TerminalSimulator {

    public PanesSimulator() {
        super("Correct all the panes!", 5, 9);
    }

    @Override
    protected void initializeTerminal() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = getSlotIndex(row, col);

                if (col == 0 || col == 1 || col == 7 || col == 8) {
                    slots[idx] = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                } else {
                    boolean isRed = random.nextBoolean();
                    slots[idx] = new ItemStack(isRed ? Items.RED_STAINED_GLASS_PANE : Items.LIME_STAINED_GLASS_PANE);
                }
            }
        }
    }

    @Override
    protected boolean onSlotClick(int slotIndex, int button) {
        int col = getCol(slotIndex);

        if (col == 0 || col == 1 || col == 7 || col == 8) {
            return false;
        }

        ItemStack stack = slots[slotIndex];

        if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
            slots[slotIndex] = new ItemStack(Items.LIME_STAINED_GLASS_PANE);
            return true;
        } else if (stack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
            slots[slotIndex] = new ItemStack(Items.RED_STAINED_GLASS_PANE);
            return true;
        }

        return false;
    }

    @Override
    protected boolean checkSolved() {
        for (int row = 0; row < rows; row++) {
            for (int col = 2; col < 7; col++) {
                int idx = getSlotIndex(row, col);
                if (slots[idx].getItem() == Items.RED_STAINED_GLASS_PANE) {
                    return false;
                }
            }
        }
        return true;
    }
}
