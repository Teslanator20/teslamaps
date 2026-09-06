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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SuperpairsSolver {

    private static final Map<Integer, ItemStack> revealedItems = new HashMap<>();
    private static final Set<Integer> matchedSlots = new HashSet<>();
    private static int lastClickedSlot = -1;
    private static ItemStack lastClickedItem = ItemStack.EMPTY;

    private static boolean initialScanDone = false;
    private static long terminalOpenTime = 0;
    private static long lastClickTime = 0;
    private static long lastScanTime = 0;
    private static boolean isClicked = false;

    private static int minSlot = 9;
    private static int maxSlot = 44;

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
                TeslaMaps.debug("[Superpairs] Screen closed, resetting");
            }
            reset();
            return;
        }

        Component title = screen.getTitle();
        String titleStr = title.getString();
        String cleanTitle = ChatFormatting.stripFormatting(titleStr);
        if (cleanTitle == null) cleanTitle = titleStr;

        if (!cleanTitle.matches("Superpairs \\(\\w+\\)")) {
            return;
        }

        if (!initialScanDone) {
            revealedItems.clear();
            matchedSlots.clear();
            lastClickedSlot = -1;
            lastClickedItem = ItemStack.EMPTY;
            terminalOpenTime = System.currentTimeMillis();
            lastClickTime = 0;
            lastScanTime = 0;
            isClicked = false;

            TeslaMaps.debug("[Superpairs] ===== DETECTED: {} =====", cleanTitle);
            initialScanDone = true;
        }

        long currentTime = System.currentTimeMillis();
        ChestMenu handler = screen.getMenu();

        if (currentTime - lastScanTime >= 100) {
            scanRevealedItems(handler);
            lastScanTime = currentTime;
        }

        boolean usingClickAnywhere = false;

        int breakThreshold = TeslaMapsConfig.get().terminalBreakThreshold;
        if (breakThreshold > 0 && isClicked && currentTime - lastClickTime > breakThreshold) {
            TeslaMaps.debug("[Superpairs] Break threshold reached, resetting click state");
            isClicked = false;
        }

        if (!usingClickAnywhere && !isClicked) {
            int nextSlot = findNextSlotToClick(handler);
            if (nextSlot != -1) {
                long timeSinceLastClick = currentTime - lastClickTime;

                int randomization = TeslaMapsConfig.get().terminalClickRandomization;
                int randomInterval = TeslaMapsConfig.get().experimentClickInterval + ThreadLocalRandom.current().nextInt(randomization + 1);

                if (timeSinceLastClick >= randomInterval) {
                    performClick(mc, handler, nextSlot);
                }
            }
        }
    }

    private static void scanRevealedItems(ChestMenu handler) {
        for (int slotId = minSlot; slotId <= maxSlot; slotId++) {
            if (matchedSlots.contains(slotId)) continue;

            Slot slot = handler.getSlot(slotId);
            if (slot == null) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.CYAN_STAINED_GLASS ||
                stack.getItem() == Items.BLACK_STAINED_GLASS_PANE ||
                stack.getItem() == Items.AIR) {
                continue;
            }

            if (!revealedItems.containsKey(slotId)) {
                revealedItems.put(slotId, stack.copy());
                TeslaMaps.debug("[Superpairs] Revealed item at slot {}: {}", slotId, stack.getHoverName().getString());

                for (Map.Entry<Integer, ItemStack> entry : revealedItems.entrySet()) {
                    int otherSlot = entry.getKey();
                    if (otherSlot == slotId) continue;
                    if (matchedSlots.contains(otherSlot)) continue;

                    if (ItemStack.isSameItem(stack, entry.getValue())) {
                        TeslaMaps.debug("[Superpairs] Found match: slots {} and {}", slotId, otherSlot);
                        matchedSlots.add(slotId);
                        matchedSlots.add(otherSlot);
                        break;
                    }
                }
            }
        }
    }

    private static int findNextSlotToClick(ChestMenu handler) {
        if (lastClickedSlot != -1 && !lastClickedItem.isEmpty()) {
            for (Map.Entry<Integer, ItemStack> entry : revealedItems.entrySet()) {
                int slot = entry.getKey();
                if (slot == lastClickedSlot) continue;
                if (matchedSlots.contains(slot)) continue;

                if (ItemStack.isSameItem(lastClickedItem, entry.getValue())) {
                    return slot;
                }
            }
        }

        for (Map.Entry<Integer, ItemStack> entry1 : revealedItems.entrySet()) {
            int slot1 = entry1.getKey();
            if (matchedSlots.contains(slot1)) continue;

            Slot gameSlot1 = handler.getSlot(slot1);
            if (gameSlot1 == null) continue;
            ItemStack currentStack1 = gameSlot1.getItem();

            if (!isHiddenItem(currentStack1)) continue;

            for (Map.Entry<Integer, ItemStack> entry2 : revealedItems.entrySet()) {
                int slot2 = entry2.getKey();
                if (slot2 <= slot1) continue; // Avoid duplicates
                if (matchedSlots.contains(slot2)) continue;

                if (ItemStack.isSameItem(entry1.getValue(), entry2.getValue())) {
                    return slot1;
                }
            }
        }

        return -1;
    }

    private static boolean isHiddenItem(ItemStack stack) {
        return stack.isEmpty() ||
               stack.getItem() == Items.CYAN_STAINED_GLASS ||
               stack.getItem() == Items.BLACK_STAINED_GLASS_PANE;
    }

    private static void performClick(Minecraft mc, ChestMenu handler, int slotId) {
        if (mc.player == null) return;

        TeslaMaps.debug("[Superpairs] Clicking slot {}", slotId);

        mc.gameMode.handleContainerInput(
            handler.containerId,
            slotId,
            0,
            ContainerInput.PICKUP,
            mc.player
        );

        lastClickedSlot = slotId;
        ItemStack knownItem = revealedItems.get(slotId);
        lastClickedItem = knownItem != null ? knownItem.copy() : ItemStack.EMPTY;

        lastClickTime = System.currentTimeMillis();
        isClicked = true;
    }

    public static int getNextCorrectSlot() {
        if (!initialScanDone) return -1;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen)) return -1;

        return findNextSlotToClick(screen.getMenu());
    }

    public static void markSlotClicked(int slot) {
        lastClickedSlot = slot;
        ItemStack knownItem = revealedItems.get(slot);
        lastClickedItem = knownItem != null ? knownItem.copy() : ItemStack.EMPTY;
    }

    public static Map<Integer, ItemStack> getRevealedItems() {
        return Collections.unmodifiableMap(revealedItems);
    }

    public static Set<Integer> getMatchedSlots() {
        return Collections.unmodifiableSet(matchedSlots);
    }

    public static boolean isActive() {
        return initialScanDone;
    }

    public static void reset() {
        if (initialScanDone) {
            TeslaMaps.debug("[Superpairs] Resetting");
        }
        revealedItems.clear();
        matchedSlots.clear();
        lastClickedSlot = -1;
        lastClickedItem = ItemStack.EMPTY;
        initialScanDone = false;
        isClicked = false;
        terminalOpenTime = 0;
        lastClickTime = 0;
        lastScanTime = 0;
    }
}
