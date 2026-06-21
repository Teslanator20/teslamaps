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
import net.minecraft.world.item.ItemStack;

public class TerminalManager {

    public enum TerminalType {
        NONE,
        CLICK_IN_ORDER,
        CORRECT_PANES,
        STARTS_WITH,
        SELECT_ALL,
        RUBIX,
        MELODY
    }

    private static TerminalType currentTerminal = TerminalType.NONE;
    private static int currentSyncId = -1;
    private static long lastSlotUpdateTime = 0;

    public static void onSlotUpdate(int syncId, int slotIndex, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || !(mc.screen instanceof ContainerScreen)) {
            currentTerminal = TerminalType.NONE;
            return;
        }

        ContainerScreen screen = (ContainerScreen) mc.screen;
        int screenSyncId = screen.getMenu().containerId;

        if (syncId != screenSyncId) {
            return;
        }

        if (currentTerminal == TerminalType.NONE || currentSyncId != syncId) {
            currentTerminal = detectTerminalType(screen);
            currentSyncId = syncId;
            if (currentTerminal != TerminalType.NONE) {
                TeslaMaps.LOGGER.info("[TerminalManager] Detected terminal: {}", currentTerminal);
            }
        }

        lastSlotUpdateTime = System.currentTimeMillis();

        switch (currentTerminal) {
            case CLICK_IN_ORDER:
                ClickInOrderTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case CORRECT_PANES:
                CorrectPanesTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case STARTS_WITH:
                StartsWithTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case SELECT_ALL:
                SelectAllTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case RUBIX:
                RubixTerminal.onSlotUpdate(slotIndex, stack);
                break;
            case MELODY:
                MelodyTerminal.onSlotUpdate(slotIndex, stack);
                break;
            default:
                break;
        }
    }

    private static TerminalType detectTerminalType(ContainerScreen screen) {
        Component title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = ChatFormatting.stripFormatting(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (cleanTitle.equals("Click in order!")) {
            return TerminalType.CLICK_IN_ORDER;
        } else if (cleanTitle.equals("Correct all the panes!")) {
            return TerminalType.CORRECT_PANES;
        } else if (cleanTitle.matches("What starts with: '[A-Z]'\\?")) {
            return TerminalType.STARTS_WITH;
        } else if (cleanTitle.matches("Select all the [A-Z]+ items!")) {
            return TerminalType.SELECT_ALL;
        } else if (cleanTitle.equals("Change all to same color!")) {
            return TerminalType.RUBIX;
        } else if (cleanTitle.equals("Click the button on time!")) {
            return TerminalType.MELODY;
        }

        return TerminalType.NONE;
    }

    public static TerminalType getCurrentTerminal() {
        return currentTerminal;
    }

    public static boolean isInTerminal() {
        return currentTerminal != TerminalType.NONE;
    }

    public static long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastSlotUpdateTime;
    }

    public static void onScreenClose() {
        currentTerminal = TerminalType.NONE;
        currentSyncId = -1;
    }

    public static boolean canClick(int slotIndex, int button) {
        switch (currentTerminal) {
            case CLICK_IN_ORDER:
                return ClickInOrderTerminal.canClick(slotIndex, button);
            case CORRECT_PANES:
                return CorrectPanesTerminal.canClick(slotIndex, button);
            case STARTS_WITH:
                return StartsWithTerminal.canClick(slotIndex, button);
            case SELECT_ALL:
                return SelectAllTerminal.canClick(slotIndex, button);
            case RUBIX:
                return RubixTerminal.canClick(slotIndex, button);
            case MELODY:
                return MelodyTerminal.canClick(slotIndex, button);
            default:
                return true; // Allow all clicks if not in a terminal
        }
    }
}
