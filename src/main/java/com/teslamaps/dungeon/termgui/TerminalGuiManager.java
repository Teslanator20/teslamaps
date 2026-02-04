package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Manager for custom terminal GUI rendering.
 * Determines which custom GUI to show based on the open terminal.
 */
public class TerminalGuiManager {
    private static CustomTermGui currentGui = null;
    private static String lastScreenTitle = "";

    /**
     * Check if we should render custom terminal GUI for the current screen.
     */
    public static boolean shouldRenderCustomGui() {
        if (!TeslaMapsConfig.get().customTerminalGui) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return false;

        return getTerminalType() != TerminalType.NONE;
    }

    /**
     * Render the custom terminal GUI overlay.
     */
    public static void render(DrawContext context) {
        if (!shouldRenderCustomGui()) return;

        TerminalType type = getTerminalType();
        if (type == TerminalType.NONE) return;

        // Create GUI instance if needed
        if (currentGui == null || !lastScreenTitle.equals(getScreenTitle())) {
            currentGui = createGuiForType(type);
            lastScreenTitle = getScreenTitle();
        }

        if (currentGui != null) {
            currentGui.render(context);
        }
    }

    /**
     * Handle mouse click on custom terminal GUI.
     * Returns true if the click was handled by the custom GUI.
     */
    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!shouldRenderCustomGui()) return false;
        if (currentGui == null) return false;

        // Click anywhere mode - redirect all clicks to the correct slot
        if (TeslaMapsConfig.get().terminalClickAnywhere) {
            TerminalType type = getTerminalType();
            int nextSlot = getNextCorrectSlot();

            com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] Click anywhere mode - Terminal: {}, Next slot: {}", type, nextSlot);

            if (nextSlot != -1) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.currentScreen instanceof GenericContainerScreen screen && mc.player != null) {
                    // Most terminals need left-click, only Rubix uses right-click for backwards direction
                    // For now, use the button from getNextCorrectSlot (Rubix will return slots that need right-click)
                    // But we need to detect if Rubix wants right-click based on the click queue
                    int buttonToUse = button;

                    // For Rubix, check if this slot needs right-click
                    if (type == TerminalType.RUBIX) {
                        java.util.Map<Integer, Integer> clickMap = RubixTerminal.getClickMap();
                        Integer clicksNeeded = clickMap.get(nextSlot);
                        if (clicksNeeded != null && clicksNeeded < 0) {
                            buttonToUse = 1; // Right-click for negative (backward) clicks
                        } else {
                            buttonToUse = 0; // Left-click for positive (forward) clicks
                        }
                    } else {
                        // All other terminals use left-click only
                        buttonToUse = 0;
                    }

                    com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] Clicking slot {} with button {}", nextSlot, buttonToUse);
                    // Click the correct slot regardless of where user clicked
                    mc.interactionManager.clickSlot(
                        screen.getScreenHandler().syncId,
                        nextSlot,
                        buttonToUse,
                        SlotActionType.PICKUP,
                        mc.player
                    );

                    // Mark the slot as clicked in the solver so it moves to the next one
                    markSlotAsClicked(type, nextSlot);
                    com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] Marked slot {} as clicked for type {}", nextSlot, type);
                }
            } else {
                com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] No next slot available - solver may not be initialized yet");
            }
            // IMPORTANT: Return true to block ALL clicks from going through
            // This prevents clicking the wrong slots when click anywhere is enabled
            return true;
        }

        // Normal mode - only handle clicks on highlighted slots
        return currentGui.handleClick(mouseX, mouseY, button);
    }

    /**
     * Get the next correct slot to click based on the current terminal type.
     * Returns -1 if no slot to click.
     */
    private static int getNextCorrectSlot() {
        TerminalType type = getTerminalType();
        switch (type) {
            case NUMBERS:
                return ClickInOrderTerminal.getNextCorrectSlot();
            case PANES:
                return CorrectPanesTerminal.getNextCorrectSlot();
            case STARTS_WITH:
                return StartsWithTerminal.getNextCorrectSlot();
            case SELECT_ALL:
                return SelectAllTerminal.getNextCorrectSlot();
            case RUBIX:
                return RubixTerminal.getNextCorrectSlot();
            case MELODY:
                return MelodyTerminal.getNextCorrectSlot();
            default:
                return -1;
        }
    }

    /**
     * Mark a slot as clicked in the solver so it moves to the next one.
     */
    private static void markSlotAsClicked(TerminalType type, int slot) {
        switch (type) {
            case PANES:
                CorrectPanesTerminal.markSlotClicked(slot);
                break;
            case STARTS_WITH:
                StartsWithTerminal.markSlotClicked(slot);
                break;
            case SELECT_ALL:
                SelectAllTerminal.markSlotClicked(slot);
                break;
            case RUBIX:
                RubixTerminal.markSlotClicked(slot);
                break;
            // NUMBERS (ClickInOrderTerminal) and MELODY don't need slot tracking
            // - Numbers rescans for red panes each tick
            // - Melody tracks lane progression automatically
        }
    }

    /**
     * Reset the GUI when terminal closes.
     */
    public static void reset() {
        currentGui = null;
        lastScreenTitle = "";
    }

    /**
     * Get the type of terminal currently open.
     */
    private static TerminalType getTerminalType() {
        String title = getScreenTitle();
        if (title == null) return TerminalType.NONE;

        String cleanTitle = Formatting.strip(title);
        if (cleanTitle == null) return TerminalType.NONE;

        if (cleanTitle.equals("Click in order!")) {
            return TerminalType.NUMBERS;
        } else if (cleanTitle.equals("Correct all the panes!")) {
            return TerminalType.PANES;
        } else if (cleanTitle.startsWith("What starts with:")) {
            return TerminalType.STARTS_WITH;
        } else if (cleanTitle.startsWith("Select all the")) {
            return TerminalType.SELECT_ALL;
        } else if (cleanTitle.equals("Change all to same color!")) {
            return TerminalType.RUBIX;
        } else if (cleanTitle.equals("Click the button on time!")) {
            return TerminalType.MELODY;
        }

        return TerminalType.NONE;
    }

    /**
     * Create a GUI instance for the given terminal type.
     */
    private static CustomTermGui createGuiForType(TerminalType type) {
        switch (type) {
            case NUMBERS:
                return new NumbersTermGui();
            case PANES:
                return new PanesTermGui();
            case STARTS_WITH:
                return new StartsWithTermGui();
            case SELECT_ALL:
                return new SelectAllTermGui();
            case RUBIX:
                return new RubixTermGui();
            case MELODY:
                return new MelodyTermGui();
            default:
                return null;
        }
    }

    /**
     * Get the title of the current screen.
     */
    private static String getScreenTitle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            Text title = screen.getTitle();
            return title != null ? title.getString() : null;
        }
        return null;
    }

    /**
     * Terminal types.
     */
    private enum TerminalType {
        NONE,
        NUMBERS,      // Click in order!
        PANES,        // Correct all the panes!
        STARTS_WITH,  // What starts with: '*'?
        SELECT_ALL,   // Select all the [color] items!
        RUBIX,        // Change all to same color!
        MELODY        // Click the button on time!
    }
}
