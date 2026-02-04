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
 * F7 Terminal Solver - "Change all to same color!" (Rubix Cube)
 * Clicks slots the required number of times to solve the puzzle.
 * Color cycle: RED → ORANGE → YELLOW → GREEN → BLUE → RED
 */
public class RubixTerminal {
    private static final String[] COLOR_CYCLE = {"RED", "ORANGE", "YELLOW", "GREEN", "BLUE"};

    private static class ClickAction {
        int slot;
        boolean rightClick; // true = right-click (backward), false = left-click (forward)
        int clickCount;

        ClickAction(int slot, boolean rightClick, int clicks) {
            this.slot = slot;
            this.rightClick = rightClick;
            this.clickCount = clicks;
        }
    }

    private static List<ClickAction> clickQueue = new ArrayList<>();
    private static int clickQueueIndex = 0;
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean initialScanDone = false;
    private static int lastDebugTick = 0;

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!TeslaMapsConfig.get().solveRubixTerminal) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[RubixTerminal] DEBUG: Feature disabled, resetting");
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
                TeslaMaps.LOGGER.info("[RubixTerminal] DEBUG: Screen closed, resetting");
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
            TeslaMaps.LOGGER.info("[RubixTerminal] DEBUG: Container title: '{}'", cleanTitle);
            lastDebugTick = currentTick;
        }

        // Check if this is the rubix terminal
        TeslaMaps.LOGGER.info("[RubixTerminal] DEBUG: Checking title match. cleanTitle='{}', equals={}",
            cleanTitle, cleanTitle.equals("Change all to same color!"));

        if (!cleanTitle.equals("Change all to same color!")) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[RubixTerminal] DEBUG: Title doesn't match, resetting");
            }
            return;
        }

        TeslaMaps.LOGGER.info("[RubixTerminal] DEBUG: Title matched! initialScanDone={}", initialScanDone);

        // Initialize on first detection
        if (!initialScanDone) {
            solved = false;
            clickQueue.clear();
            clickQueueIndex = 0;
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            TeslaMaps.LOGGER.info("[RubixTerminal] ===== TERMINAL DETECTED =====");
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();

        // Scan for slots needing clicks every 200ms
        if (currentTime - lastScanTime >= 200) {
            scanAndBuildClickQueue(screen);
            lastScanTime = currentTime;
        }

        // Auto-click from the queue
        // Skip auto-clicking if using custom GUI with click anywhere
        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        if (!solved && clickQueueIndex < clickQueue.size() && !usingClickAnywhere) {
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            // Use configured delays with ±10ms randomization for human-like behavior
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(-10, 11);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(-10, 11);

            // Initial delay before first click
            if (clickQueueIndex == 0 && timeSinceOpen >= randomInitialDelay) {
                performNextClick(mc, screen);
            }
            // Delay between subsequent clicks
            else if (clickQueueIndex > 0 && timeSinceLastClick >= randomInterval) {
                performNextClick(mc, screen);
            }
        } else if (clickQueueIndex >= clickQueue.size() && initialScanDone) {
            // Queue finished - check if container is still open after delay
            long timeSinceLastClick = currentTime - lastClickTime;

            if (clickQueue.isEmpty()) {
                // Empty queue from the start means all colors matched
                if (!solved) {
                    solved = true;
                    TeslaMaps.LOGGER.info("[RubixTerminal] ===== ALL COLORS MATCHED =====");
                }
            } else if (timeSinceLastClick > 1000) {
                // Container still open 1 second after finishing queue - restart!
                TeslaMaps.LOGGER.info("[RubixTerminal] Container still open after finishing queue - restarting scan!");
                clickQueue.clear();
                clickQueueIndex = 0;
                lastScanTime = 0; // Force immediate re-scan
            }
        }
    }

    /**
     * Scan all slots and build an optimal click queue.
     * Calculates the best target color and optimal click directions.
     * Only builds queue if it's currently empty (don't rebuild while executing).
     */
    private static void scanAndBuildClickQueue(GenericContainerScreen screen) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        // The 3x3 grid layout:
        // Top row: 12(left), 13(mid), 14(right)
        // Mid row: 21(left), 22(mid), 23(right)
        // Bot row: 30(left), 31(mid), 32(right)
        int[] gridSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};

        // Read current colors
        Map<Integer, String> slotColors = new HashMap<>();
        for (int slot : gridSlots) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;

            String color = getPaneColor(stack);
            if (color != null) {
                slotColors.put(slot, color);
            }
        }

        if (slotColors.isEmpty()) {
            TeslaMaps.LOGGER.warn("[RubixTerminal] No colors found in grid!");
            return;
        }

        // Find optimal target color (requires fewest total clicks)
        String bestTarget = findOptimalTargetColor(slotColors);

        // Check if all slots already match target
        boolean allMatch = true;
        for (String color : slotColors.values()) {
            if (!color.equals(bestTarget)) {
                allMatch = false;
                break;
            }
        }

        if (allMatch) {
            TeslaMaps.LOGGER.info("[RubixTerminal] All colors already match {}!", bestTarget);
            clickQueue.clear();
            return;
        }

        TeslaMaps.LOGGER.info("[RubixTerminal] ===== SCANNING =====");
        TeslaMaps.LOGGER.info("[RubixTerminal] Optimal target color: {}", bestTarget);

        // Build click queue (only if not currently executing)
        if (clickQueueIndex >= clickQueue.size()) {
            clickQueue.clear();
            clickQueueIndex = 0;

            for (Map.Entry<Integer, String> entry : slotColors.entrySet()) {
                int slot = entry.getKey();
                String currentColor = entry.getValue();

                if (currentColor.equals(bestTarget)) continue; // Already correct

                // Calculate optimal clicks
                int forwardClicks = getDistanceForward(currentColor, bestTarget);
                int backwardClicks = getDistanceBackward(currentColor, bestTarget);

                if (forwardClicks <= backwardClicks) {
                    // Use left-click (forward)
                    for (int i = 0; i < forwardClicks; i++) {
                        clickQueue.add(new ClickAction(slot, false, forwardClicks));
                    }
                    TeslaMaps.LOGGER.info("[RubixTerminal] Slot {}: {} -> {} (LEFT x{})", slot, currentColor, bestTarget, forwardClicks);
                } else {
                    // Use right-click (backward)
                    for (int i = 0; i < backwardClicks; i++) {
                        clickQueue.add(new ClickAction(slot, true, backwardClicks));
                    }
                    TeslaMaps.LOGGER.info("[RubixTerminal] Slot {}: {} -> {} (RIGHT x{})", slot, currentColor, bestTarget, backwardClicks);
                }
            }

            if (clickQueue.isEmpty()) {
                TeslaMaps.LOGGER.info("[RubixTerminal] All slots match target color!");
            } else {
                TeslaMaps.LOGGER.info("[RubixTerminal] Click queue: {} clicks", clickQueue.size());
            }
        }
    }

    /**
     * Find the target color that requires the fewest total clicks.
     */
    private static String findOptimalTargetColor(Map<Integer, String> slotColors) {
        String bestTarget = null;
        int minTotalClicks = Integer.MAX_VALUE;

        for (String targetColor : COLOR_CYCLE) {
            int totalClicks = 0;
            for (String currentColor : slotColors.values()) {
                if (currentColor.equals(targetColor)) continue;
                int forward = getDistanceForward(currentColor, targetColor);
                int backward = getDistanceBackward(currentColor, targetColor);
                totalClicks += Math.min(forward, backward);
            }

            if (totalClicks < minTotalClicks) {
                minTotalClicks = totalClicks;
                bestTarget = targetColor;
            }
        }

        return bestTarget;
    }

    /**
     * Get distance forward in color cycle.
     * RED → ORANGE → YELLOW → GREEN → BLUE → RED
     */
    private static int getDistanceForward(String from, String to) {
        int fromIdx = getColorIndex(from);
        int toIdx = getColorIndex(to);
        if (fromIdx == -1 || toIdx == -1) return 999;

        if (toIdx >= fromIdx) {
            return toIdx - fromIdx;
        } else {
            return (COLOR_CYCLE.length - fromIdx) + toIdx;
        }
    }

    /**
     * Get distance backward in color cycle.
     * RED ← BLUE ← GREEN ← YELLOW ← ORANGE ← RED
     */
    private static int getDistanceBackward(String from, String to) {
        int fromIdx = getColorIndex(from);
        int toIdx = getColorIndex(to);
        if (fromIdx == -1 || toIdx == -1) return 999;

        if (toIdx <= fromIdx) {
            return fromIdx - toIdx;
        } else {
            return fromIdx + (COLOR_CYCLE.length - toIdx);
        }
    }

    /**
     * Get index of color in cycle.
     */
    private static int getColorIndex(String color) {
        for (int i = 0; i < COLOR_CYCLE.length; i++) {
            if (COLOR_CYCLE[i].equals(color)) return i;
        }
        return -1;
    }

    /**
     * Get the color name from a stained glass pane item.
     * Rubix only uses 5 colors: red → orange → yellow → green → blue
     */
    private static String getPaneColor(ItemStack stack) {
        if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) return "RED";
        if (stack.getItem() == Items.ORANGE_STAINED_GLASS_PANE) return "ORANGE";
        if (stack.getItem() == Items.YELLOW_STAINED_GLASS_PANE) return "YELLOW";
        if (stack.getItem() == Items.GREEN_STAINED_GLASS_PANE) return "GREEN";
        if (stack.getItem() == Items.BLUE_STAINED_GLASS_PANE) return "BLUE";
        return null;
    }

    /**
     * Perform the next click from the queue.
     */
    private static void performNextClick(MinecraftClient mc, GenericContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[RubixTerminal] Cannot click - player is null");
            return;
        }

        if (clickQueueIndex >= clickQueue.size()) {
            TeslaMaps.LOGGER.warn("[RubixTerminal] No more clicks in queue!");
            return;
        }

        ClickAction action = clickQueue.get(clickQueueIndex);
        GenericContainerScreenHandler handler = screen.getScreenHandler();

        TeslaMaps.LOGGER.info("[RubixTerminal] Clicking slot {} ({} {}/{})",
            action.slot, action.rightClick ? "RIGHT" : "LEFT",
            clickQueueIndex + 1, clickQueue.size());

        // Click the slot
        mc.interactionManager.clickSlot(
            handler.syncId,
            action.slot,
            action.rightClick ? 1 : 0,  // 0 = left, 1 = right
            SlotActionType.PICKUP,
            mc.player
        );

        clickQueueIndex++;
        lastClickTime = System.currentTimeMillis();
    }

    /**
     * For easy mode: returns the next slot to click, or -1 if none.
     */
    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || clickQueueIndex >= clickQueue.size()) return -1;
        return clickQueue.get(clickQueueIndex).slot;
    }

    /**
     * For custom GUI: get the click queue for rendering.
     * Returns a map of slot -> number of clicks needed.
     */
    public static java.util.Map<Integer, Integer> getClickMap() {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        for (ClickAction action : clickQueue) {
            int clicks = action.rightClick ? -action.clickCount : action.clickCount;
            map.put(action.slot, clicks);
        }
        return map;
    }

    /**
     * For easy mode: mark a slot as clicked (advances queue).
     */
    public static void markSlotClicked(int slot) {
        if (clickQueueIndex < clickQueue.size()) {
            ClickAction action = clickQueue.get(clickQueueIndex);
            // Only advance if the clicked slot matches the current action
            if (action.slot == slot) {
                clickQueueIndex++;
                lastClickTime = System.currentTimeMillis();
                TeslaMaps.LOGGER.info("[RubixTerminal] Manually clicked slot {} - advancing queue ({}/{})",
                    slot, clickQueueIndex, clickQueue.size());
            }
        }
    }

    public static void reset() {
        if (initialScanDone) {
            TeslaMaps.LOGGER.info("[RubixTerminal] Resetting");
        }
        clickQueue.clear();
        clickQueueIndex = 0;
        solved = false;
        initialScanDone = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
        lastDebugTick = 0;
    }
}
