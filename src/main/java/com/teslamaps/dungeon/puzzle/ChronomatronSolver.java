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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ChronomatronSolver {

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

    private static Map<Integer, Boolean> previousGlintState = new HashMap<>();

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
                TeslaMaps.LOGGER.info("[Chronomatron] Screen closed, resetting");
            }
            reset();
            return;
        }

        Component title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = ChatFormatting.stripFormatting(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (!cleanTitle.matches("Chronomatron \\(\\w+\\)")) {
            return;
        }

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

            isSingleRow = cleanTitle.endsWith("(High)") || cleanTitle.endsWith("(Grand)") || cleanTitle.endsWith("(Supreme)");

            TeslaMaps.LOGGER.info("[Chronomatron] ===== DETECTED: {} (singleRow={}) =====", cleanTitle, isSingleRow);
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();
        ChestMenu handler = screen.getMenu();

        if (currentTime - lastScanTime >= 50) {
            processSlotChanges(handler);
            lastScanTime = currentTime;
        }

        boolean usingClickAnywhere = TeslaMapsConfig.get().customTerminalGui && TeslaMapsConfig.get().terminalClickAnywhere;

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

    private static void processSlotChanges(ChestMenu handler) {
        int minSlot = isSingleRow ? 17 : 10;
        int maxSlot = isSingleRow ? 25 : 34;

        ItemStack instructionStack = handler.getSlot(49).getItem();
        String instructionName = instructionStack.getHoverName().getString();

        switch (state) {
            case REMEMBER -> {
                for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
                    Slot slot = handler.getSlot(slotId);
                    if (slot == null) continue;

                    ItemStack stack = slot.getItem();
                    if (stack.isEmpty()) continue;

                    boolean hasGlint = stack.hasFoil();
                    Boolean prevGlint = previousGlintState.get(slotId);

                    if (hasGlint && (prevGlint == null || !prevGlint)) {
                        if (chronomatronCurrentSlot == 0) {
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
            }
        }
    }

    private static void performNextClick(Minecraft mc, ChestMenu handler) {
        if (mc.player == null || chronomatronCurrentOrdinal >= chronomatronSlots.size()) return;

        Item targetItem = chronomatronSlots.get(chronomatronCurrentOrdinal);

        int slotToClick = -1;
        int minSlot = isSingleRow ? 17 : 10;
        int maxSlot = isSingleRow ? 25 : 34;

        for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == targetItem) {
                slotToClick = slotId;
                break;
            }
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

        mc.gameMode.handleContainerInput(
            handler.containerId,
            slotToClick,
            0,
            ContainerInput.PICKUP,
            mc.player
        );

        chronomatronCurrentOrdinal++;
        lastClickTime = System.currentTimeMillis();
        isClicked = true;

        if (chronomatronCurrentOrdinal >= chronomatronSlots.size()) {
            TeslaMaps.LOGGER.info("[Chronomatron] Sequence complete, entering END state");
            state = State.END;
        }
    }

    public static int getNextCorrectSlot() {
        if (!initialScanDone || state != State.SHOW || chronomatronCurrentOrdinal >= chronomatronSlots.size()) {
            return -1;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return -1;

        ChestMenu handler = screen.getMenu();
        Item targetItem = chronomatronSlots.get(chronomatronCurrentOrdinal);

        int minSlot = isSingleRow ? 17 : 10;
        int maxSlot = isSingleRow ? 25 : 34;

        for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == targetItem) return slotId;
            Item glassEquiv = TERRACOTTA_TO_GLASS.get(stack.getItem());
            if (glassEquiv == targetItem) return slotId;
        }

        return -1;
    }

    public static void markSlotClicked(int slot) {
        if (state == State.SHOW && chronomatronCurrentOrdinal < chronomatronSlots.size()) {
            chronomatronCurrentOrdinal++;
            if (chronomatronCurrentOrdinal >= chronomatronSlots.size()) {
                state = State.END;
            }
        }
    }

    public static List<Item> getSequence() {
        return Collections.unmodifiableList(chronomatronSlots);
    }

    public static int getCurrentOrdinal() {
        return chronomatronCurrentOrdinal;
    }

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
