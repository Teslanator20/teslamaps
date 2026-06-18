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

/**
 * Custom GUI for the "Correct all the panes!" terminal.
 */
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

        // Find all red panes (incorrect)
        List<Integer> redPanes = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.index >= slotCount) continue;

            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                redPanes.add(slot.index);
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
