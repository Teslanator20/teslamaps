package com.teslamaps.utils;

import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Tracks the open container from its packets: fully opened once the content packet (or every
 * menu slot) arrived, version bumps on every later slot change. Main thread only (TAIL hooks).
 * Ported from SkyHanni's data/OtherInventoryData.
 */
public final class ContainerState {

    private static final Map<MenuType<?>, Integer> SLOT_COUNTS = Map.ofEntries(
            Map.entry(MenuType.ANVIL, 3), Map.entry(MenuType.BEACON, 1), Map.entry(MenuType.BLAST_FURNACE, 3),
            Map.entry(MenuType.BREWING_STAND, 5), Map.entry(MenuType.CARTOGRAPHY_TABLE, 2),
            Map.entry(MenuType.CRAFTING, 9), Map.entry(MenuType.CRAFTER_3x3, 9), Map.entry(MenuType.ENCHANTMENT, 2),
            Map.entry(MenuType.FURNACE, 3), Map.entry(MenuType.GENERIC_3x3, 9), Map.entry(MenuType.GENERIC_9x1, 9),
            Map.entry(MenuType.GENERIC_9x2, 18), Map.entry(MenuType.GENERIC_9x3, 27), Map.entry(MenuType.GENERIC_9x4, 36),
            Map.entry(MenuType.GENERIC_9x5, 45), Map.entry(MenuType.GENERIC_9x6, 54), Map.entry(MenuType.GRINDSTONE, 3),
            Map.entry(MenuType.HOPPER, 5), Map.entry(MenuType.LECTERN, 1), Map.entry(MenuType.LOOM, 3),
            Map.entry(MenuType.MERCHANT, 3), Map.entry(MenuType.SHULKER_BOX, 27), Map.entry(MenuType.SMITHING, 3),
            Map.entry(MenuType.SMOKER, 3), Map.entry(MenuType.STONECUTTER, 1));

    private static int windowId = -1;
    private static int slotCount;
    private static final BitSet filled = new BitSet();
    private static boolean accept;
    private static boolean fullyOpened;
    private static int version;

    private ContainerState() {}

    public static void onOpen(ClientboundOpenScreenPacket packet) {
        Integer count = SLOT_COUNTS.get(packet.getType());
        windowId = count == null ? -1 : packet.getContainerId();
        slotCount = count == null ? 0 : count;
        filled.clear();
        accept = count != null;
        fullyOpened = false;
        version++;
    }

    public static void onContent(ClientboundContainerSetContentPacket packet) {
        if (windowId < 0 || packet.containerId() != windowId) return;
        List<ItemStack> items = packet.items();
        int n = Math.min(items.size(), slotCount);
        for (int i = 0; i < n; i++) if (!items.get(i).isEmpty()) filled.set(i);
        if (accept) done();
        else version++;
    }

    public static void onSetSlot(ClientboundContainerSetSlotPacket packet) {
        if (windowId < 0 || packet.getContainerId() != windowId) return;
        int slot = packet.getSlot();
        if (!accept) {
            if (slot >= 0 && slot < slotCount) version++;
            return;
        }
        if (slot >= slotCount) {
            done();
            return;
        }
        if (slot >= 0 && !packet.getItem().isEmpty()) filled.set(slot);
        if (filled.cardinality() == slotCount) done();
    }

    public static void onClose() {
        windowId = -1;
        accept = false;
        fullyOpened = false;
    }

    private static void done() {
        fullyOpened = true;
        accept = false;
        version++;
    }

    /** True once every slot of this container arrived. False for untracked menu types. */
    public static boolean fullyOpened(int containerId) {
        return fullyOpened && containerId == windowId;
    }

    /** Changes on open, full open and every later menu slot update. */
    public static int version() {
        return version;
    }
}
