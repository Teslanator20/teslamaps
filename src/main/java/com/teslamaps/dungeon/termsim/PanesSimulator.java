package com.teslamaps.dungeon.termsim;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Simulator for "Correct all the panes!" terminal.
 * Click red panes to turn them green.
 */
public class PanesSimulator extends TerminalSimulator {

    public PanesSimulator() {
        super("Correct all the panes!", 5, 9);
    }

    @Override
    protected void initializeTerminal() {
        // Fill with random red/green panes (edges are black)
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = getSlotIndex(row, col);

                // Edge columns are black
                if (col == 0 || col == 1 || col == 7 || col == 8) {
                    slots[idx] = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
                } else {
                    // Random red or green
                    boolean isRed = random.nextBoolean();
                    slots[idx] = new ItemStack(isRed ? Items.RED_STAINED_GLASS_PANE : Items.LIME_STAINED_GLASS_PANE);
                }
            }
        }
    }

    @Override
    protected boolean onSlotClick(int slotIndex, int button) {
        int col = getCol(slotIndex);

        // Can't click edges
        if (col == 0 || col == 1 || col == 7 || col == 8) {
            return false;
        }

        ItemStack stack = slots[slotIndex];

        // Toggle red <-> green
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
        // All non-edge panes must be green
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
