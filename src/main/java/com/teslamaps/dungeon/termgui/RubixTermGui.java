package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.RubixTerminal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

import java.util.Map;

/**
 * Custom GUI for the "Change all to same color!" terminal (Rubix).
 */
public class RubixTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Not used for this terminal
    }

    @Override
    public void renderTerminal(DrawContext context, int slotCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        renderBackground(context, slotCount, 3, 2);

        // Get the click map from the solver
        Map<Integer, Integer> clickMap = RubixTerminal.getClickMap();

        for (Map.Entry<Integer, Integer> entry : clickMap.entrySet()) {
            int slot = entry.getKey();
            int clicksRequired = entry.getValue();

            if (clicksRequired == 0) continue;

            // Determine color based on clicks needed
            int color = getColor(clicksRequired);

            float[] pos = renderSlot(context, slot, color);

            // Draw the number of clicks needed
            float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
            String text = String.valueOf(Math.abs(clicksRequired));
            drawTextCentered(context, text, pos[0], pos[1], slotSize, 0xFFFFFFFF);
        }
    }

    /**
     * Get color based on number of clicks required.
     * Positive = left click (forward), negative = right click (backward).
     */
    private int getColor(int clicksRequired) {
        if (clicksRequired == 1) {
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor1);
        } else if (clicksRequired == 2) {
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor2);
        } else if (clicksRequired == -1) {
            // Negative = right click (backward)
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor1);
        } else {
            // -2 or other
            return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiRubixColor2);
        }
    }
}
