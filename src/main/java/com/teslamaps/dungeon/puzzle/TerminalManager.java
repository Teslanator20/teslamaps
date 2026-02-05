package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Central manager for terminal solving.
 * Coordinates event-driven updates and delegates to individual terminal solvers.
 */
public class TerminalManager {

    public enum TerminalType {
        NONE,
        CLICK_IN_ORDER,
        CORRECT_PANES,
        STARTS_WITH,
        SELECT_ALL,
        RUBIX,
        MELODY
    }

    private static TerminalType currentTerminal = TerminalType.NONE;
    private static int currentSyncId = -1;
    private static long lastSlotUpdateTime = 0;

    /**
     * Called by mixin when a slot update packet is received.
     * This is the event-driven entry point - no polling needed!
     */
    public static void onSlotUpdate(int syncId, int slotIndex, ItemStack stack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null || !(mc.currentScreen instanceof GenericContainerScreen)) {
            currentTerminal = TerminalType.NONE;
            return;
        }

        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        int screenSyncId = screen.getScreenHandler().syncId;

        // Only process updates for the current screen
        if (syncId != screenSyncId) {
            return;
        }

        // Detect terminal type if not yet detected
        if (currentTerminal == TerminalType.NONE || currentSyncId != syncId) {
            currentTerminal = detectTerminalType(screen);
            currentSyncId = syncId;
            if (currentTerminal != TerminalType.NONE) {
                TeslaMaps.LOGGER.info("[TerminalManager] Detected terminal: {}", currentTerminal);
            }
        }

        lastSlotUpdateTime = System.currentTimeMillis();

        // Notify the appropriate terminal solver
        switch (currentTerminal) {
            case CLICK_IN_ORDER:
                ClickInOrderTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case CORRECT_PANES:
                CorrectPanesTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case STARTS_WITH:
                StartsWithTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case SELECT_ALL:
                SelectAllTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case RUBIX:
                RubixTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case MELODY:
                MelodyTerminal.onSlotUpdate(slotIndex, stack);
                break;
            default:
                break;
        }
    }

    /**
     * Detect which terminal type is currently open based on screen title.
     */
    private static TerminalType detectTerminalType(GenericContainerScreen screen) {
        Text title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = Formatting.strip(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (cleanTitle.equals("Click in order!")) {
            return TerminalType.CLICK_IN_ORDER;
        } else if (cleanTitle.equals("Correct all the panes!")) {
            return TerminalType.CORRECT_PANES;
        } else if (cleanTitle.matches("What starts with: '[A-Z]'\\?")) {
            return TerminalType.STARTS_WITH;
        } else if (cleanTitle.matches("Select all the [A-Z]+ items!")) {
            return TerminalType.SELECT_ALL;
        } else if (cleanTitle.equals("Change all to same color!")) {
            return TerminalType.RUBIX;
        } else if (cleanTitle.equals("Click the button on time!")) {
            return TerminalType.MELODY;
        }

        return TerminalType.NONE;
    }

    /**
     * Get the current terminal type.
     */
    public static TerminalType getCurrentTerminal() {
        return currentTerminal;
    }

    /**
     * Check if we're currently in a terminal.
     */
    public static boolean isInTerminal() {
        return currentTerminal != TerminalType.NONE;
    }

    /**
     * Get time since last slot update (for isClicked timeout).
     */
    public static long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastSlotUpdateTime;
    }

    /**
     * Reset state when screen closes.
     */
    public static void onScreenClose() {
        currentTerminal = TerminalType.NONE;
        currentSyncId = -1;
    }

    /**
     * Validate if a click is allowed on the given slot.
     * Delegates to the appropriate terminal solver.
     */
    public static boolean canClick(int slotIndex, int button) {
        switch (currentTerminal) {
            case CLICK_IN_ORDER:
                return ClickInOrderTerminal.canClick(slotIndex, button);
            case CORRECT_PANES:
                return CorrectPanesTerminal.canClick(slotIndex, button);
            case STARTS_WITH:
                return StartsWithTerminal.canClick(slotIndex, button);
            case SELECT_ALL:
                return SelectAllTerminal.canClick(slotIndex, button);
            case RUBIX:
                return RubixTerminal.canClick(slotIndex, button);
            case MELODY:
                return MelodyTerminal.canClick(slotIndex, button);
            default:
                return true; // Allow all clicks if not in a terminal
        }
    }
}
