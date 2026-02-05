package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F7 Terminal Solver - "Select all the [COLOR] items!"
 * Auto-clicks all items of the specified color.
 */
public class SelectAllTerminal {
    private static final Pattern PATTERN = Pattern.compile("Select all the ([A-Z]+) items!");

    private static String targetColor = null;
    private static List<Integer> correctSlots = new ArrayList<>();
    private static Set<Integer> clickedSlots = new HashSet<>();
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static boolean slotsFound = false;
    private static boolean isClicked = false;
    private static int lastDebugTick = 0;

    public static void tick() {
        // DISABLED - Auto terminal feature commented out
        if (true) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        /* DISABLED
        if (!TeslaMapsConfig.get().solveSelectAllTerminal) {
            if (targetColor != null) {
            }
            reset();
            return;
        }
        */

        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        // Check if we're looking at a container screen
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (targetColor != null) {
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
            lastDebugTick = currentTick;
        }

        // Check if this is the "select all" terminal
        Matcher matcher = PATTERN.matcher(titleStr);
        if (!matcher.find()) {
            // Not our terminal
            if (targetColor != null) {
            }
            return;
        }

        // Extract the target color
        String color = matcher.group(1);

        // If this is a new terminal or different color, reset and solve
        if (targetColor == null || !targetColor.equals(color)) {
            targetColor = color;
            solved = false;
            slotsFound = false;
            correctSlots.clear();
            clickedSlots.clear();
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            TeslaMaps.LOGGER.info("[SelectAllTerminal] ===== TERMINAL DETECTED =====");
            TeslaMaps.LOGGER.info("[SelectAllTerminal] Target color: {}", targetColor);

            // Find ALL correct slots
            findAllCorrectSlots(screen);
            slotsFound = true;
        }

        // Auto-click all matching slots one by one
        // Skip auto-clicking if using custom GUI with click anywhere
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        long currentTime = System.currentTimeMillis();

        // Break threshold: reset isClicked if stuck for too long
        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[SelectAllTerminal] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!solved && slotsFound && !correctSlots.isEmpty() && !usingClickAnywhere && !isClicked) {
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
                if (clickedSlots.size() < correctSlots.size()) {
                    performNextClick(mc, screen);
                } else {
                    // All slots clicked
                    if (!solved) {
                        TeslaMaps.LOGGER.info("[SelectAllTerminal] ===== ALL ITEMS CLICKED =====");
                    }
                    solved = true;

                    // If container is still open after 2 seconds, restart
                    if (timeSinceLastClick > 2000) {
                        TeslaMaps.LOGGER.info("[SelectAllTerminal] Container still open after 2s, restarting...");
                        correctSlots.clear();
                        clickedSlots.clear();
                        solved = false;
                        slotsFound = false;
                        isClicked = false;
                        targetColor = null;
                    }
                }
            }
        }
    }

    /**
     * Find ALL slots that contain items of the target color.
     */
    private static void findAllCorrectSlots(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();


        int scannedItems = 0;

        // Scan top-to-bottom (column by column) instead of left-to-right
        // Chest is 9 columns wide, 6 rows tall
        for (int col = 0; col < 9; col++) {
            for (int row = 0; row < 6; row++) {
                int slotId = row * 9 + col;
                if (slotId >= handler.slots.size()) continue;

                Slot slot = handler.slots.get(slotId);

                // Skip player inventory slots (slots 0-53 are container, 54+ are player inv)
                if (slot.id >= 54) continue;

                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) continue;

                scannedItems++;

                // Get item display name
                String itemName = stack.getName().getString();

                // Strip formatting codes
                String strippedName = Formatting.strip(itemName);
                if (strippedName == null || strippedName.isEmpty()) {
                    continue;
                }


                // Check if this item matches the target color
                if (matchesColor(strippedName, targetColor)) {
                    correctSlots.add(slot.id);
                    TeslaMaps.LOGGER.info("[SelectAllTerminal] ===== FOUND MATCHING ITEM =====");
                    TeslaMaps.LOGGER.info("[SelectAllTerminal] Item: '{}'", strippedName);
                    TeslaMaps.LOGGER.info("[SelectAllTerminal] Slot: {}", slot.id);
                }
            }
        }

        if (correctSlots.isEmpty()) {
            TeslaMaps.LOGGER.warn("[SelectAllTerminal] ===== SCAN COMPLETE - NO MATCHES =====");
            TeslaMaps.LOGGER.warn("[SelectAllTerminal] Scanned {} items, none matched '{}'", scannedItems, targetColor);
        } else {
            TeslaMaps.LOGGER.info("[SelectAllTerminal] ===== SCAN COMPLETE =====");
            TeslaMaps.LOGGER.info("[SelectAllTerminal] Found {} items with color '{}'", correctSlots.size(), targetColor);
            TeslaMaps.LOGGER.info("[SelectAllTerminal] Slots: {}", correctSlots);
        }
    }

    /**
     * Check if an item name matches the target color.
     * Handles special cases like Lapis = Blue, Ink Sack = Black, Light Gray = Silver, etc.
     */
    private static boolean matchesColor(String itemName, String color) {
        String upperName = itemName.toUpperCase();
        String upperColor = color.toUpperCase();

        // Special cases for dyes and materials
        if (upperColor.equals("BLUE")) {
            // Lapis counts as blue, but NOT light blue
            if (upperName.contains("LAPIS")) return true;
            if (upperName.startsWith("LIGHT BLUE")) return false;
            if (upperName.startsWith("BLUE")) return true;
        } else if (upperColor.equals("BLACK")) {
            // Ink sack is black
            if (upperName.contains("INK")) return true;
            if (upperName.startsWith("BLACK")) return true;
        } else if (upperColor.equals("WHITE")) {
            // Bone meal is white
            if (upperName.contains("BONE")) return true;
            if (upperName.startsWith("WHITE")) return true;
        } else if (upperColor.equals("SILVER")) {
            // Light gray is silver
            if (upperName.startsWith("LIGHT GRAY")) return true;
            if (upperName.startsWith("SILVER")) return true;
        } else if (upperColor.equals("YELLOW")) {
            // Dandelion yellow
            if (upperName.contains("DANDELION")) return true;
            if (upperName.startsWith("YELLOW")) return true;
        } else if (upperColor.equals("RED")) {
            // Rose red
            if (upperName.contains("ROSE") && !upperName.contains("QUARTZ")) return true;
            if (upperName.startsWith("RED")) return true;
        } else if (upperColor.equals("GREEN")) {
            // Cactus green
            if (upperName.contains("CACTUS")) return true;
            if (upperName.startsWith("LIME")) return false; // Lime is NOT green
            if (upperName.startsWith("GREEN")) return true;
        } else if (upperColor.equals("BROWN")) {
            // Cocoa beans is brown
            if (upperName.contains("COCOA")) return true;
            if (upperName.startsWith("BROWN")) return true;
        } else if (upperColor.equals("LIGHT BLUE")) {
            // Light blue items
            if (upperName.startsWith("LIGHT BLUE")) return true;
        } else {
            // For other colors, just check if the name starts with the color
            // Check with trailing space OR exact match OR followed by non-letter character
            if (upperName.startsWith(upperColor + " ")) return true;
            if (upperName.equals(upperColor)) return true;
        }

        return false;
    }

    /**
     * Perform the next auto-click on an unclicked slot.
     */
    private static void performNextClick(MinecraftClient mc, GenericContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[SelectAllTerminal] DEBUG: Cannot click - player is null");
            return;
        }

        // Find next unclicked slot
        int slotToClick = -1;
        for (int slot : correctSlots) {
            if (!clickedSlots.contains(slot)) {
                slotToClick = slot;
                break;
            }
        }

        if (slotToClick == -1) {
            TeslaMaps.LOGGER.warn("[SelectAllTerminal] DEBUG: No unclicked slots found!");
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[SelectAllTerminal] ===== PERFORMING AUTO-CLICK =====");
        TeslaMaps.LOGGER.info("[SelectAllTerminal] Clicking slot {} ({}/{})", slotToClick, clickedSlots.size() + 1, correctSlots.size());

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

        TeslaMaps.LOGGER.info("[SelectAllTerminal] ===== CLICK SENT =====");
        TeslaMaps.LOGGER.info("[SelectAllTerminal] Progress: {}/{} items clicked", clickedSlots.size(), correctSlots.size());
    }

    /**
     * For easy mode: returns the next slot to click, or -1 if none.
     */
    public static int getNextCorrectSlot() {
        if (!slotsFound || solved || correctSlots.isEmpty()) return -1;

        // Find next unclicked slot
        for (int slot : correctSlots) {
            if (!clickedSlots.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * For easy mode: mark a slot as clicked.
     */
    public static void markSlotClicked(int slot) {
        clickedSlots.add(slot);
        lastClickTime = System.currentTimeMillis();
    }

    /**
     * For custom GUI: get all correct slots.
     */
    public static List<Integer> getCorrectSlots() {
        return new ArrayList<>(correctSlots);
    }

    /**
     * For custom GUI: get the target color.
     */
    public static String getTargetColor() {
        return targetColor;
    }

    /**
     * For custom GUI: get clicked slots.
     */
    public static Set<Integer> getClickedSlots() {
        return new HashSet<>(clickedSlots);
    }

    /**
     * Event-driven slot update handler.
     * Called by TerminalManager when a slot update packet is received.
     */
    public static void onSlotUpdate(int slotIndex, net.minecraft.item.ItemStack stack) {
        if (!TeslaMapsConfig.get().solveSelectAllTerminal) return;
        if (slotIndex >= 54) return; // Ignore player inventory

        // If an item we clicked got enchant glint (was clicked), mark as done
        if (clickedSlots.contains(slotIndex)) {
            if (stack.hasGlint()) {
                isClicked = false; // Click was registered
                TeslaMaps.LOGGER.info("[SelectAllTerminal] Slot {} click confirmed (has glint)", slotIndex);
            }
        }
    }

    /**
     * Validate if a click is allowed on this slot.
     * For SelectAllTerminal: allow clicking any correct slot that hasn't been clicked.
     */
    public static boolean canClick(int slotIndex, int button) {
        if (!slotsFound || solved) return true;

        // Allow clicking any correct slot that hasn't been clicked yet
        if (correctSlots.contains(slotIndex) && !clickedSlots.contains(slotIndex)) {
            return true;
        }

        // Block clicks on wrong items or already-clicked items
        if (correctSlots.contains(slotIndex)) {
            TeslaMaps.LOGGER.info("[SelectAllTerminal] Blocked click on slot {} - already clicked", slotIndex);
        } else {
            TeslaMaps.LOGGER.info("[SelectAllTerminal] Blocked click on slot {} - not a matching color", slotIndex);
        }
        return false;
    }

    public static void reset() {
        if (targetColor != null) {
        }
        targetColor = null;
        correctSlots.clear();
        clickedSlots.clear();
        solved = false;
        slotsFound = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastDebugTick = 0;
    }
}
