package com.teslamaps.dungeon.puzzle;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SelectAllTerminal {
    private static final Pattern PATTERN = Pattern.compile("Select all the ([A-Z]+) items!");

    private static String targetColor = null;
    private static List<Integer> correctSlots = new ArrayList<>();
    private static Set<Integer> clickedSlots = new HashSet<>();
    private static boolean solved = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static boolean slotsFound = false;
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
            if (targetColor != null) {
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

        Matcher matcher = PATTERN.matcher(titleStr);
        if (!matcher.find()) {
            if (targetColor != null) {
            }
            return;
        }

        String color = matcher.group(1);

        if (targetColor == null || !targetColor.equals(color)) {
            targetColor = color;
            solved = false;
            slotsFound = false;
            correctSlots.clear();
            clickedSlots.clear();
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            TeslaMaps.debug("[SelectAllTerminal] Target color: {}", targetColor);

            findAllCorrectSlots(screen);
            slotsFound = true;
        }

        boolean usingClickAnywhere = false;

        long currentTime = System.currentTimeMillis();

        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.debug("[SelectAllTerminal] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!solved && slotsFound && !correctSlots.isEmpty() && !usingClickAnywhere && !isClicked) {
            long timeSinceOpen = currentTime - terminalOpenTime;
            long timeSinceLastClick = currentTime - lastClickTime;

            int randomization = TeslaMapsConfig.get().terminalClickRandomization;
            int randomInitialDelay = TeslaMapsConfig.get().terminalClickDelay + ThreadLocalRandom.current().nextInt(randomization + 1);
            int randomInterval = TeslaMapsConfig.get().terminalClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

            if (clickedSlots.isEmpty() && timeSinceOpen >= randomInitialDelay) {
                performNextClick(mc, screen);
            }
            else if (!clickedSlots.isEmpty() && timeSinceLastClick >= randomInterval) {
                if (clickedSlots.size() < correctSlots.size()) {
                    performNextClick(mc, screen);
                } else {
                    if (!solved) {
                    }
                    solved = true;

                    if (timeSinceLastClick > 2000) {
                        TeslaMaps.debug("[SelectAllTerminal] Container still open after 2s, restarting...");
                        correctSlots.clear();
                        clickedSlots.clear();
                        solved = false;
                        slotsFound = false;
                        isClicked = false;
                        targetColor = null;
                    }
                }
            }
        }
    }

    private static void findAllCorrectSlots(ContainerScreen screen) {
        ChestMenu handler = screen.getMenu();

        int scannedItems = 0;

        for (int col = 0; col < 9; col++) {
            for (int row = 0; row < 6; row++) {
                int slotId = row * 9 + col;
                if (slotId >= handler.slots.size()) continue;

                Slot slot = handler.slots.get(slotId);

                if (slot.index >= 54) continue;

                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;

                scannedItems++;

                String itemName = stack.getHoverName().getString();

                String strippedName = ChatFormatting.stripFormatting(itemName);
                if (strippedName == null || strippedName.isEmpty()) {
                    continue;
                }

                if (matchesColor(strippedName, targetColor)) {
                    correctSlots.add(slot.index);
                    TeslaMaps.debug("[SelectAllTerminal] Item: '{}'", strippedName);
                    TeslaMaps.debug("[SelectAllTerminal] Slot: {}", slot.index);
                }
            }
        }

        if (correctSlots.isEmpty()) {
            TeslaMaps.LOGGER.warn("[SelectAllTerminal] Scanned {} items, none matched '{}'", scannedItems, targetColor);
        } else {
            TeslaMaps.debug("[SelectAllTerminal] Found {} items with color '{}'", correctSlots.size(), targetColor);
            TeslaMaps.debug("[SelectAllTerminal] Slots: {}", correctSlots);
        }
    }

    private static boolean matchesColor(String itemName, String color) {
        String upperName = itemName.toUpperCase();
        String upperColor = color.toUpperCase();

        if (upperColor.equals("BLUE")) {
            if (upperName.contains("LAPIS")) return true;
            if (upperName.startsWith("LIGHT BLUE")) return false;
            if (upperName.startsWith("BLUE")) return true;
        } else if (upperColor.equals("BLACK")) {
            if (upperName.contains("INK")) return true;
            if (upperName.startsWith("BLACK")) return true;
        } else if (upperColor.equals("WHITE")) {
            if (upperName.contains("BONE")) return true;
            if (upperName.startsWith("WHITE")) return true;
        } else if (upperColor.equals("SILVER")) {
            if (upperName.startsWith("LIGHT GRAY")) return true;
            if (upperName.startsWith("SILVER")) return true;
        } else if (upperColor.equals("YELLOW")) {
            if (upperName.contains("DANDELION")) return true;
            if (upperName.startsWith("YELLOW")) return true;
        } else if (upperColor.equals("RED")) {
            if (upperName.contains("ROSE") && !upperName.contains("QUARTZ")) return true;
            if (upperName.startsWith("RED")) return true;
        } else if (upperColor.equals("GREEN")) {
            if (upperName.contains("CACTUS")) return true;
            if (upperName.startsWith("LIME")) return false; // Lime is NOT green
            if (upperName.startsWith("GREEN")) return true;
        } else if (upperColor.equals("BROWN")) {
            if (upperName.contains("COCOA")) return true;
            if (upperName.startsWith("BROWN")) return true;
        } else if (upperColor.equals("LIGHT BLUE")) {
            if (upperName.startsWith("LIGHT BLUE")) return true;
        } else {
            if (upperName.startsWith(upperColor + " ")) return true;
            if (upperName.equals(upperColor)) return true;
        }

        return false;
    }

    private static void performNextClick(Minecraft mc, ContainerScreen screen) {
        if (mc.player == null) {
            TeslaMaps.LOGGER.warn("[SelectAllTerminal] DEBUG: Cannot click - player is null");
            return;
        }

        int slotToClick = -1;
        for (int slot : correctSlots) {
            if (!clickedSlots.contains(slot)) {
                slotToClick = slot;
                break;
            }
        }

        if (slotToClick == -1) {
            TeslaMaps.LOGGER.warn("[SelectAllTerminal] DEBUG: No unclicked slots found!");
            return;
        }

        ChestMenu handler = screen.getMenu();

        TeslaMaps.debug("[SelectAllTerminal] Clicking slot {} ({}/{})", slotToClick, clickedSlots.size() + 1, correctSlots.size());

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

        TeslaMaps.debug("[SelectAllTerminal] Progress: {}/{} items clicked", clickedSlots.size(), correctSlots.size());
    }

    public static int getNextCorrectSlot() {
        if (!slotsFound || solved || correctSlots.isEmpty()) return -1;

        for (int slot : correctSlots) {
            if (!clickedSlots.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public static void markSlotClicked(int slot) {
        clickedSlots.add(slot);
        lastClickTime = System.currentTimeMillis();
    }

    public static List<Integer> getCorrectSlots() {
        return new ArrayList<>(correctSlots);
    }

    public static String getTargetColor() {
        return targetColor;
    }

    public static Set<Integer> getClickedSlots() {
        return new HashSet<>(clickedSlots);
    }

    public static void onSlotUpdate(int slotIndex, net.minecraft.world.item.ItemStack stack) {
        if (!TeslaMapsConfig.get().solveSelectAllTerminal) return;
        if (slotIndex >= 54) return; // Ignore player inventory

        if (clickedSlots.contains(slotIndex)) {
            if (stack.hasFoil()) {
                isClicked = false; // Click was registered
                TeslaMaps.debug("[SelectAllTerminal] Slot {} click confirmed (has glint)", slotIndex);
            }
        }
    }

    public static boolean canClick(int slotIndex, int button) {
        if (!slotsFound || solved) return true;

        if (correctSlots.contains(slotIndex) && !clickedSlots.contains(slotIndex)) {
            return true;
        }

        if (correctSlots.contains(slotIndex)) {
            TeslaMaps.debug("[SelectAllTerminal] Blocked click on slot {} - already clicked", slotIndex);
        } else {
            TeslaMaps.debug("[SelectAllTerminal] Blocked click on slot {} - not a matching color", slotIndex);
        }
        return false;
    }

    public static void reset() {
        if (targetColor != null) {
        }
        targetColor = null;
        correctSlots.clear();
        clickedSlots.clear();
        solved = false;
        slotsFound = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastDebugTick = 0;
    }
}
