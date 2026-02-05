package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * F7 Terminal Solver - "Correct all the panes!"
 * Auto-clicks red/incorrect panes to turn them green.
 */
public class CorrectPanesTerminal {
    private static List<Integer> incorrectSlots = new ArrayList<>();
    private static Set<Integer> clickedSlots = new HashSet<>();
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean initialScanDone = false;
    private static boolean isClicked = false;
    private static int lastDebugTick = 0;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!TeslaMapsConfig.get().solveCorrectPanesTerminal) {
            if (!incorrectSlots.isEmpty()) {
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] DEBUG: Feature disabled, resetting");
            }
            reset();
            return;
        }

        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        // Check if we're looking at a container screen
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (!incorrectSlots.isEmpty()) {
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] DEBUG: Screen closed, resetting");
            }
            reset();
            return;
        }

        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;

        // Get screen title
        Text title = screen.getTitle();
        String titleStr = title.getString();

        // Debug: Log container title once per second
        int currentTick = mc.player.age;
        if (currentTick - lastDebugTick > 20) {
            TeslaMaps.LOGGER.info("[CorrectPanesTerminal] DEBUG: Container title: '{}'", titleStr);
            lastDebugTick = currentTick;
        }

        // Check if this is the "correct panes" terminal
        if (!titleStr.equals("Correct all the panes!")) {
            // Not our terminal
            if (!incorrectSlots.isEmpty()) {
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] DEBUG: Title doesn't match, resetting");
            }
            return;
        }

        // If this is a new terminal, initialize
        if (!initialScanDone) {
            solved = false;
            clickedSlots.clear();
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== TERMINAL DETECTED =====");
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();

        // Re-scan every 100ms to find incorrect panes (they change as we click)
        if (currentTime - lastScanTime >= 100) {
            int prevSize = incorrectSlots.size();
            findIncorrectPanes(screen);
            lastScanTime = currentTime;
            // Reset isClicked if slot count changed (click was registered)
            if (incorrectSlots.size() != prevSize) {
                isClicked = false;
            }
        }

        // Auto-click incorrect panes one by one
        // Skip auto-clicking if using custom GUI with click anywhere
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        // Break threshold: reset isClicked if stuck for too long
        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!solved && !incorrectSlots.isEmpty() && !usingClickAnywhere && !isClicked) {
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            // Use configured delays with randomization for human-like behavior (0 to configured max)
            int randomization = TeslaMapsConfig.get().terminalClickRandomization;
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

            // Initial delay before first click
            if (clickedSlots.isEmpty() && timeSinceOpen >= randomInitialDelay) {
                performNextClick(mc, screen);
            }
            // Delay between subsequent clicks
            else if (!clickedSlots.isEmpty() && timeSinceLastClick >= randomInterval) {
                performNextClick(mc, screen);
            }
        } else if (incorrectSlots.isEmpty() && initialScanDone && clickedSlots.size() > 0) {
            // No more incorrect panes found after we've clicked some
            long timeSinceLastClick = currentTime - lastClickTime;

            if (!solved) {
                solved = true;
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== ALL PANES CORRECTED =====");
            }

            // If container still open after 1 second, restart
            if (timeSinceLastClick > 1000) {
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Container still open after 1s, restarting...");
                incorrectSlots.clear();
                clickedSlots.clear();
                solved = false;
                lastScanTime = 0; // Force immediate re-scan
            }
        }
    }

    /**
     * Find all incorrect (red/orange) panes that need to be clicked.
     */
    private static void findIncorrectPanes(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        List<Integer> newIncorrectSlots = new ArrayList<>();

        for (Slot slot : handler.slots) {
            // Skip player inventory slots (slots 0-53 are container, 54+ are player inv)
            if (slot.id >= 54) continue;

            // Skip slots at edges (columns 0, 1, 7, 8)
            int column = slot.id % 9;
            if (column == 0 || column == 1 || column == 7 || column == 8) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // Check if it's a red or orange pane (incorrect)
            if (stack.getItem() == Items.RED_STAINED_GLASS_PANE ||
                stack.getItem() == Items.ORANGE_STAINED_GLASS_PANE) {

                // Only add if we haven't already clicked it
                if (!clickedSlots.contains(slot.id)) {
                    newIncorrectSlots.add(slot.id);
                }
            }
        }

        // Only log if the list changed
        if (!newIncorrectSlots.equals(incorrectSlots)) {
            incorrectSlots = newIncorrectSlots;
            if (!incorrectSlots.isEmpty()) {
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Found {} incorrect panes: {}",
                    incorrectSlots.size(), incorrectSlots);
            }
        }
    }

    /**
     * Click the next incorrect pane.
     */
    private static void performNextClick(MinecraftClient mc, GenericContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[CorrectPanesTerminal] DEBUG: Cannot click - player is null");
            return;
        }

        if (incorrectSlots.isEmpty()) {
            TeslaMaps.LOGGER.warn("[CorrectPanesTerminal] DEBUG: No incorrect panes to click!");
            return;
        }

        // Click the first unclicked incorrect pane
        int slotToClick = incorrectSlots.get(0);
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== PERFORMING AUTO-CLICK =====");
        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Clicking slot {} (clicked {} so far)",
            slotToClick, clickedSlots.size());

        // Click the slot (left click = button 0)
        mc.interactionManager.clickSlot(
            handler.syncId,
            slotToClick,
            0,  // Left click
            SlotActionType.PICKUP,
            mc.player
        );

        clickedSlots.add(slotToClick);
        lastClickTime = System.currentTimeMillis();
        isClicked = true;

        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== CLICK SENT =====");
        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Total clicks: {}", clickedSlots.size());
    }

    /**
     * For easy mode: returns the next slot to click, or -1 if none.
     */
    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || incorrectSlots.isEmpty()) return -1;
        return incorrectSlots.get(0); // First incorrect pane
    }

    /**
     * For easy mode: mark a slot as clicked.
     */
    public static void markSlotClicked(int slot) {
        clickedSlots.add(slot);
        lastClickTime = System.currentTimeMillis();
    }

    /**
     * Event-driven slot update handler.
     * Called by TerminalManager when a slot update packet is received.
     */
    public static void onSlotUpdate(int slotIndex, net.minecraft.item.ItemStack stack) {
        if (!TeslaMapsConfig.get().solveCorrectPanesTerminal) return;
        if (slotIndex >= 54) return; // Ignore player inventory

        // Skip edge columns
        int column = slotIndex % 9;
        if (column == 0 || column == 1 || column == 7 || column == 8) return;

        // If a red/orange pane turned green, remove from incorrect list
        if (incorrectSlots.contains(slotIndex)) {
            if (stack.getItem() != Items.RED_STAINED_GLASS_PANE &&
                stack.getItem() != Items.ORANGE_STAINED_GLASS_PANE) {
                incorrectSlots.remove(Integer.valueOf(slotIndex));
                isClicked = false;
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Slot {} corrected!", slotIndex);
            }
        }
        // If a new incorrect pane appeared
        else if (stack.getItem() == Items.RED_STAINED_GLASS_PANE ||
                 stack.getItem() == Items.ORANGE_STAINED_GLASS_PANE) {
            if (!clickedSlots.contains(slotIndex)) {
                incorrectSlots.add(slotIndex);
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] New incorrect pane at slot {}", slotIndex);
            }
        }
    }

    /**
     * Validate if a click is allowed on this slot.
     * For Panes terminal: allow clicking any incorrect pane.
     */
    public static boolean canClick(int slotIndex, int button) {
        if (!initialScanDone || solved) return true;

        // Allow clicking any slot that's in the incorrect list
        if (incorrectSlots.contains(slotIndex)) {
            return true;
        }

        // Block clicks on already-correct panes
        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Blocked click on slot {} - not an incorrect pane", slotIndex);
        return false;
    }

    public static void reset() {
        if (!incorrectSlots.isEmpty()) {
            TeslaMaps.LOGGER.info("[CorrectPanesTerminal] DEBUG: Resetting");
        }
        incorrectSlots.clear();
        clickedSlots.clear();
        solved = false;
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
        lastDebugTick = 0;
    }
}
