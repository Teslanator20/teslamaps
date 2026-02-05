package com.teslamaps.dungeon.termsim;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simulator for "Click in order!" terminal.
 * Click numbered red panes 1-14 in order.
 */
public class NumbersSimulator extends TerminalSimulator {
    private int nextNumber = 1;

    public NumbersSimulator() {
        super("Click in order!", 4, 9);
    }

    @Override
    protected void initializeTerminal() {
        nextNumber = 1;

        // Fill with black panes
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        }

        // Place numbers 1-14 in random positions (avoiding edges)
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

        // Must be a red pane
        if (stack.getItem() != Items.RED_STAINED_GLASS_PANE) {
            return false;
        }

        int number = stack.getCount();

        // Must click in order
        if (number != nextNumber) {
            return false;
        }

        // Turn green
        slots[slotIndex] = new ItemStack(Items.LIME_STAINED_GLASS_PANE, number);
        nextNumber++;

        return true;
    }

    @Override
    protected boolean checkSolved() {
        return nextNumber > 14;
    }
}
