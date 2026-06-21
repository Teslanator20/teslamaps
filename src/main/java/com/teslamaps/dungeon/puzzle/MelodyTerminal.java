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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MelodyTerminal {
    private static final int[] TERRACOTTA_SLOTS = {16, 25, 34, 43};

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
                TeslaMaps.LOGGER.info("[MelodyTerminal] Screen closed, resetting");
            }
            reset();
            return;
        }

        ContainerScreen screen = (ContainerScreen) mc.screen;

        Component title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = ChatFormatting.stripFormatting(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        int currentTick = mc.player.tickCount;
        if (currentTick - lastDebugTick > 20) {
            TeslaMaps.LOGGER.info("[MelodyTerminal] Container title: '{}'", cleanTitle);
            lastDebugTick = currentTick;
        }

        if (!cleanTitle.equals("Click the button on time!")) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[MelodyTerminal] Title doesn't match, resetting");
            }
            return;
        }

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

        long timeSinceProgression = currentTime - laneProgressTime;
        if (currentTime - lastScanTime >= 100 && (laneProgressTime == 0 || timeSinceProgression >= 300)) {
            scanLane(screen);
            lastScanTime = currentTime;
        }

        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        if (!solved && lastGreenPosition != -1 && purplePosition != -1 && lastGreenPosition == purplePosition && !usingClickAnywhere) {
            long timeSinceLastClick = currentTime - lastClickTime;

            int melodyDelay = TeslaMapsConfig.get().melodyTerminalClickDelay;
            if (timeSinceLastClick > melodyDelay) {
                clickTerracotta(mc, screen, currentLane);
                lastClickTime = currentTime;

            }
        }

        if (currentLane < 4) { // Only check if we haven't finished all lanes
            ChestMenu handler = screen.getMenu();
            ItemStack currentTerracotta = handler.getSlot(TERRACOTTA_SLOTS[currentLane]).getItem();

            if (!currentTerracotta.isEmpty() && currentTerracotta.getItem() != Items.LIME_TERRACOTTA) {
                currentLane++;
                laneProgressTime = currentTime; // Mark when we progressed
                TeslaMaps.LOGGER.info("[MelodyTerminal] Lane progressed! Now on lane {} (waiting 300ms for magenta update)", currentLane + 1);

                if (currentLane >= 4) {
                    solved = true;
                    TeslaMaps.LOGGER.info("[MelodyTerminal] ===== ALL LANES COMPLETE =====");
                }

                lastGreenPosition = -1;
                purplePosition = -1;
            }
        }
    }

    private static void scanLane(ContainerScreen screen) {
        if (currentLane >= 4) return; // All lanes done

        ChestMenu handler = screen.getMenu();
        int[] laneSlots = LANE_SLOTS[currentLane];

        lastGreenPosition = -1;
        purplePosition = -1;

        for (int topSlot = 1; topSlot <= 7; topSlot++) {
            ItemStack stack = handler.getSlot(topSlot).getItem();

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

        TeslaMaps.LOGGER.info("[MelodyTerminal] Scanning lane {} (slots {}-{})", currentLane + 1, laneSlots[0], laneSlots[6]);
        for (int i = 0; i < 7; i++) { // Only scan positions 0-6, skip 7 (terracotta) and 8 (edge)
            int slot = laneSlots[i];
            ItemStack stack = handler.getSlot(slot).getItem();

            if (!stack.isEmpty()) {
                TeslaMaps.LOGGER.info("[MelodyTerminal] Lane {} slot {} (pos {}): {}", currentLane + 1, slot, i, stack.getItem().toString());
            }

            if (stack.isEmpty()) continue;

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

    private static void clickTerracotta(Minecraft mc, ContainerScreen screen, int lane) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[MelodyTerminal] Cannot click - player is null");
            return;
        }

        int slotToClick = TERRACOTTA_SLOTS[lane];
        ChestMenu handler = screen.getMenu();

        TeslaMaps.LOGGER.info("[MelodyTerminal] ===== CLICKING TERRACOTTA =====");
        TeslaMaps.LOGGER.info("[MelodyTerminal] Lane {} - Clicking slot {}", lane + 1, slotToClick);

        mc.gameMode.handleContainerInput(
            handler.containerId,
            slotToClick,
            0,  // Left click
            ContainerInput.PICKUP,
            mc.player
        );

        lastGreenPosition = -1;
        purplePosition = -1;
    }

    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || currentLane >= 4) return -1;

        if (lastGreenPosition != -1 && purplePosition != -1 && lastGreenPosition == purplePosition) {
            return TERRACOTTA_SLOTS[currentLane];
        }

        return -1;
    }

    public static int getCurrentLane() {
        return currentLane;
    }

    public static int getLastGreenPosition() {
        return lastGreenPosition;
    }

    public static int getPurplePosition() {
        return purplePosition;
    }

    public static void onSlotUpdate(int slotIndex, net.minecraft.world.item.ItemStack stack) {
        if (!TeslaMapsConfig.get().solveMelodyTerminal) return;

        for (int i = 0; i < TERRACOTTA_SLOTS.length; i++) {
            if (slotIndex == TERRACOTTA_SLOTS[i]) {
                if (stack.getItem() != Items.LIME_TERRACOTTA && i == currentLane) {
                    currentLane++;
                    laneProgressTime = System.currentTimeMillis();
                    lastGreenPosition = -1;
                    purplePosition = -1;
                    TeslaMaps.LOGGER.info("[MelodyTerminal] Lane {} completed via event!", i + 1);

                    if (currentLane >= 4) {
                        solved = true;
                        TeslaMaps.LOGGER.info("[MelodyTerminal] All lanes complete!");
                    }
                }
                break;
            }
        }
    }

    public static boolean canClick(int slotIndex, int button) {
        if (!initialScanDone || solved) return true;

        if (currentLane < 4 && slotIndex == TERRACOTTA_SLOTS[currentLane]) {
            return true;
        }

        TeslaMaps.LOGGER.info("[MelodyTerminal] Blocked click on slot {} - should click terracotta at {}",
            slotIndex, currentLane < 4 ? TERRACOTTA_SLOTS[currentLane] : "N/A");
        return false;
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
