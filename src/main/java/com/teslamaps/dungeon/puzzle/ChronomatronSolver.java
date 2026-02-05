package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Experiment Solver - Chronomatron (Superpairs table)
 * Memory sequence puzzle - items pulse with glint in order, player must click them back.
 *
 * State machine:
 * REMEMBER - Watch items with glint to build sequence
 * WAIT - Sequence complete, waiting for timer to start
 * SHOW - Timer started, auto-click items in remembered order
 * END - Round complete, waiting for next round or experiment end
 */
public class ChronomatronSolver {

    // Terracotta to Glass mapping for color matching
    private static final Map<Item, Item> TERRACOTTA_TO_GLASS = Map.ofEntries(
        Map.entry(Items.RED_TERRACOTTA, Items.RED_STAINED_GLASS),
        Map.entry(Items.ORANGE_TERRACOTTA, Items.ORANGE_STAINED_GLASS),
        Map.entry(Items.YELLOW_TERRACOTTA, Items.YELLOW_STAINED_GLASS),
        Map.entry(Items.LIME_TERRACOTTA, Items.LIME_STAINED_GLASS),
        Map.entry(Items.GREEN_TERRACOTTA, Items.GREEN_STAINED_GLASS),
        Map.entry(Items.CYAN_TERRACOTTA, Items.CYAN_STAINED_GLASS),
        Map.entry(Items.LIGHT_BLUE_TERRACOTTA, Items.LIGHT_BLUE_STAINED_GLASS),
        Map.entry(Items.BLUE_TERRACOTTA, Items.BLUE_STAINED_GLASS),
        Map.entry(Items.PURPLE_TERRACOTTA, Items.PURPLE_STAINED_GLASS),
        Map.entry(Items.PINK_TERRACOTTA, Items.PINK_STAINED_GLASS)
    );

    private enum State { REMEMBER, WAIT, SHOW, END }

    private static State state = State.REMEMBER;
    private static final List<Item> chronomatronSlots = new ArrayList<>(); // Sequence of items to click
    private static int chronomatronChainLengthCount = 0; // Index of current item being shown
    private static int chronomatronCurrentSlot = 0; // Slot of currently glowing item
    private static int chronomatronCurrentOrdinal = 0; // Next index in sequence to click
    private static boolean isSingleRow = false; // High/Grand/Supreme are single row

    private static boolean initialScanDone = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean isClicked = false;
    private static int lastDebugTick = 0;

    // Track previous slot states for glint detection
    private static Map<Integer, Boolean> previousGlintState = new HashMap<>();

    public static void tick() {
        // DISABLED - Auto experiment feature commented out
        if (true) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        /* DISABLED
        if (!TeslaMapsConfig.get().solveChronomatron) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[Chronomatron] Feature disabled, resetting");
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
                TeslaMaps.LOGGER.info("[Chronomatron] Screen closed, resetting");
            }
            reset();
            return;
        }

        Text title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = Formatting.strip(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        // Check if this is Chronomatron
        if (!cleanTitle.matches("Chronomatron \\(\\w+\\)")) {
            return;
        }

        // Initialize on first detection
        if (!initialScanDone) {
            state = State.REMEMBER;
            chronomatronSlots.clear();
            chronomatronChainLengthCount = 0;
            chronomatronCurrentSlot = 0;
            chronomatronCurrentOrdinal = 0;
            previousGlintState.clear();
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            isClicked = false;

            // Determine if single row based on level
            isSingleRow = cleanTitle.endsWith("(High)") || cleanTitle.endsWith("(Grand)") || cleanTitle.endsWith("(Supreme)");

            TeslaMaps.LOGGER.info("[Chronomatron] ===== DETECTED: {} (singleRow={}) =====", cleanTitle, isSingleRow);
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        // Scan for slot changes every tick
        if (currentTime - lastScanTime >= 50) {
            processSlotChanges(handler);
            lastScanTime = currentTime;
        }

        // Auto-click logic when in SHOW state
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        // Break threshold
        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[Chronomatron] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (state == State.SHOW && !usingClickAnywhere && !isClicked) {
            if (chronomatronCurrentOrdinal < chronomatronSlots.size()) {
                long timeSinceOpen = currentTime - terminalOpenTime;
                long timeSinceLastClick = currentTime - lastClickTime;

                int randomization = TeslaMapsConfig.get().terminalClickRandomization;
                int randomInitialDelay = TeslaMapsConfig.get().experimentClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
                int randomInterval = TeslaMapsConfig.get().experimentClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

                boolean shouldClick = false;
                if (chronomatronCurrentOrdinal == 0 && timeSinceOpen >= randomInitialDelay) {
                    shouldClick = true;
                } else if (chronomatronCurrentOrdinal > 0 && timeSinceLastClick >= randomInterval) {
                    shouldClick = true;
                }

                if (shouldClick) {
                    performNextClick(mc, handler);
                }
            }
        }
    }

    /**
     * Process slot changes to detect glint (flashing items) and timer state.
     */
    private static void processSlotChanges(GenericContainerScreenHandler handler) {
        // Determine valid slot range based on layout
        int minSlot = isSingleRow ? 17 : 10;
        int maxSlot = isSingleRow ? 25 : 34;

        // Check instruction slot (49) for timer/state changes
        ItemStack instructionStack = handler.getSlot(49).getStack();
        String instructionName = instructionStack.getName().getString();

        switch (state) {
            case REMEMBER -> {
                // Look for items with glint
                for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
                    Slot slot = handler.getSlot(slotId);
                    if (slot == null) continue;

                    ItemStack stack = slot.getStack();
                    if (stack.isEmpty()) continue;

                    boolean hasGlint = stack.hasGlint();
                    Boolean prevGlint = previousGlintState.get(slotId);

                    // Item just started glowing
                    if (hasGlint && (prevGlint == null || !prevGlint)) {
                        if (chronomatronCurrentSlot == 0) {
                            // New item in sequence
                            if (chronomatronSlots.size() <= chronomatronChainLengthCount) {
                                Item glassItem = TERRACOTTA_TO_GLASS.get(stack.getItem());
                                if (glassItem != null) {
                                    chronomatronSlots.add(glassItem);
                                    TeslaMaps.LOGGER.info("[Chronomatron] Added item {} to sequence (total: {})",
                                        glassItem.toString(), chronomatronSlots.size());
                                    state = State.WAIT;
                                }
                            } else {
                                chronomatronChainLengthCount++;
                            }
                            chronomatronCurrentSlot = slotId;
                        }
                    }
                    // Item stopped glowing
                    else if (!hasGlint && prevGlint != null && prevGlint && chronomatronCurrentSlot == slotId) {
                        chronomatronCurrentSlot = 0;
                    }

                    previousGlintState.put(slotId, hasGlint);
                }
            }
            case WAIT -> {
                if (instructionName.startsWith("Timer: ")) {
                    TeslaMaps.LOGGER.info("[Chronomatron] Timer started, entering SHOW state with {} items", chronomatronSlots.size());
                    state = State.SHOW;
                    chronomatronCurrentOrdinal = 0;
                    terminalOpenTime = System.currentTimeMillis(); // Reset for click timing
                }
            }
            case END -> {
                if (!instructionName.startsWith("Timer: ")) {
                    if (instructionName.equals("Remember the pattern!")) {
                        TeslaMaps.LOGGER.info("[Chronomatron] New round starting");
                        chronomatronChainLengthCount = 0;
                        chronomatronCurrentOrdinal = 0;
                        chronomatronCurrentSlot = 0;
                        previousGlintState.clear();
                        state = State.REMEMBER;
                    } else {
                        TeslaMaps.LOGGER.info("[Chronomatron] Experiment ended");
                        reset();
                    }
                }
            }
            case SHOW -> {
                // Nothing special to do in SHOW state for scanning
            }
        }
    }

    /**
     * Click the next item in the sequence.
     */
    private static void performNextClick(MinecraftClient mc, GenericContainerScreenHandler handler) {
        if (mc.player == null || chronomatronCurrentOrdinal >= chronomatronSlots.size()) return;

        Item targetItem = chronomatronSlots.get(chronomatronCurrentOrdinal);

        // Find a slot with the target item (or its terracotta equivalent)
        int slotToClick = -1;
        int minSlot = isSingleRow ? 17 : 10;
        int maxSlot = isSingleRow ? 25 : 34;

        for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // Check if this slot matches the target
            if (stack.getItem() == targetItem) {
                slotToClick = slotId;
                break;
            }
            // Check terracotta equivalent
            Item glassEquiv = TERRACOTTA_TO_GLASS.get(stack.getItem());
            if (glassEquiv == targetItem) {
                slotToClick = slotId;
                break;
            }
        }

        if (slotToClick == -1) {
            TeslaMaps.LOGGER.warn("[Chronomatron] Could not find slot for item {}", targetItem);
            return;
        }

        TeslaMaps.LOGGER.info("[Chronomatron] Clicking slot {} for item {} ({}/{})",
            slotToClick, targetItem, chronomatronCurrentOrdinal + 1, chronomatronSlots.size());

        mc.interactionManager.clickSlot(
            handler.syncId,
            slotToClick,
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        chronomatronCurrentOrdinal++;
        lastClickTime = System.currentTimeMillis();
        isClicked = true;

        // Check if we finished the sequence
        if (chronomatronCurrentOrdinal >= chronomatronSlots.size()) {
            TeslaMaps.LOGGER.info("[Chronomatron] Sequence complete, entering END state");
            state = State.END;
        }
    }

    /**
     * Get the next correct slot to click (for click-anywhere mode).
     */
    public static int getNextCorrectSlot() {
        if (!initialScanDone || state != State.SHOW || chronomatronCurrentOrdinal >= chronomatronSlots.size()) {
            return -1;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return -1;

        GenericContainerScreenHandler handler = screen.getScreenHandler();
        Item targetItem = chronomatronSlots.get(chronomatronCurrentOrdinal);

        int minSlot = isSingleRow ? 17 : 10;
        int maxSlot = isSingleRow ? 25 : 34;

        for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == targetItem) return slotId;
            Item glassEquiv = TERRACOTTA_TO_GLASS.get(stack.getItem());
            if (glassEquiv == targetItem) return slotId;
        }

        return -1;
    }

    /**
     * Mark that a slot was clicked (for click-anywhere mode tracking).
     */
    public static void markSlotClicked(int slot) {
        if (state == State.SHOW && chronomatronCurrentOrdinal < chronomatronSlots.size()) {
            chronomatronCurrentOrdinal++;
            if (chronomatronCurrentOrdinal >= chronomatronSlots.size()) {
                state = State.END;
            }
        }
    }

    /**
     * Get the current sequence for display purposes.
     */
    public static List<Item> getSequence() {
        return Collections.unmodifiableList(chronomatronSlots);
    }

    /**
     * Get the current ordinal (next item index to click).
     */
    public static int getCurrentOrdinal() {
        return chronomatronCurrentOrdinal;
    }

    /**
     * Check if solver is active.
     */
    public static boolean isActive() {
        return initialScanDone && state == State.SHOW;
    }

    public static void reset() {
        if (initialScanDone) {
            TeslaMaps.LOGGER.info("[Chronomatron] Resetting");
        }
        state = State.REMEMBER;
        chronomatronSlots.clear();
        chronomatronChainLengthCount = 0;
        chronomatronCurrentSlot = 0;
        chronomatronCurrentOrdinal = 0;
        previousGlintState.clear();
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
        lastDebugTick = 0;
    }
}
