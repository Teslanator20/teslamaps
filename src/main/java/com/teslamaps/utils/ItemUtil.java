package com.teslamaps.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getString("id").orElse("");
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
        ItemLore lore = lore(stack);
        if (lore == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Component line : lore.lines()) out.add(StringUtil.strip(line.getString()));
        return out;
    }

    /** Case-insensitive substring search over the stripped lore. */
    public static boolean loreContains(ItemStack stack, String needle) {
        ItemLore lore = lore(stack);
        if (lore == null) return false;
        String lower = needle.toLowerCase(Locale.ROOT);
        for (Component line : lore.lines()) {
            if (StringUtil.strip(line.getString()).toLowerCase(Locale.ROOT).contains(lower)) return true;
        }
        return false;
    }

    /** Slot index in the open container whose item name contains the string, -1 if none. */
    public static int slotByName(String name) {
        return findSlot(stack -> StringUtil.strip(stack.getHoverName().getString())
                .toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)));
    }

    /** Slot index in the open container whose lore contains the string, -1 if none. */
    public static int slotByLore(String lore) {
        return findSlot(stack -> loreContains(stack, lore));
    }

    /** Slot index in the open container holding the item with this Hypixel uuid, -1 if none. */
    public static int slotByUuid(String uuid) {
        return findSlot(stack -> uuid.equals(itemUuid(stack)));
    }

    private static int findSlot(java.util.function.Predicate<ItemStack> test) {
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
