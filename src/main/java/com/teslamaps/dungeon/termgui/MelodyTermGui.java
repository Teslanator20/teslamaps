package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.MelodyTerminal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

/**
 * Custom GUI for the "Click the button on time!" terminal (Melody).
 */
public class MelodyTermGui extends CustomTermGui {
    @Override
    protected int[] getCurrentSolution() {
        return new int[0]; // Not used for this terminal
    }

    @Override
    public void renderTerminal(DrawContext context, int slotCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;

        renderBackground(context, 44, 7, 3);

        int currentLane = MelodyTerminal.getCurrentLane();
        int lastGreenPos = MelodyTerminal.getLastGreenPosition();
        int purplePos = MelodyTerminal.getPurplePosition();

        // Render 44 slots (top row of buttons + 4 lanes)
        for (int index = 0; index < 44; index++) {
            int column = index % 9;
            int row = index / 9;

            int color;

            if (row == 0) {
                // Top row - show PURPLE target indicator (where you need to click when green aligns)
                if (purplePos != -1 && index == purplePos) {
                    // Purple/magenta color for the target column
                    color = 0xFFFF00FF; // Bright magenta
                } else {
                    continue; // Skip other top row slots
                }
            } else if (column == 7 && row >= 1 && row <= 4) {
                // Terracotta column (column 7, rows 1-4) - highlight the current lane
                if (row - 1 == currentLane && purplePos != -1) {
                    color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiMelodyColor);
                } else {
                    color = 0x44888888; // Gray background for inactive lanes
                }
            } else if (column >= 1 && column <= 6 && row >= 1 && row <= 4) {
                // Button columns (1-6) - highlight where GREEN moving indicator is
                // Calculate slot number of green indicator in current lane
                int currentLaneRow = currentLane + 1; // Lane 0 = row 1, lane 1 = row 2, etc.
                if (row == currentLaneRow && lastGreenPos != -1 && column == lastGreenPos) {
                    // Green color for the moving indicator
                    color = 0xFF00FF00; // Bright green
                } else if (row == currentLaneRow) {
                    // Light gray background for active lane
                    color = 0x44888888;
                } else {
                    continue; // Skip other lanes
                }
            } else {
                continue; // Skip other slots
            }

            renderSlot(context, index, color);
        }
    }
}
