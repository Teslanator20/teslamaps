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

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class RubixSimulator extends TerminalSimulator {
    private static final Item[] COLOR_CYCLE = {
            Items.RED_STAINED_GLASS_PANE,
            Items.ORANGE_STAINED_GLASS_PANE,
            Items.YELLOW_STAINED_GLASS_PANE,
            Items.GREEN_STAINED_GLASS_PANE,
            Items.BLUE_STAINED_GLASS_PANE
    };

    private static final int[] GRID_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};

    public RubixSimulator() {
        super("Change all to same color!", 5, 9);
    }

    @Override
    protected void initializeTerminal() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        }

        for (int slot : GRID_SLOTS) {
            int colorIdx = random.nextInt(COLOR_CYCLE.length);
            slots[slot] = new ItemStack(COLOR_CYCLE[colorIdx]);
        }
    }

    @Override
    protected boolean onSlotClick(int slotIndex, int button) {
        boolean isGridSlot = false;
        for (int gs : GRID_SLOTS) {
            if (gs == slotIndex) {
                isGridSlot = true;
                break;
            }
        }
        if (!isGridSlot) return false;

        ItemStack stack = slots[slotIndex];
        int currentIdx = getColorIndex(stack.getItem());
        if (currentIdx == -1) return false;

        int newIdx;
        if (button == 1) {
            newIdx = (currentIdx - 1 + COLOR_CYCLE.length) % COLOR_CYCLE.length;
        } else {
            newIdx = (currentIdx + 1) % COLOR_CYCLE.length;
        }

        slots[slotIndex] = new ItemStack(COLOR_CYCLE[newIdx]);
        return true;
    }

    @Override
    protected boolean checkSolved() {
        Item firstColor = null;
        for (int slot : GRID_SLOTS) {
            Item color = slots[slot].getItem();
            if (firstColor == null) {
                firstColor = color;
            } else if (color != firstColor) {
                return false;
            }
        }
        return true;
    }

    private int getColorIndex(Item item) {
        for (int i = 0; i < COLOR_CYCLE.length; i++) {
            if (COLOR_CYCLE[i] == item) return i;
        }
        return -1;
    }
}
