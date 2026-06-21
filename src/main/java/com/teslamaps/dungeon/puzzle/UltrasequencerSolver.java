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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

public class UltrasequencerSolver {

    private static final int MIN_PANE_SLOT = 9;
    private static final int MAX_PANE_SLOT = 44;

    private enum State { REMEMBER, WAIT, SHOW, END }

    private static State state = State.REMEMBER;
    private static final Map<Integer, Integer> numberedSlots = new HashMap<>();
    private static int nextNumber = 1;
    private static DyeColor lastPaneColor = null;

    private static boolean initialScanDone = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
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

        if (!(mc.screen instanceof ContainerScreen screen)) {
            if (initialScanDone) {
                TeslaMaps.LOGGER.info("[Ultrasequencer] Screen closed, resetting");
            }
            reset();
            return;
        }

        Component title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = ChatFormatting.stripFormatting(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (!cleanTitle.matches("Ultrasequencer \\(\\w+\\)")) {
            return;
        }

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
        ChestMenu handler = screen.getMenu();

        processStateMachine(handler);

        checkPaneColorChange(handler);

        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

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

    private static void processStateMachine(ChestMenu handler) {
        ItemStack instructionStack = handler.getSlot(49).getItem();
        String instructionName = instructionStack.getHoverName().getString();

        switch (state) {
            case REMEMBER -> {
                if (instructionName.equals("Remember the pattern!")) {
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
            }
        }
    }

    private static void scanNumberedItems(ChestMenu handler) {
        numberedSlots.clear();

        for (int slotId = MIN_PANE_SLOT; slotId <= MAX_PANE_SLOT; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.BLACK_STAINED_GLASS_PANE) continue;

            String name = stack.getHoverName().getString();
            if (name.matches("\\d+")) {
                int number = Integer.parseInt(name);
                if (number >= 1 && number <= 14) {
                    numberedSlots.put(slotId, number);
                    TeslaMaps.LOGGER.info("[Ultrasequencer] Found item {} at slot {}", number, slotId);
                }
            }
        }
    }

    private static void checkPaneColorChange(ChestMenu handler) {
        for (int slotId = MIN_PANE_SLOT; slotId <= MAX_PANE_SLOT; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (block instanceof StainedGlassPaneBlock glassPaneBlock) {
                    DyeColor color = glassPaneBlock.getColor();

                    if (color == DyeColor.BLACK) continue;

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

    private static int findSlotForNumber(int number) {
        for (Map.Entry<Integer, Integer> entry : numberedSlots.entrySet()) {
            if (entry.getValue() == number) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private static void performClick(Minecraft mc, ChestMenu handler, int slotId) {
        if (mc.player == null) return;

        TeslaMaps.LOGGER.info("[Ultrasequencer] Clicking slot {} for number {}", slotId, nextNumber);

        mc.gameMode.handleContainerInput(
            handler.containerId,
            slotId,
            0,
            ContainerInput.PICKUP,
            mc.player
        );

        nextNumber++;
        lastClickTime = System.currentTimeMillis();
        isClicked = true;

        if (nextNumber > numberedSlots.size()) {
            TeslaMaps.LOGGER.info("[Ultrasequencer] Sequence complete, entering END state");
            state = State.END;
        }
    }

    public static int getNextCorrectSlot() {
        if (!initialScanDone || state != State.SHOW) return -1;
        return findSlotForNumber(nextNumber);
    }

    public static void markSlotClicked(int slot) {
        Integer number = numberedSlots.get(slot);
        if (number != null && number == nextNumber) {
            nextNumber++;
            if (nextNumber > numberedSlots.size()) {
                state = State.END;
            }
        }
    }

    public static Map<Integer, Integer> getNumberedSlots() {
        return Collections.unmodifiableMap(numberedSlots);
    }

    public static int getNextNumber() {
        return nextNumber;
    }

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
