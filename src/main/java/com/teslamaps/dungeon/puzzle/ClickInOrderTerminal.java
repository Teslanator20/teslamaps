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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ClickInOrderTerminal {

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
            if (!slotToNumber.isEmpty()) {
            }
            reset();
            return;
        }

        ContainerScreen screen = (ContainerScreen) mc.screen;

        Component title = screen.getTitle();
        String titleStr = title.getString();

        int currentTick = mc.player.tickCount;
        if (currentTick - lastDebugTick > 20) {
            lastDebugTick = currentTick;
        }

        String cleanTitle = net.minecraft.ChatFormatting.stripFormatting(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (!cleanTitle.equals("Click in order!")) {
            if (!slotToNumber.isEmpty()) {
            }
            return;
        }

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

        if (currentTime - lastScanTime >= 100) {
            int prevSize = orderedSlots.size();
            findAllNumberedSlots(screen);
            lastScanTime = currentTime;
            if (orderedSlots.size() != prevSize) {
                isClicked = false;
            }
        }

        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!solved && !orderedSlots.isEmpty() && !usingClickAnywhere && !isClicked) {
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            int randomization = TeslaMapsConfig.get().terminalClickRandomization;
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

            if (nextNumberToClick == 1 && timeSinceOpen >= randomInitialDelay) {
                performNextClick(mc, screen);
            }
            else if (nextNumberToClick > 1 && timeSinceLastClick >= randomInterval) {
                performNextClick(mc, screen);
            }
        } else if (orderedSlots.isEmpty() && initialScanDone && nextNumberToClick > 1) {
            long timeSinceLastClick = currentTime - lastClickTime;

            if (!solved) {
                solved = true;
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== ALL PANES TURNED GREEN =====");
            }

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

    private static void findAllNumberedSlots(ContainerScreen screen) {
        ChestMenu handler = screen.getMenu();

        slotToNumber.clear();
        orderedSlots.clear();

        int redFound = 0;

        for (Slot slot : handler.slots) {
            if (slot.index >= 54) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
                int number = stack.getCount(); // Stack size = order number
                if (number >= 1 && number <= 14) {
                    slotToNumber.put(slot.index, number);
                    redFound++;
                }
            }
        }

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Found {} RED panes to click", redFound);

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

    private static void performNextClick(Minecraft mc, ContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[ClickInOrderTerminal] DEBUG: Cannot click - player is null");
            return;
        }

        if (orderedSlots.isEmpty()) {
            TeslaMaps.LOGGER.warn("[ClickInOrderTerminal] DEBUG: No red panes to click!");
            return;
        }

        int slotToClick = orderedSlots.get(0);
        int number = slotToNumber.get(slotToClick);
        ChestMenu handler = screen.getMenu();

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== PERFORMING AUTO-CLICK =====");
        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Clicking pane #{} at slot {}", number, slotToClick);

        mc.gameMode.handleContainerInput(
            handler.containerId,
            slotToClick,
            0,  // Left click
            ContainerInput.PICKUP,
            mc.player
        );

        nextNumberToClick = number + 1;
        lastClickTime = System.currentTimeMillis();
        isClicked = true; // Mark that we're waiting for click to register

        TeslaMaps.LOGGER.info("[ClickInOrderTerminal] ===== CLICK SENT =====");
    }

    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || orderedSlots.isEmpty()) return -1;
        return orderedSlots.get(0); // First red pane in order
    }

    public static void onSlotUpdate(int slotIndex, ItemStack stack) {
        if (!TeslaMapsConfig.get().solveClickInOrderTerminal) return;
        if (slotIndex >= 54) return; // Ignore player inventory

        if (slotToNumber.containsKey(slotIndex)) {
            if (stack.getItem() != Items.RED_STAINED_GLASS_PANE) {
                int number = slotToNumber.remove(slotIndex);
                orderedSlots.remove(Integer.valueOf(slotIndex));
                isClicked = false; // Ready for next click
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Slot {} (pane #{}) turned green!", slotIndex, number);
            }
        }
        else if (stack.getItem() == Items.RED_STAINED_GLASS_PANE) {
            int number = stack.getCount();
            if (number >= 1 && number <= 14) {
                slotToNumber.put(slotIndex, number);
                int insertIdx = 0;
                for (int i = 0; i < orderedSlots.size(); i++) {
                    if (slotToNumber.get(orderedSlots.get(i)) > number) {
                        break;
                    }
                    insertIdx = i + 1;
                }
                orderedSlots.add(insertIdx, slotIndex);
                TeslaMaps.LOGGER.info("[ClickInOrderTerminal] New red pane #{} at slot {}", number, slotIndex);
            }
        }
    }

    public static boolean canClick(int slotIndex, int button) {
        if (!initialScanDone || solved || orderedSlots.isEmpty()) return true;

        int nextSlot = orderedSlots.get(0);
        if (slotIndex != nextSlot) {
            TeslaMaps.LOGGER.info("[ClickInOrderTerminal] Blocked click on slot {} - next should be {}", slotIndex, nextSlot);
            return false;
        }
        return true;
    }

    public static void reset() {
        if (!slotToNumber.isEmpty()) {
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
