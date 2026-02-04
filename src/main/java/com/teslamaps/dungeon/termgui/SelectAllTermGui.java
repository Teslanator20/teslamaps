package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.SelectAllTerminal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

import java.util.List;
import java.util.Set;

/**
 * Custom GUI for the "Select all the [color] items!" terminal.
 * Shows correct items in green, clicked items dimmed, with color indicator.
 */
public class SelectAllTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Not used for this terminal
    }

    @Override
    public void renderTerminal(DrawContext context, int slotCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        renderBackground(context, slotCount, 9, 2);

        // Get solution from solver
        List<Integer> correctSlots = SelectAllTerminal.getCorrectSlots();
        Set<Integer> clickedSlots = SelectAllTerminal.getClickedSlots();
        String targetColor = SelectAllTerminal.getTargetColor();

        // Highlight correct items
        for (int slot : correctSlots) {
            boolean isClicked = clickedSlots.contains(slot);

            // Green for unclicked correct items, dimmed green for clicked
            int color;
            if (isClicked) {
                // Dimmed gray for already clicked items
                color = 0x44555555;
            } else {
                // Bright green for items to click
                color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiSelectColor);
            }

            float[] pos = renderSlot(context, slot, color);

            // Draw the first letter of the color on each correct item
            if (targetColor != null && TeslaMapsConfig.get().terminalGuiShowNumbers) {
                float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
                // Show first letter of color (e.g., "R" for RED, "B" for BLUE)
                String text = targetColor.substring(0, 1);
                drawTextCentered(context, text, pos[0], pos[1], slotSize,
                    isClicked ? 0x77FFFFFF : 0xFFFFFFFF);
            }
        }
    }
}
