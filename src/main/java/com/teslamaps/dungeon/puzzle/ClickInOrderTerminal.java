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
 * F7 Terminal Solver - "Click in order!"
 * Auto-clicks glass panes numbered 1-14 in order based on stack size.
 */
public class ClickInOrderTerminal {
    // No pattern needed - we just check exact title match

    private static Map<Integer, Integer> slotToNumber = new HashMap<>(); // slot -> number
    private static List<Integer> orderedSlots = new ArrayList<>(); // slots in order 1-14
    private static int nextNumberToClick = 1;
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean initialScanDone = false;
    private static boolean isClicked = false; // Track if we're waiting for a click to register
    private static int lastDebugTick = 0;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!TeslaMapsConfig.get().solveClickInOrderTerminal) {
            if (!slotToNumber.isEmpty()) {
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] DEBUG: Feature disabled, resetting");
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
            if (!slotToNumber.isEmpty()) {
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] DEBUG: Screen closed, resetting");
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
            TeslaMaps.LOGGER.info("[ClickInOrderTerminal] DEBUG: Container title: '{}'", titleStr);
            lastDebugTick = currentTick;
        }

        // Check if this is the "click in order" terminal
        // Strip formatting first
        String cleanTitle = net.minecraft.util.Formatting.strip(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (!cleanTitle.equals("Click in order!")) {
            // Not our terminal
            if (!slotToNumber.isEmpty()) {
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] DEBUG: Title doesn't match, resetting");
            }
            return;
        }

        // Initialize on first detection
        if (!initialScanDone) {
            solved = false;
            nextNumberToClick = 1;
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== TERMINAL DETECTED =====");
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();

        // Re-scan for red panes every 100ms (in case some clicks didn't register)
        if (currentTime - lastScanTime >= 100) {
            int prevSize = orderedSlots.size();
            findAllNumberedSlots(screen);
            lastScanTime = currentTime;
            // Reset isClicked if slot count changed (click was registered)
            if (orderedSlots.size() != prevSize) {
                isClicked = false;
            }
        }

        // Auto-click red panes in order by stack size
        // Skip auto-clicking if using custom GUI with click anywhere
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        // Break threshold: reset isClicked if stuck for too long
        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!solved && !orderedSlots.isEmpty() && !usingClickAnywhere && !isClicked) {
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            // Use configured delays with randomization for human-like behavior (0 to configured max)
            int randomization = TeslaMapsConfig.get().terminalClickRandomization;
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

            // Initial delay before first click
            if (nextNumberToClick == 1 && timeSinceOpen >= randomInitialDelay) {
                performNextClick(mc, screen);
            }
            // Delay between subsequent clicks
            else if (nextNumberToClick > 1 && timeSinceLastClick >= randomInterval) {
                performNextClick(mc, screen);
            }
        } else if (orderedSlots.isEmpty() && initialScanDone && nextNumberToClick > 1) {
            // No more red panes found and we've clicked at least one
            long timeSinceLastClick = currentTime - lastClickTime;

            if (!solved) {
                solved = true;
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== ALL PANES TURNED GREEN =====");
            }

            // If container still open after 1 second, restart
            if (timeSinceLastClick > 1000) {
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Container still open after 1s, restarting...");
                slotToNumber.clear();
                orderedSlots.clear();
                nextNumberToClick = 1;
                solved = false;
                lastScanTime = 0; // Force immediate re-scan
            }
        }
    }

    /**
     * Find all RED glass panes and map them to their numbers (stack size).
     * Red panes turn green when clicked correctly.
     */
    private static void findAllNumberedSlots(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] DEBUG: Scanning {} slots for RED panes", handler.slots.size());

        slotToNumber.clear();
        orderedSlots.clear();

        int redFound = 0;

        for (Slot slot : handler.slots) {
            // Skip player inventory slots (slots 0-53 are container, 54+ are player inv)
            if (slot.id >= 54) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // Only look for RED glass panes (these are the ones to click)
            if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                int number = stack.getCount(); // Stack size = order number
                if (number >= 1 && number <= 14) {
                    slotToNumber.put(slot.id, number);
                    redFound++;
                    TeslaMaps.LOGGER.info("[ClickInOrderTerminal] DEBUG: Slot {} - RED PANE Number {}", slot.id, number);
                }
            }
        }

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Found {} RED panes to click", redFound);

        // Build ordered list (slots 1-14)
        for (int i = 1; i <= 14; i++) {
            for (Map.Entry<Integer, Integer> entry : slotToNumber.entrySet()) {
                if (entry.getValue() == i) {
                    orderedSlots.add(entry.getKey());
                    break;
                }
            }
        }

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== SCAN COMPLETE =====");
        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Found {} numbered panes", slotToNumber.size());
        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Click order: {}", orderedSlots);
    }

    /**
     * Click the next red pane in order.
     */
    private static void performNextClick(MinecraftClient mc, GenericContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[ClickInOrderTerminal] DEBUG: Cannot click - player is null");
            return;
        }

        if (orderedSlots.isEmpty()) {
            TeslaMaps.LOGGER.warn("[ClickInOrderTerminal] DEBUG: No red panes to click!");
            return;
        }

        // Click the first red pane (lowest number)
        int slotToClick = orderedSlots.get(0);
        int number = slotToNumber.get(slotToClick);
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== PERFORMING AUTO-CLICK =====");
        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Clicking pane #{} at slot {}", number, slotToClick);

        // Click the slot (left click = button 0)
        mc.interactionManager.clickSlot(
            handler.syncId,
            slotToClick,
            0,  // Left click
            SlotActionType.PICKUP,
            mc.player
        );

        nextNumberToClick = number + 1;
        lastClickTime = System.currentTimeMillis();
        isClicked = true; // Mark that we're waiting for click to register

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== CLICK SENT =====");
    }

    /**
     * For easy mode: returns the next slot to click, or -1 if none.
     */
    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || orderedSlots.isEmpty()) return -1;
        return orderedSlots.get(0); // First red pane in order
    }

    public static void reset() {
        if (!slotToNumber.isEmpty()) {
            TeslaMaps.LOGGER.info("[ClickInOrderTerminal] DEBUG: Resetting");
        }
        slotToNumber.clear();
        orderedSlots.clear();
        nextNumberToClick = 1;
        solved = false;
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
        lastDebugTick = 0;
    }
}
