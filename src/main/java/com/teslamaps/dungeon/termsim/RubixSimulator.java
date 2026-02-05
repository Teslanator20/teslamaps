package com.teslamaps.dungeon.termsim;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Simulator for "Change all to same color!" terminal.
 * Click panes to cycle colors. Left click = forward, Right click = backward.
 * Color cycle: RED -> ORANGE -> YELLOW -> GREEN -> BLUE -> RED
 */
public class RubixSimulator extends TerminalSimulator {
    private static final Item[] COLOR_CYCLE = {
            Items.RED_STAINED_GLASS_PANE,
            Items.ORANGE_STAINED_GLASS_PANE,
            Items.YELLOW_STAINED_GLASS_PANE,
            Items.GREEN_STAINED_GLASS_PANE,
            Items.BLUE_STAINED_GLASS_PANE
    };

    // Grid positions (3x3 in center of 5x9)
    private static final int[] GRID_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};

    public RubixSimulator() {
        super("Change all to same color!", 5, 9);
    }

    @Override
    protected void initializeTerminal() {
        // Fill with black panes
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        }

        // Place random colors in 3x3 grid
        for (int slot : GRID_SLOTS) {
            int colorIdx = random.nextInt(COLOR_CYCLE.length);
            slots[slot] = new ItemStack(COLOR_CYCLE[colorIdx]);
        }
    }

    @Override
    protected boolean onSlotClick(int slotIndex, int button) {
        // Must be a grid slot
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

        // Left click (button 0) = forward, Right click (button 1) = backward
        int newIdx;
        if (button == 1) {
            // Backward
            newIdx = (currentIdx - 1 + COLOR_CYCLE.length) % COLOR_CYCLE.length;
        } else {
            // Forward
            newIdx = (currentIdx + 1) % COLOR_CYCLE.length;
        }

        slots[slotIndex] = new ItemStack(COLOR_CYCLE[newIdx]);
        return true;
    }

    @Override
    protected boolean checkSolved() {
        // All grid slots must be same color
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
