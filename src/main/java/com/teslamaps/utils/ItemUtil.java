package com.teslamaps.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

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
}
