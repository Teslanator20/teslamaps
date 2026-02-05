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
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Experiment Solver - Superpairs (Memory matching game)
 * Click items to reveal them, find matching pairs.
 *
 * Unlike Chronomatron, this doesn't have a remember phase - we learn items
 * as the player clicks them, then highlight matches.
 */
public class SuperpairsSolver {

    // Map of slot -> revealed item
    private static final Map<Integer, ItemStack> revealedItems = new HashMap<>();
    // Set of slots that are already matched (found their pair)
    private static final Set<Integer> matchedSlots = new HashSet<>();
    // The last clicked slot
    private static int lastClickedSlot = -1;
    // The item from the last click (before it gets hidden again)
    private static ItemStack lastClickedItem = ItemStack.EMPTY;

    private static boolean initialScanDone = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean isClicked = false;

    // Valid slot range for Superpairs (varies by level)
    private static int minSlot = 9;
    private static int maxSlot = 44;

    public static void tick() {
        // DISABLED - Auto experiment feature commented out
        if (true) {
            reset();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        /* DISABLED
        if (!TeslaMapsConfig.get().solveSuperpairs) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[Superpairs] Feature disabled, resetting");
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
                TeslaMaps.LOGGER.info("[Superpairs] Screen closed, resetting");
            }
            reset();
            return;
        }

        Text title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = Formatting.strip(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        // Check if this is Superpairs
        if (!cleanTitle.matches("Superpairs \\(\\w+\\)")) {
            return;
        }

        // Initialize on first detection
        if (!initialScanDone) {
            revealedItems.clear();
            matchedSlots.clear();
            lastClickedSlot = -1;
            lastClickedItem = ItemStack.EMPTY;
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            isClicked = false;

            TeslaMaps.LOGGER.info("[Superpairs] ===== DETECTED: {} =====", cleanTitle);
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        // Scan for revealed items every 100ms
        if (currentTime - lastScanTime >= 100) {
            scanRevealedItems(handler);
            lastScanTime = currentTime;
        }

        // Auto-click logic - find and click matching pairs
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        // Break threshold
        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[Superpairs] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!usingClickAnywhere && !isClicked) {
            int nextSlot = findNextSlotToClick(handler);
            if (nextSlot != -1) {
                long timeSinceLastClick = currentTime - lastClickTime;

                int randomization = TeslaMapsConfig.get().terminalClickRandomization;
                int randomInterval = TeslaMapsConfig.get().experimentClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

                if (timeSinceLastClick >= randomInterval) {
                    performClick(mc, handler, nextSlot);
                }
            }
        }
    }

    /**
     * Scan the container for revealed items (non-glass panes).
     */
    private static void scanRevealedItems(GenericContainerScreenHandler handler) {
        for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
            // Skip already matched slots
            if (matchedSlots.contains(slotId)) continue;

            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // Skip glass panes (hidden items)
            if (stack.getItem() == Items.CYAN_STAINED_GLASS ||
                stack.getItem() == Items.BLACK_STAINED_GLASS_PANE ||
                stack.getItem() == Items.AIR) {
                continue;
            }

            // This item is revealed - store it
            if (!revealedItems.containsKey(slotId)) {
                revealedItems.put(slotId, stack.copy());
                TeslaMaps.LOGGER.info("[Superpairs] Revealed item at slot {}: {}", slotId, stack.getName().getString());

                // Check if this creates a match with another revealed item
                for (Map.Entry<Integer, ItemStack> entry : revealedItems.entrySet()) {
                    int otherSlot = entry.getKey();
                    if (otherSlot == slotId) continue;
                    if (matchedSlots.contains(otherSlot)) continue;

                    if (ItemStack.areItemsEqual(stack, entry.getValue())) {
                        TeslaMaps.LOGGER.info("[Superpairs] Found match: slots {} and {}", slotId, otherSlot);
                        matchedSlots.add(slotId);
                        matchedSlots.add(otherSlot);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Find the next slot to click based on known pairs.
     */
    private static int findNextSlotToClick(GenericContainerScreenHandler handler) {
        // First priority: If we have an item revealed (last click), find its match
        if (lastClickedSlot != -1 && !lastClickedItem.isEmpty()) {
            for (Map.Entry<Integer, ItemStack> entry : revealedItems.entrySet()) {
                int slot = entry.getKey();
                if (slot == lastClickedSlot) continue;
                if (matchedSlots.contains(slot)) continue;

                if (ItemStack.areItemsEqual(lastClickedItem, entry.getValue())) {
                    return slot;
                }
            }
        }

        // Second priority: Find any known pair where we know both locations
        for (Map.Entry<Integer, ItemStack> entry1 : revealedItems.entrySet()) {
            int slot1 = entry1.getKey();
            if (matchedSlots.contains(slot1)) continue;

            // Check if this slot currently shows a hidden item (glass)
            Slot gameSlot1 = handler.getSlot(slot1);
            if (gameSlot1 == null) continue;
            ItemStack currentStack1 = gameSlot1.getStack();

            // Skip if item is still revealed
            if (!isHiddenItem(currentStack1)) continue;

            // Look for a match
            for (Map.Entry<Integer, ItemStack> entry2 : revealedItems.entrySet()) {
                int slot2 = entry2.getKey();
                if (slot2 <= slot1) continue; // Avoid duplicates
                if (matchedSlots.contains(slot2)) continue;

                if (ItemStack.areItemsEqual(entry1.getValue(), entry2.getValue())) {
                    // Found a known pair - click the first slot
                    return slot1;
                }
            }
        }

        return -1;
    }

    private static boolean isHiddenItem(ItemStack stack) {
        return stack.isEmpty() ||
               stack.getItem() == Items.CYAN_STAINED_GLASS ||
               stack.getItem() == Items.BLACK_STAINED_GLASS_PANE;
    }

    /**
     * Perform a click on the given slot.
     */
    private static void performClick(MinecraftClient mc, GenericContainerScreenHandler handler, int slotId) {
        if (mc.player == null) return;

        TeslaMaps.LOGGER.info("[Superpairs] Clicking slot {}", slotId);

        mc.interactionManager.clickSlot(
            handler.syncId,
            slotId,
            0,
            SlotActionType.PICKUP,
            mc.player
        );

        lastClickedSlot = slotId;
        ItemStack knownItem = revealedItems.get(slotId);
        lastClickedItem = knownItem != null ? knownItem.copy() : ItemStack.EMPTY;

        lastClickTime = System.currentTimeMillis();
        isClicked = true;
    }

    /**
     * Get the next correct slot to click (for click-anywhere mode).
     */
    public static int getNextCorrectSlot() {
        if (!initialScanDone) return -1;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return -1;

        return findNextSlotToClick(screen.getScreenHandler());
    }

    /**
     * Mark that a slot was clicked (for click-anywhere mode tracking).
     */
    public static void markSlotClicked(int slot) {
        lastClickedSlot = slot;
        ItemStack knownItem = revealedItems.get(slot);
        lastClickedItem = knownItem != null ? knownItem.copy() : ItemStack.EMPTY;
    }

    /**
     * Get all revealed items for display.
     */
    public static Map<Integer, ItemStack> getRevealedItems() {
        return Collections.unmodifiableMap(revealedItems);
    }

    /**
     * Get matched slots for display.
     */
    public static Set<Integer> getMatchedSlots() {
        return Collections.unmodifiableSet(matchedSlots);
    }

    /**
     * Check if solver is active.
     */
    public static boolean isActive() {
        return initialScanDone;
    }

    public static void reset() {
        if (initialScanDone) {
            TeslaMaps.LOGGER.info("[Superpairs] Resetting");
        }
        revealedItems.clear();
        matchedSlots.clear();
        lastClickedSlot = -1;
        lastClickedItem = ItemStack.EMPTY;
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
    }
}
