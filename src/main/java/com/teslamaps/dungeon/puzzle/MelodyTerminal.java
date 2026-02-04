package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * F7 Terminal Solver - "Click the button on time!" (Melody)
 * 4 lanes, one active at a time (top to bottom).
 * Green pane moves across lane, click terracotta when green aligns with purple.
 */
public class MelodyTerminal {
    // Terracotta click slots (one per lane) - the lime terracotta at position 7 in each lane
    private static final int[] TERRACOTTA_SLOTS = {16, 25, 34, 43};

    // Lanes: row 1 = slots 9-17, row 2 = slots 18-26, row 3 = slots 27-35, row 4 = slots 36-44
    private static final int[][] LANE_SLOTS = {
        {9, 10, 11, 12, 13, 14, 15, 16, 17},   // Lane 1
        {18, 19, 20, 21, 22, 23, 24, 25, 26},  // Lane 2
        {27, 28, 29, 30, 31, 32, 33, 34, 35},  // Lane 3
        {36, 37, 38, 39, 40, 41, 42, 43, 44}   // Lane 4
    };

    private static int currentLane = 0; // 0-3 for lanes 1-4
    private static int lastGreenPosition = -1;
    private static int purplePosition = -1;
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean initialScanDone = false;
    private static int lastDebugTick = 0;
    private static long laneProgressTime = 0; // Time when lane progressed

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!TeslaMapsConfig.get().solveMelodyTerminal) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[MelodyTerminal] Feature disabled, resetting");
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
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[MelodyTerminal] Screen closed, resetting");
            }
            reset();
            return;
        }

        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;

        // Get screen title
        Text title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = Formatting.strip(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        // Debug: Log container title once per second
        int currentTick = mc.player.age;
        if (currentTick - lastDebugTick > 20) {
            TeslaMaps.LOGGER.info("[MelodyTerminal] Container title: '{}'", cleanTitle);
            lastDebugTick = currentTick;
        }

        // Check if this is the melody terminal
        if (!cleanTitle.equals("Click the button on time!")) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[MelodyTerminal] Title doesn't match, resetting");
            }
            return;
        }

        // Initialize on first detection
        if (!initialScanDone) {
            solved = false;
            currentLane = 0;
            lastGreenPosition = -1;
            purplePosition = -1;
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            TeslaMaps.LOGGER.info("[MelodyTerminal] ===== TERMINAL DETECTED =====");
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();

        // Scan for green and purple panes every 100ms
        // But wait 300ms after lane progression to give server time to update magenta
        long timeSinceProgression = currentTime - laneProgressTime;
        if (currentTime - lastScanTime >= 100 && (laneProgressTime == 0 || timeSinceProgression >= 300)) {
            scanLane(screen);
            lastScanTime = currentTime;
        }

        // Auto-click when green aligns with purple
        // Skip auto-clicking if using custom GUI with click anywhere
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        if (!solved && lastGreenPosition != -1 && purplePosition != -1 && lastGreenPosition == purplePosition && !usingClickAnywhere) {
            long timeSinceLastClick = currentTime - lastClickTime;

            // Use configured melody delay
            int melodyDelay = TeslaMapsConfig.get().melodyTerminalClickDelay;
            if (timeSinceLastClick > melodyDelay) {
                clickTerracotta(mc, screen, currentLane);
                lastClickTime = currentTime;

                // Don't increment lane here - let it retry if the click didn't register
                // The terminal will naturally progress when successful, and we'll detect the new active lane
            }
        }

        // Detect lane progression by checking if current lane's terracotta is no longer lime
        if (currentLane < 4) { // Only check if we haven't finished all lanes
            GenericContainerScreenHandler handler = screen.getScreenHandler();
            ItemStack currentTerracotta = handler.getSlot(TERRACOTTA_SLOTS[currentLane]).getStack();

            // If terracotta is no longer lime, we've progressed to next lane
            if (!currentTerracotta.isEmpty() && currentTerracotta.getItem() != Items.LIME_TERRACOTTA) {
                currentLane++;
                laneProgressTime = currentTime; // Mark when we progressed
                TeslaMaps.LOGGER.info("[MelodyTerminal] Lane progressed! Now on lane {} (waiting 300ms for magenta update)", currentLane + 1);

                if (currentLane >= 4) {
                    solved = true;
                    TeslaMaps.LOGGER.info("[MelodyTerminal] ===== ALL LANES COMPLETE =====");
                }

                // Reset positions for new lane
                lastGreenPosition = -1;
                purplePosition = -1;
            }
        }
    }

    /**
     * Scan the current active lane for green pane and check magenta indicator column.
     */
    private static void scanLane(GenericContainerScreen screen) {
        if (currentLane >= 4) return; // All lanes done

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int[] laneSlots = LANE_SLOTS[currentLane];

        lastGreenPosition = -1;
        purplePosition = -1;

        // Check top row (slots 1-7) for magenta indicator
        for (int topSlot = 1; topSlot <= 7; topSlot++) {
            ItemStack stack = handler.getSlot(topSlot).getStack();

            if (!stack.isEmpty()) {
                String itemId = stack.getItem().toString();
                TeslaMaps.LOGGER.info("[MelodyTerminal] Top row slot {}: {} (exact: {})", topSlot, itemId,
                    stack.getItem() == Items.MAGENTA_STAINED_GLASS_PANE ? "MAGENTA" :
                    stack.getItem() == Items.PURPLE_STAINED_GLASS_PANE ? "PURPLE" :
                    stack.getItem() == Items.PINK_STAINED_GLASS_PANE ? "PINK" :
                    stack.getItem() == Items.BLACK_STAINED_GLASS_PANE ? "BLACK" :
                    "OTHER");

                if (stack.getItem() == Items.MAGENTA_STAINED_GLASS_PANE ||
                    stack.getItem() == Items.PURPLE_STAINED_GLASS_PANE ||
                    stack.getItem() == Items.PINK_STAINED_GLASS_PANE) {
                    purplePosition = topSlot;
                    TeslaMaps.LOGGER.info("[MelodyTerminal] ===== MAGENTA FOUND at top slot {} = target position {} =====", topSlot, purplePosition);
                    break;
                }
            }
        }

        // Scan the lane for green (lime) pane (skip position 7 which is the terracotta)
        TeslaMaps.LOGGER.info("[MelodyTerminal] Scanning lane {} (slots {}-{})", currentLane + 1, laneSlots[0], laneSlots[6]);
        for (int i = 0; i < 7; i++) { // Only scan positions 0-6, skip 7 (terracotta) and 8 (edge)
            int slot = laneSlots[i];
            ItemStack stack = handler.getSlot(slot).getStack();

            if (!stack.isEmpty()) {
                TeslaMaps.LOGGER.info("[MelodyTerminal] Lane {} slot {} (pos {}): {}", currentLane + 1, slot, i, stack.getItem().toString());
            }

            if (stack.isEmpty()) continue;

            // Green (lime) pane = moving indicator
            if (stack.getItem() == Items.LIME_STAINED_GLASS_PANE) {
                lastGreenPosition = i;
                TeslaMaps.LOGGER.info("[MelodyTerminal] ===== Lane {} - GREEN at position {} =====", currentLane + 1, i);
            }
        }

        if (lastGreenPosition != -1 && purplePosition != -1) {
            if (lastGreenPosition == purplePosition) {
                TeslaMaps.LOGGER.info("[MelodyTerminal] ===== ALIGNMENT! Lane {} - Green at position {} matches magenta column {} =====",
                    currentLane + 1, lastGreenPosition, purplePosition);
            }
        }
    }

    /**
     * Click the terracotta for the current lane.
     */
    private static void clickTerracotta(MinecraftClient mc, GenericContainerScreen screen, int lane) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[MelodyTerminal] Cannot click - player is null");
            return;
        }

        int slotToClick = TERRACOTTA_SLOTS[lane];
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[MelodyTerminal] ===== CLICKING TERRACOTTA =====");
        TeslaMaps.LOGGER.info("[MelodyTerminal] Lane {} - Clicking slot {}", lane + 1, slotToClick);

        // Click the slot
        mc.interactionManager.clickSlot(
            handler.syncId,
            slotToClick,
            0,  // Left click
            SlotActionType.PICKUP,
            mc.player
        );

        // Reset positions for next lane
        lastGreenPosition = -1;
        purplePosition = -1;
    }

    /**
     * For easy mode: returns the slot that should be clicked next, or -1 if none.
     */
    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || currentLane >= 4) return -1;

        // When green aligns with purple, click the terracotta
        if (lastGreenPosition != -1 && purplePosition != -1 && lastGreenPosition == purplePosition) {
            return TERRACOTTA_SLOTS[currentLane];
        }

        return -1;
    }

    /**
     * For custom GUI: get current lane (0-3).
     */
    public static int getCurrentLane() {
        return currentLane;
    }

    /**
     * For custom GUI: get the last green position.
     */
    public static int getLastGreenPosition() {
        return lastGreenPosition;
    }

    /**
     * For custom GUI: get the purple indicator position.
     */
    public static int getPurplePosition() {
        return purplePosition;
    }

    public static void reset() {
        if (initialScanDone) {
            TeslaMaps.LOGGER.info("[MelodyTerminal] Resetting");
        }
        currentLane = 0;
        lastGreenPosition = -1;
        purplePosition = -1;
        solved = false;
        initialScanDone = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
        lastDebugTick = 0;
        laneProgressTime = 0;
    }
}
