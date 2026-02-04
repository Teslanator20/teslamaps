package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom GUI for the "Correct all the panes!" terminal.
 */
public class PanesTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Will be filled in renderTerminal
    }

    @Override
    public void renderTerminal(DrawContext context, int slotCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        renderBackground(context, slotCount, 7, 2);

        // Find all red panes (incorrect)
        List<Integer> redPanes = new ArrayList<>();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.id >= slotCount) continue;

            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                redPanes.add(slot.id);
            }
        }

        // Render all slots
        for (int index = 9; index < slotCount; index++) {
            if ((index % 9) == 0 || (index % 9) == 8) continue;

            // Highlight red panes
            int color = redPanes.contains(index) ?
                       TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiPanesColor) :
                       0x00000000; // Transparent

            renderSlot(context, index, color);
        }
    }
}
