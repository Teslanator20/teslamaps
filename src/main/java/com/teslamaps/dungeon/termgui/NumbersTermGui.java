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

/**
 * Custom GUI for the "Click in order!" terminal (Numbers).
 */
public class NumbersTermGui extends CustomTermGui {
    private static final int TRANSPARENT = 0x00000000;

    @Override
    protected int[] getCurrentSolution() {
        // Get the solution from the existing terminal solver
        // For now, we'll scan the screen handler directly
        return new int[0]; // Will be filled in renderTerminal
    }

    @Override
    public void renderTerminal(GuiGraphicsExtractor context, int slotCount) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return;

        renderBackground(context, slotCount, 7, 2);

        // Build map of slot -> number
        Map<Integer, Integer> slotToNumber = new HashMap<>();
        List<Integer> orderedSlots = new ArrayList<>();

        for (Slot slot : screen.getMenu().slots) {
            if (slot.index >= slotCount) continue; // Skip player inventory

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            // Red panes have the order number as stack size
            if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                int number = stack.getCount();
                if (number >= 1 && number <= 14) {
                    slotToNumber.put(slot.index, number);
                }
            }
        }

        // Build ordered list
        for (int i = 1; i <= 14; i++) {
            for (Map.Entry<Integer, Integer> entry : slotToNumber.entrySet()) {
                if (entry.getValue() == i) {
                    orderedSlots.add(entry.getKey());
                    break;
                }
            }
        }

        // Render slots
        for (int index = 9; index < slotCount; index++) {
            // Skip edges (columns 0 and 8)
            if ((index % 9) == 0 || (index % 9) == 8) continue;

            int solutionIndex = orderedSlots.indexOf(index);
            int amount = orderedSlots.size() - solutionIndex;

            // Color based on position in solution
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

            // Draw number if configured and in solution
            if (TeslaMapsConfig.get().terminalGuiShowNumbers && solutionIndex != -1) {
                float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
                drawTextCentered(context, String.valueOf(amount), pos[0], pos[1], slotSize, 0xFFFFFFFF);
            }
        }
    }
}
