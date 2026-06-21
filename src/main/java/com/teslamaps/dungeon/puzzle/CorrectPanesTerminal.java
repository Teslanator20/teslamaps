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

public class CorrectPanesTerminal {
    private static List<Integer> incorrectSlots = new ArrayList<>();
    private static Set<Integer> clickedSlots = new HashSet<>();
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean initialScanDone = false;
    private static boolean isClicked = false;
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
            if (!incorrectSlots.isEmpty()) {
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

        if (!titleStr.equals("Correct all the panes!")) {
            if (!incorrectSlots.isEmpty()) {
            }
            return;
        }

        if (!initialScanDone) {
            solved = false;
            clickedSlots.clear();
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== TERMINAL DETECTED =====");
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastScanTime >= 100) {
            int prevSize = incorrectSlots.size();
            findIncorrectPanes(screen);
            lastScanTime = currentTime;
            if (incorrectSlots.size() != prevSize) {
                isClicked = false;
            }
        }

        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!solved && !incorrectSlots.isEmpty() && !usingClickAnywhere && !isClicked) {
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            int randomization = TeslaMapsConfig.get().terminalClickRandomization;
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

            if (clickedSlots.isEmpty() && timeSinceOpen >= randomInitialDelay) {
                performNextClick(mc, screen);
            }
            else if (!clickedSlots.isEmpty() && timeSinceLastClick >= randomInterval) {
                performNextClick(mc, screen);
            }
        } else if (incorrectSlots.isEmpty() && initialScanDone && clickedSlots.size() > 0) {
            long timeSinceLastClick = currentTime - lastClickTime;

            if (!solved) {
                solved = true;
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== ALL PANES CORRECTED =====");
            }

            if (timeSinceLastClick > 1000) {
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Container still open after 1s, restarting...");
                incorrectSlots.clear();
                clickedSlots.clear();
                solved = false;
                lastScanTime = 0; // Force immediate re-scan
            }
        }
    }

    private static void findIncorrectPanes(ContainerScreen screen) {
        ChestMenu handler = screen.getMenu();

        List<Integer> newIncorrectSlots = new ArrayList<>();

        for (Slot slot : handler.slots) {
            if (slot.index >= 54) continue;

            int column = slot.index % 9;
            if (column == 0 || column == 1 || column == 7 || column == 8) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.RED_STAINED_GLASS_PANE ||
                stack.getItem() == Items.ORANGE_STAINED_GLASS_PANE) {

                if (!clickedSlots.contains(slot.index)) {
                    newIncorrectSlots.add(slot.index);
                }
            }
        }

        if (!newIncorrectSlots.equals(incorrectSlots)) {
            incorrectSlots = newIncorrectSlots;
            if (!incorrectSlots.isEmpty()) {
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Found {} incorrect panes: {}",
                    incorrectSlots.size(), incorrectSlots);
            }
        }
    }

    private static void performNextClick(Minecraft mc, ContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[CorrectPanesTerminal] DEBUG: Cannot click - player is null");
            return;
        }

        if (incorrectSlots.isEmpty()) {
            TeslaMaps.LOGGER.warn("[CorrectPanesTerminal] DEBUG: No incorrect panes to click!");
            return;
        }

        int slotToClick = incorrectSlots.get(0);
        ChestMenu handler = screen.getMenu();

        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== PERFORMING AUTO-CLICK =====");
        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Clicking slot {} (clicked {} so far)",
            slotToClick, clickedSlots.size());

        mc.gameMode.handleContainerInput(
            handler.containerId,
            slotToClick,
            0,  // Left click
            ContainerInput.PICKUP,
            mc.player
        );

        clickedSlots.add(slotToClick);
        lastClickTime = System.currentTimeMillis();
        isClicked = true;

        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] ===== CLICK SENT =====");
        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Total clicks: {}", clickedSlots.size());
    }

    public static int getNextCorrectSlot() {
        if (!initialScanDone || solved || incorrectSlots.isEmpty()) return -1;
        return incorrectSlots.get(0); // First incorrect pane
    }

    public static void markSlotClicked(int slot) {
        clickedSlots.add(slot);
        lastClickTime = System.currentTimeMillis();
    }

    public static void onSlotUpdate(int slotIndex, net.minecraft.world.item.ItemStack stack) {
        if (!TeslaMapsConfig.get().solveCorrectPanesTerminal) return;
        if (slotIndex >= 54) return; // Ignore player inventory

        int column = slotIndex % 9;
        if (column == 0 || column == 1 || column == 7 || column == 8) return;

        if (incorrectSlots.contains(slotIndex)) {
            if (stack.getItem() != Items.RED_STAINED_GLASS_PANE &&
                stack.getItem() != Items.ORANGE_STAINED_GLASS_PANE) {
                incorrectSlots.remove(Integer.valueOf(slotIndex));
                isClicked = false;
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Slot {} corrected!", slotIndex);
            }
        }
        else if (stack.getItem() == Items.RED_STAINED_GLASS_PANE ||
                 stack.getItem() == Items.ORANGE_STAINED_GLASS_PANE) {
            if (!clickedSlots.contains(slotIndex)) {
                incorrectSlots.add(slotIndex);
                TeslaMaps.LOGGER.info("[CorrectPanesTerminal] New incorrect pane at slot {}", slotIndex);
            }
        }
    }

    public static boolean canClick(int slotIndex, int button) {
        if (!initialScanDone || solved) return true;

        if (incorrectSlots.contains(slotIndex)) {
            return true;
        }

        TeslaMaps.LOGGER.info("[CorrectPanesTerminal] Blocked click on slot {} - not an incorrect pane", slotIndex);
        return false;
    }

    public static void reset() {
        if (!incorrectSlots.isEmpty()) {
        }
        incorrectSlots.clear();
        clickedSlots.clear();
        solved = false;
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
        lastDebugTick = 0;
    }
}
