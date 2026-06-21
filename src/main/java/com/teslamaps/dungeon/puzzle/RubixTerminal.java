/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    private static boolean isClicked = false;

    public static void tick() {
        if (true) {
            reset();
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }

        if (!(mc.screen instanceof ContainerScreen)) {
            if (initialScanDone) {
            }
            reset();
            return;
        }

        ContainerScreen screen = (ContainerScreen) mc.screen;

        Component title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = ChatFormatting.stripFormatting(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (!cleanTitle.equals("Change all to same color!")) {
            return;
        }

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

        if (currentTime - lastScanTime >= 200) {
            int prevQueueSize = clickQueue.size();
            int prevIndex = clickQueueIndex;
            scanAndBuildClickQueue(screen);
            lastScanTime = currentTime;
            if (clickQueue.size() != prevQueueSize || clickQueueIndex != prevIndex) {
                isClicked = false;
            }
        }

        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[RubixTerminal] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!solved && clickQueueIndex < clickQueue.size() && !usingClickAnywhere && !isClicked) {
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            int randomization = TeslaMapsConfig.get().terminalClickRandomization;
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

            if (clickQueueIndex == 0 && timeSinceOpen >= randomInitialDelay) {
                performNextClick(mc, screen);
            }
            else if (clickQueueIndex > 0 && timeSinceLastClick >= randomInterval) {
                performNextClick(mc, screen);
            }
        } else if (clickQueueIndex >= clickQueue.size() && initialScanDone) {
            long timeSinceLastClick = currentTime - lastClickTime;

            if (clickQueue.isEmpty()) {
                if (!solved) {
                    solved = true;
                    TeslaMaps.LOGGER.info("[RubixTerminal] ===== ALL COLORS MATCHED =====");
                }
            } else if (timeSinceLastClick > 1000) {
                TeslaMaps.LOGGER.info("[RubixTerminal] Container still open after finishing queue - restarting scan!");
                clickQueue.clear();
                clickQueueIndex = 0;
                lastScanTime = 0; // Force immediate re-scan
            }
        }
    }

    private static void scanAndBuildClickQueue(ContainerScreen screen) {
        ChestMenu handler = screen.getMenu();

        int[] gridSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};

        Map<Integer, String> slotColors = new HashMap<>();
        for (int slot : gridSlots) {
            ItemStack stack = handler.getSlot(slot).getItem();
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

        String bestTarget = findOptimalTargetColor(slotColors);

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

        if (clickQueueIndex >= clickQueue.size()) {
            clickQueue.clear();
            clickQueueIndex = 0;

            for (Map.Entry<Integer, String> entry : slotColors.entrySet()) {
                int slot = entry.getKey();
                String currentColor = entry.getValue();

                if (currentColor.equals(bestTarget)) continue; // Already correct

                int forwardClicks = getDistanceForward(currentColor, bestTarget);
                int backwardClicks = getDistanceBackward(currentColor, bestTarget);

                if (forwardClicks <= backwardClicks) {
                    for (int i = 0; i < forwardClicks; i++) {
                        clickQueue.add(new ClickAction(slot, false, forwardClicks));
                    }
                    TeslaMaps.LOGGER.info("[RubixTerminal] Slot {}: {} -> {} (LEFT x{})", slot, currentColor, bestTarget, forwardClicks);
                } else {
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

    private static int getColorIndex(String color) {
        for (int i = 0; i < COLOR_CYCLE.length; i++) {
            if (COLOR_CYCLE[i].equals(color)) return i;
        }
        return -1;
    }

    private static String getPaneColor(ItemStack stack) {
        if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) return "RED";
        if (stack.getItem() == Items.ORANGE_STAINED_GLASS_PANE) return "ORANGE";
        if (stack.getItem() == Items.YELLOW_STAINED_GLASS_PANE) return "YELLOW";
        if (stack.getItem() == Items.GREEN_STAINED_GLASS_PANE) return "GREEN";
        if (stack.getItem() == Items.BLUE_STAINED_GLASS_PANE) return "BLUE";
        return null;
    }

    private static void performNextClick(Minecraft mc, ContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[RubixTerminal] Cannot click - player is null");
            return;
        }

        if (clickQueueIndex >= clickQueue.size()) {
            TeslaMaps.LOGGER.warn("[RubixTerminal] No more clicks in queue!");
            return;
        }

        ClickAction action = clickQueue.get(clickQueueIndex);
        ChestMenu handler = screen.getMenu();

        TeslaMaps.LOGGER.info("[RubixTerminal] Clicking slot {} ({} {}/{})",
            action.slot, action.rightClick ? "RIGHT" : "LEFT",
            clickQueueIndex + 1, clickQueue.size());

        mc.gameMode.handleContainerInput(
            handler.containerId,
            action.slot,
            action.rightClick ? 1 : 0,  // 0 = left, 1 = right
            ContainerInput.PICKUP,
            mc.player
        );

        clickQueueIndex++;
        lastClickTime = System.currentTimeMillis();
        isClicked = true;
    }

    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || clickQueueIndex >= clickQueue.size()) return -1;
        return clickQueue.get(clickQueueIndex).slot;
    }

    public static java.util.Map<Integer, Integer> getClickMap() {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        for (ClickAction action : clickQueue) {
            int clicks = action.rightClick ? -action.clickCount : action.clickCount;
            map.put(action.slot, clicks);
        }
        return map;
    }

    public static void markSlotClicked(int slot) {
        if (clickQueueIndex < clickQueue.size()) {
            ClickAction action = clickQueue.get(clickQueueIndex);
            if (action.slot == slot) {
                clickQueueIndex++;
                lastClickTime = System.currentTimeMillis();
                TeslaMaps.LOGGER.info("[RubixTerminal] Manually clicked slot {} - advancing queue ({}/{})",
                    slot, clickQueueIndex, clickQueue.size());
            }
        }
    }

    public static void onSlotUpdate(int slotIndex, ItemStack stack) {
        if (!TeslaMapsConfig.get().solveRubixTerminal) return;

        int[] gridSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
        boolean isGridSlot = false;
        for (int gs : gridSlots) {
            if (gs == slotIndex) {
                isGridSlot = true;
                break;
            }
        }
        if (!isGridSlot) return;

        isClicked = false;
        TeslaMaps.LOGGER.info("[RubixTerminal] Slot {} color changed, ready for next click", slotIndex);
    }

    public static boolean canClick(int slotIndex, int button) {
        if (!initialScanDone || solved || clickQueueIndex >= clickQueue.size()) return true;

        ClickAction nextAction = clickQueue.get(clickQueueIndex);

        if (slotIndex != nextAction.slot) {
            TeslaMaps.LOGGER.info("[RubixTerminal] Blocked click on slot {} - next should be {}", slotIndex, nextAction.slot);
            return false;
        }

        boolean shouldRightClick = nextAction.rightClick;
        boolean isRightClick = (button == 1);

        if (shouldRightClick != isRightClick) {
            TeslaMaps.LOGGER.info("[RubixTerminal] Blocked {} click on slot {} - should use {} click",
                isRightClick ? "right" : "left", slotIndex, shouldRightClick ? "right" : "left");
            return false;
        }

        return true;
    }

    public static void reset() {
        if (initialScanDone) {
            TeslaMaps.LOGGER.info("[RubixTerminal] Resetting");
        }
        clickQueue.clear();
        clickQueueIndex = 0;
        solved = false;
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
    }
}
