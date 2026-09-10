package com.teslamaps.features;

import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

// Ported from SkyHanni's PersonalCompactorOverlay.kt
public class CompactorPreview {

    private static int slotsForTier(int tier) {
        return switch (tier) {
            case 7000 -> 12;
            case 6000 -> 7;
            case 5000 -> 3;
            case 4000 -> 1;
            default -> 0;
        };
    }

    public static List<String> lines(ItemStack stack) {
        String id = ItemUtil.skyblockId(stack);
        String prefix;
        if (id.startsWith("PERSONAL_COMPACTOR_")) prefix = "personal_compact_";
        else if (id.startsWith("PERSONAL_DELETOR_")) prefix = "personal_deletor_";
        else return List.of();

        int tier;
        try {
            tier = Integer.parseInt(id.substring(id.lastIndexOf('_') + 1));
        } catch (NumberFormatException e) {
            return List.of();
        }
        int slots = slotsForTier(tier);
        if (slots == 0) return List.of();

        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return List.of();
        CompoundTag tag = d.copyTag();

        List<String> out = new java.util.ArrayList<>(slots + 2);
        boolean enabled = tag.getByteOr("PERSONAL_DELETOR_ACTIVE", (byte) 0) == 1;
        out.add("§7Status: " + (enabled ? "§aEnabled" : "§cDisabled"));
        for (int i = 0; i < slots; i++) {
            String itemId = tag.getStringOr(prefix + i, "");
            if (itemId.isEmpty()) {
                out.add(" §8- §7Empty");
                continue;
            }
            String name = SkyblockItems.coloredName(itemId);
            if (name == null) name = "§f" + pretty(itemId);
            out.add(" §8- " + name);
        }
        return out;
    }

    private static String pretty(String id) {
        String[] parts = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
