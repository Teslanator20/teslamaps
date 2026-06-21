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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class NumbersSimulator extends TerminalSimulator {
    private int nextNumber = 1;

    public NumbersSimulator() {
        super("Click in order!", 4, 9);
    }

    @Override
    protected void initializeTerminal() {
        nextNumber = 1;

        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        }

        List<Integer> validSlots = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 1; col < cols - 1; col++) {
                validSlots.add(getSlotIndex(row, col));
            }
        }
        Collections.shuffle(validSlots, random);

        for (int num = 1; num <= 14; num++) {
            int slotIdx = validSlots.get(num - 1);
            ItemStack stack = new ItemStack(Items.RED_STAINED_GLASS_PANE, num);
            slots[slotIdx] = stack;
        }
    }

    @Override
    protected boolean onSlotClick(int slotIndex, int button) {
        ItemStack stack = slots[slotIndex];

        if (stack.getItem() != Items.RED_STAINED_GLASS_PANE) {
            return false;
        }

        int number = stack.getCount();

        if (number != nextNumber) {
            return false;
        }

        slots[slotIndex] = new ItemStack(Items.LIME_STAINED_GLASS_PANE, number);
        nextNumber++;

        return true;
    }

    @Override
    protected boolean checkSolved() {
        return nextNumber > 14;
    }
}
