package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F7 Terminal Solver - "What starts with: 'X'?"
 * Auto-clicks the correct item when player clicks anywhere on screen.
 */
public class StartsWithTerminal {
    private static final Pattern PATTERN = Pattern.compile("What starts with: '([A-Z])'\\?");

    private static Character targetLetter = null;
    private static java.util.List<Integer> correctSlots = new java.util.ArrayList<>();
    private static java.util.Set<Integer> clickedSlots = new java.util.HashSet<>();
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static boolean slotsFound = false;
    private static int lastDebugTick = 0;

    public static void tick() {
        // Note: We don't check DungeonManager.isInDungeon() because practice mode (/term) doesn't register as dungeon
        // If a terminal GUI is open, we're either in a dungeon or practice mode - both are valid

        MinecraftClient mc = MinecraftClient.getInstance();

        if (!TeslaMapsConfig.get().solveStartsWithTerminal) {
            if (targetLetter != null) {
                TeslaMaps.LOGGER.info("[StartsWithTerminal] DEBUG: Feature disabled, resetting");
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
            if (targetLetter != null) {
                TeslaMaps.LOGGER.info("[StartsWithTerminal] DEBUG: Screen closed or not container, resetting");
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
            TeslaMaps.LOGGER.info("[StartsWithTerminal] DEBUG: Container title: '{}'", titleStr);
            lastDebugTick = currentTick;
        }

        // Check if this is the "starts with" terminal
        Matcher matcher = PATTERN.matcher(titleStr);
        if (!matcher.find()) {
            // Not our terminal
            if (targetLetter != null) {
                TeslaMaps.LOGGER.info("[StartsWithTerminal] DEBUG: Title doesn't match pattern, resetting");
            }
            return;
        }

        // Extract the target letter
        char letter = matcher.group(1).charAt(0);

        // If this is a new terminal or different letter, reset and solve
        if (targetLetter == null || targetLetter != letter) {
            targetLetter = letter;
            solved = false;
            slotsFound = false;
            correctSlots.clear();
            clickedSlots.clear();
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            TeslaMaps.LOGGER.info("[StartsWithTerminal] ===== TERMINAL DETECTED =====");
            TeslaMaps.LOGGER.info("[StartsWithTerminal] Target letter: {}", targetLetter);
            TeslaMaps.LOGGER.info("[StartsWithTerminal] Delay before first click: {}ms", TeslaMapsConfig.get().terminalClickDelay);

            // Find ALL correct slots
            findAllCorrectSlots(screen);
            slotsFound = true;
        }

        // Auto-click all matching slots one by one
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        if (!solved && slotsFound && !correctSlots.isEmpty() && !usingClickAnywhere) {
            long currentTime = System.currentTimeMillis();
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            // Use configured delays with ±10ms randomization for human-like behavior
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(-10, 11);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(-10, 11);

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
                        TeslaMaps.LOGGER.info("[StartsWithTerminal] ===== ALL ITEMS CLICKED =====");
                    }
                    solved = true;

                    // If container is still open after 2 seconds, restart
                    if (timeSinceLastClick > 2000) {
                        TeslaMaps.LOGGER.info("[StartsWithTerminal] Container still open after 2s, restarting...");
                        correctSlots.clear();
                        clickedSlots.clear();
                        solved = false;
                        slotsFound = false;
                        targetLetter = null;
                    }
                }
            }
        }
    }

    /**
     * Find ALL slots that contain items starting with the target letter.
     */
    private static void findAllCorrectSlots(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[StartsWithTerminal] DEBUG: Scanning {} slots in container", handler.slots.size());

        // Scan all slots in the container
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

                char firstChar = strippedName.charAt(0);
                TeslaMaps.LOGGER.info("[StartsWithTerminal] DEBUG: Slot {} - '{}' starts with '{}'",
                    slot.id, strippedName, firstChar);

                // Check if name starts with target letter (case insensitive)
                if (Character.toUpperCase(firstChar) == targetLetter) {
                    correctSlots.add(slot.id);
                    TeslaMaps.LOGGER.info("[StartsWithTerminal] ===== FOUND MATCHING ITEM =====");
                    TeslaMaps.LOGGER.info("[StartsWithTerminal] Item: '{}'", strippedName);
                    TeslaMaps.LOGGER.info("[StartsWithTerminal] Slot: {}", slot.id);
                }
            }
        }

        if (correctSlots.isEmpty()) {
            TeslaMaps.LOGGER.warn("[StartsWithTerminal] ===== SCAN COMPLETE - NO MATCHES =====");
            TeslaMaps.LOGGER.warn("[StartsWithTerminal] Scanned {} items, none started with '{}'", scannedItems, targetLetter);
        } else {
            TeslaMaps.LOGGER.info("[StartsWithTerminal] ===== SCAN COMPLETE =====");
            TeslaMaps.LOGGER.info("[StartsWithTerminal] Found {} items starting with '{}'", correctSlots.size(), targetLetter);
            TeslaMaps.LOGGER.info("[StartsWithTerminal] Slots: {}", correctSlots);
        }
    }

    /**
     * Perform the next auto-click on an unclicked slot.
     */
    private static void performNextClick(MinecraftClient mc, GenericContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[StartsWithTerminal] DEBUG: Cannot click - player is null");
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
            TeslaMaps.LOGGER.warn("[StartsWithTerminal] DEBUG: No unclicked slots found!");
            return;
        }

        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[StartsWithTerminal] ===== PERFORMING AUTO-CLICK =====");
        TeslaMaps.LOGGER.info("[StartsWithTerminal] Clicking slot {} ({}/{})", slotToClick, clickedSlots.size() + 1, correctSlots.size());
        TeslaMaps.LOGGER.info("[StartsWithTerminal] Handler syncId: {}", handler.syncId);

        // Click the correct slot (left click = button 0)
        mc.interactionManager.clickSlot(
            handler.syncId,
            slotToClick,
            0,  // Left click
            SlotActionType.PICKUP,
            mc.player
        );

        clickedSlots.add(slotToClick);
        lastClickTime = System.currentTimeMillis();

        TeslaMaps.LOGGER.info("[StartsWithTerminal] ===== CLICK SENT =====");
        TeslaMaps.LOGGER.info("[StartsWithTerminal] Progress: {}/{} items clicked", clickedSlots.size(), correctSlots.size());
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
    public static java.util.List<Integer> getCorrectSlots() {
        return new java.util.ArrayList<>(correctSlots);
    }

    /**
     * For custom GUI: get the target letter.
     */
    public static Character getTargetLetter() {
        return targetLetter;
    }

    /**
     * For custom GUI: get clicked slots.
     */
    public static java.util.Set<Integer> getClickedSlots() {
        return new java.util.HashSet<>(clickedSlots);
    }

    public static void reset() {
        if (targetLetter != null) {
            TeslaMaps.LOGGER.info("[StartsWithTerminal] DEBUG: Resetting (was tracking letter '{}')", targetLetter);
        }
        targetLetter = null;
        correctSlots.clear();
        clickedSlots.clear();
        solved = false;
        slotsFound = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastDebugTick = 0;
    }
}
