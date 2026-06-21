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
package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.puzzle.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;

public class TerminalGuiManager {
    private static CustomTermGui currentGui = null;
    private static String lastScreenTitle = "";

    public static boolean shouldRenderCustomGui() {
        if (!TeslaMapsConfig.get().customTerminalGui) return false;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen)) return false;

        return getTerminalType() != TerminalType.NONE;
    }

    public static void render(GuiGraphicsExtractor context) {
        if (!shouldRenderCustomGui()) return;

        TerminalType type = getTerminalType();
        if (type == TerminalType.NONE) return;

        if (currentGui == null || !lastScreenTitle.equals(getScreenTitle())) {
            currentGui = createGuiForType(type);
            lastScreenTitle = getScreenTitle();
        }

        if (currentGui != null) {
            currentGui.render(context);
        }
    }

    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!shouldRenderCustomGui()) return false;
        if (currentGui == null) return false;

        if (TeslaMapsConfig.get().terminalClickAnywhere) {
            TerminalType type = getTerminalType();
            int nextSlot = getNextCorrectSlot();

            com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] Click anywhere mode - Terminal: {}, Next slot: {}", type, nextSlot);

            if (nextSlot != -1) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ContainerScreen screen && mc.player != null) {
                    int buttonToUse = button;

                    if (type == TerminalType.RUBIX) {
                        java.util.Map<Integer, Integer> clickMap = RubixTerminal.getClickMap();
                        Integer clicksNeeded = clickMap.get(nextSlot);
                        if (clicksNeeded != null && clicksNeeded < 0) {
                            buttonToUse = 1; // Right-click for negative (backward) clicks
                        } else {
                            buttonToUse = 0; // Left-click for positive (forward) clicks
                        }
                    } else {
                        buttonToUse = 0;
                    }

                    com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] Clicking slot {} with button {}", nextSlot, buttonToUse);
                    mc.gameMode.handleContainerInput(
                        screen.getMenu().containerId,
                        nextSlot,
                        buttonToUse,
                        ContainerInput.PICKUP,
                        mc.player
                    );

                    markSlotAsClicked(type, nextSlot);
                    com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] Marked slot {} as clicked for type {}", nextSlot, type);
                }
            } else {
                com.teslamaps.TeslaMaps.LOGGER.info("[TerminalGUI] No next slot available - solver may not be initialized yet");
            }
            return true;
        }

        return currentGui.handleClick(mouseX, mouseY, button);
    }

    private static int getNextCorrectSlot() {
        TerminalType type = getTerminalType();
        switch (type) {
            case NUMBERS:
                return ClickInOrderTerminal.getNextCorrectSlot();
            case PANES:
                return CorrectPanesTerminal.getNextCorrectSlot();
            case STARTS_WITH:
                return StartsWithTerminal.getNextCorrectSlot();
            case SELECT_ALL:
                return SelectAllTerminal.getNextCorrectSlot();
            case RUBIX:
                return RubixTerminal.getNextCorrectSlot();
            case MELODY:
                return MelodyTerminal.getNextCorrectSlot();
            default:
                return -1;
        }
    }

    private static void markSlotAsClicked(TerminalType type, int slot) {
        switch (type) {
            case PANES:
                CorrectPanesTerminal.markSlotClicked(slot);
                break;
            case STARTS_WITH:
                StartsWithTerminal.markSlotClicked(slot);
                break;
            case SELECT_ALL:
                SelectAllTerminal.markSlotClicked(slot);
                break;
            case RUBIX:
                RubixTerminal.markSlotClicked(slot);
                break;
        }
    }

    public static void reset() {
        currentGui = null;
        lastScreenTitle = "";
    }

    private static TerminalType getTerminalType() {
        String title = getScreenTitle();
        if (title == null) return TerminalType.NONE;

        String cleanTitle = ChatFormatting.stripFormatting(title);
        if (cleanTitle == null) return TerminalType.NONE;

        if (cleanTitle.equals("Click in order!")) {
            return TerminalType.NUMBERS;
        } else if (cleanTitle.equals("Correct all the panes!")) {
            return TerminalType.PANES;
        } else if (cleanTitle.startsWith("What starts with:")) {
            return TerminalType.STARTS_WITH;
        } else if (cleanTitle.startsWith("Select all the")) {
            return TerminalType.SELECT_ALL;
        } else if (cleanTitle.equals("Change all to same color!")) {
            return TerminalType.RUBIX;
        } else if (cleanTitle.equals("Click the button on time!")) {
            return TerminalType.MELODY;
        }

        return TerminalType.NONE;
    }

    private static CustomTermGui createGuiForType(TerminalType type) {
        switch (type) {
            case NUMBERS:
                return new NumbersTermGui();
            case PANES:
                return new PanesTermGui();
            case STARTS_WITH:
                return new StartsWithTermGui();
            case SELECT_ALL:
                return new SelectAllTermGui();
            case RUBIX:
                return new RubixTermGui();
            case MELODY:
                return new MelodyTermGui();
            default:
                return null;
        }
    }

    private static String getScreenTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ContainerScreen screen) {
            Component title = screen.getTitle();
            return title != null ? title.getString() : null;
        }
        return null;
    }

    private enum TerminalType {
        NONE,
        NUMBERS,      // Click in order!
        PANES,        // Correct all the panes!
        STARTS_WITH,  // What starts with: '*'?
        SELECT_ALL,   // Select all the [color] items!
        RUBIX,        // Change all to same color!
        MELODY        // Click the button on time!
    }
}
