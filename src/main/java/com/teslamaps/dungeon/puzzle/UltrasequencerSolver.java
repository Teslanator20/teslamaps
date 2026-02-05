package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.block.Block;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Experiment Solver - Ultrasequencer (Number sequence puzzle)
 * Remember numbered items (1-14), then click them in order.
 *
 * State machine:
 * REMEMBER - Scan for numbered items while "Remember the pattern!" is shown
 * WAIT - Items memorized, waiting for timer to start
 * SHOW - Timer started, auto-click items in sequence 1, 2, 3...
 * END - Round complete (detected by glass pane color change)
 */
public class UltrasequencerSolver {

    // Valid pane slots (9-44 for Metaphysical level)
    private static final int MIN_PANE_SLOT = 9;
    private static final int MAX_PANE_SLOT = 44;

    private enum State { REMEMBER, WAIT, SHOW, END }

    private static State state = State.REMEMBER;
    // Map of slot -> stack count (the number 1-14)
    private static final Map<Integer, Integer> numberedSlots = new HashMap<>();
    // Next number to click
    private static int nextNumber = 1;
    // Last known glass pane color (to detect round changes)
    private static DyeColor lastPaneColor = null;

    private static boolean initialScanDone = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean isClicked = false;

    public static void tick() {
        // DISABLED - Auto experiment feature commented out
        if (true) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        /* DISABLED
        if (!TeslaMapsConfig.get().solveUltrasequencer) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[Ultrasequencer] Feature disabled, resetting");
            }
            reset();
            return;
        }
        */

        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[Ultrasequencer] Screen closed, resetting");
            }
            reset();
            return;
        }

        Text title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = Formatting.strip(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        // Check if this is Ultrasequencer
        if (!cleanTitle.matches("Ultrasequencer \\(\\w+\\)")) {
            return;
        }

        // Initialize on first detection
        if (!initialScanDone) {
            state = State.REMEMBER;
            numberedSlots.clear();
            nextNumber = 1;
            lastPaneColor = null;
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            isClicked = false;

            TeslaMaps.LOGGER.info("[Ultrasequencer] ===== DETECTED: {} =====", cleanTitle);
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        // Process state machine
        processStateMachine(handler);

        // Check for pane color change (round transition)
        checkPaneColorChange(handler);

        // Auto-click logic when in SHOW state
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        // Break threshold
        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[Ultrasequencer] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (state == State.SHOW && !usingClickAnywhere && !isClicked) {
            int nextSlot = findSlotForNumber(nextNumber);
            if (nextSlot != -1) {
                long timeSinceLastClick = currentTime - lastClickTime;

                int randomization = TeslaMapsConfig.get().terminalClickRandomization;
                int randomInitialDelay = TeslaMapsConfig.get().experimentClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
                int randomInterval = TeslaMapsConfig.get().experimentClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

                boolean shouldClick = false;
                if (nextNumber == 1 && (currentTime - terminalOpenTime) >= randomInitialDelay) {
                    shouldClick = true;
                } else if (nextNumber > 1 && timeSinceLastClick >= randomInterval) {
                    shouldClick = true;
                }

                if (shouldClick) {
                    performClick(mc, handler, nextSlot);
                }
            }
        }
    }

    /**
     * Process state transitions based on container contents.
     */
    private static void processStateMachine(GenericContainerScreenHandler handler) {
        ItemStack instructionStack = handler.getSlot(49).getStack();
        String instructionName = instructionStack.getName().getString();

        switch (state) {
            case REMEMBER -> {
                if (instructionName.equals("Remember the pattern!")) {
                    // Scan for numbered items
                    scanNumberedItems(handler);
                    if (!numberedSlots.isEmpty()) {
                        TeslaMaps.LOGGER.info("[Ultrasequencer] Memorized {} numbered items", numberedSlots.size());
                        state = State.WAIT;
                    }
                }
            }
            case WAIT -> {
                if (instructionName.startsWith("Timer: ")) {
                    TeslaMaps.LOGGER.info("[Ultrasequencer] Timer started, entering SHOW state");
                    state = State.SHOW;
                    nextNumber = 1;
                    terminalOpenTime = System.currentTimeMillis();
                }
            }
            case END -> {
                if (!instructionName.startsWith("Timer: ")) {
                    if (instructionName.equals("Remember the pattern!")) {
                        TeslaMaps.LOGGER.info("[Ultrasequencer] New round starting");
                        numberedSlots.clear();
                        nextNumber = 1;
                        state = State.REMEMBER;
                    } else {
                        TeslaMaps.LOGGER.info("[Ultrasequencer] Experiment ended");
                        reset();
                    }
                }
            }
            case SHOW -> {
                // Stay in SHOW until color change detected
            }
        }
    }

    /**
     * Scan container for numbered items (stack count = sequence number).
     */
    private static void scanNumberedItems(GenericContainerScreenHandler handler) {
        numberedSlots.clear();

        for (int slotId = MIN_PANE_SLOT; slotId <= MAX_PANE_SLOT; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // Skip black glass panes (border)
            if (stack.getItem() == Items.BLACK_STAINED_GLASS_PANE) continue;

            String name = stack.getName().getString();
            // Check if name is a number
            if (name.matches("\\d+")) {
                int number = Integer.parseInt(name);
                if (number >= 1 && number <= 14) {
                    numberedSlots.put(slotId, number);
                    TeslaMaps.LOGGER.info("[Ultrasequencer] Found item {} at slot {}", number, slotId);
                }
            }
        }
    }

    /**
     * Check for glass pane color changes (indicates round transition).
     */
    private static void checkPaneColorChange(GenericContainerScreenHandler handler) {
        for (int slotId = MIN_PANE_SLOT; slotId <= MAX_PANE_SLOT; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // Check if it's a stained glass pane
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block instanceof StainedGlassPaneBlock glassPaneBlock) {
                    DyeColor color = glassPaneBlock.getColor();

                    // Skip black (border)
                    if (color == DyeColor.BLACK) continue;

                    // Color changed = round ended
                    if (lastPaneColor != null && lastPaneColor != color) {
                        TeslaMaps.LOGGER.info("[Ultrasequencer] Pane color changed from {} to {}, round ended",
                            lastPaneColor, color);
                        state = State.END;
                    }

                    lastPaneColor = color;
                    return;
                }
            }
        }
    }

    /**
     * Find the slot containing the given number.
     */
    private static int findSlotForNumber(int number) {
        for (Map.Entry<Integer, Integer> entry : numberedSlots.entrySet()) {
            if (entry.getValue() == number) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Perform a click on the given slot.
     */
    private static void performClick(MinecraftClient mc, GenericContainerScreenHandler handler, int slotId) {
        if (mc.player == null) return;

        TeslaMaps.LOGGER.info("[Ultrasequencer] Clicking slot {} for number {}", slotId, nextNumber);

        mc.interactionManager.clickSlot(
            handler.syncId,
            slotId,
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        nextNumber++;
        lastClickTime = System.currentTimeMillis();
        isClicked = true;

        // Check if we clicked the last number
        if (nextNumber > numberedSlots.size()) {
            TeslaMaps.LOGGER.info("[Ultrasequencer] Sequence complete, entering END state");
            state = State.END;
        }
    }

    /**
     * Get the next correct slot to click (for click-anywhere mode).
     */
    public static int getNextCorrectSlot() {
        if (!initialScanDone || state != State.SHOW) return -1;
        return findSlotForNumber(nextNumber);
    }

    /**
     * Mark that a slot was clicked (for click-anywhere mode tracking).
     */
    public static void markSlotClicked(int slot) {
        Integer number = numberedSlots.get(slot);
        if (number != null && number == nextNumber) {
            nextNumber++;
            if (nextNumber > numberedSlots.size()) {
                state = State.END;
            }
        }
    }

    /**
     * Get all numbered slots for display.
     */
    public static Map<Integer, Integer> getNumberedSlots() {
        return Collections.unmodifiableMap(numberedSlots);
    }

    /**
     * Get the next number to click.
     */
    public static int getNextNumber() {
        return nextNumber;
    }

    /**
     * Check if solver is active.
     */
    public static boolean isActive() {
        return initialScanDone && state == State.SHOW;
    }

    public static void reset() {
        if (initialScanDone) {
            TeslaMaps.LOGGER.info("[Ultrasequencer] Resetting");
        }
        state = State.REMEMBER;
        numberedSlots.clear();
        nextNumber = 1;
        lastPaneColor = null;
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
    }
}
