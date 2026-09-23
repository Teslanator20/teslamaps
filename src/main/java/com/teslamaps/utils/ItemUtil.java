package com.teslamaps.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public class ItemUtil {
    public static String skyblockId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return ((com.teslamaps.injected.CustomizableStack) (Object) stack).teslamaps$skyblockId();
    }

    public static java.util.List<String> abilityScrolls(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return java.util.List.of();
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return java.util.List.of();
        net.minecraft.nbt.ListTag list = d.copyTag().getListOrEmpty("ability_scroll");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(list.getStringOr(i, ""));
        return out;
    }

    /** Hypixel's per-item uuid from CUSTOM_DATA, null if the item has none. */
    public static String itemUuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag().getString("uuid").orElse(null);
    }

    public static ItemLore lore(ItemStack stack) {
        return stack == null ? null : stack.get(DataComponents.LORE);
    }

    /** Lore lines, § codes stripped. Empty list if the item has no lore. */
    public static List<String> loreLines(ItemStack stack) {
        if (stack == null) return List.of();
        return ((com.teslamaps.injected.CustomizableStack) (Object) stack).teslamaps$loreLines();
    }

    /** Case-insensitive substring search over the stripped lore. */
    public static boolean loreContains(ItemStack stack, String needle) {
        String lower = needle.toLowerCase(Locale.ROOT);
        for (String line : loreLines(stack)) {
            if (line.toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }

    /** Hotbar index 0-8 whose item name contains the string, -1 if none. Feed to setSelectedSlot. */
    public static int hotbarSlotByName(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return findHotbar(stack -> StringUtil.stripTrim(stack.getHoverName().getString())
                .toLowerCase(Locale.ROOT).contains(needle));
    }

    /** Hotbar index 0-8 whose lore contains the string, -1 if none. */
    public static int hotbarSlotByLore(String lore) {
        return findHotbar(stack -> loreContains(stack, lore));
    }

    /** Slot index in the OPEN CONTAINER whose item name contains the string, -1 if none. */
    public static int containerSlotByName(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return findContainer(stack -> StringUtil.stripTrim(stack.getHoverName().getString())
                .toLowerCase(Locale.ROOT).contains(needle));
    }

    /** Slot index in the OPEN CONTAINER whose lore contains the string, -1 if none. */
    public static int containerSlotByLore(String lore) {
        return findContainer(stack -> loreContains(stack, lore));
    }

    /** Slot index in the OPEN CONTAINER holding the item with this Hypixel uuid, -1 if none. */
    public static int containerSlotByUuid(String uuid) {
        return findContainer(stack -> uuid.equals(itemUuid(stack)));
    }

    private static int findHotbar(Predicate<ItemStack> test) {
        var player = Minecraft.getInstance().player;
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && test.test(stack)) return i;
        }
        return -1;
    }

    private static int findContainer(Predicate<ItemStack> test) {
        var player = Minecraft.getInstance().player;
        if (player == null) return -1;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && test.test(stack)) return i;
        }
        return -1;
    }
}
