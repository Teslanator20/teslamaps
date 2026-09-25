package com.teslamaps.features;

// Skyblocker's ItemStackMixin#skyblockIdTooltip / WpcMod's SkyBlockID
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class SkyblockIdTooltip {

    private SkyblockIdTooltip() {}

    public static void register() {
        Feature feature = Feature.of("SkyblockIdTooltip").when(() -> TeslaMapsConfig.get().skyblockIdTooltip);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!TeslaMapsConfig.get().skyblockIdTooltip || !feature.active()) return;
            if (TeslaMapsConfig.get().skyblockIdTooltipAdvanced && !flag.isAdvanced()) return;
            String id = ItemUtil.skyblockId(stack);
            if (id.isEmpty()) return;
            Component line = Component.literal("skyblock:" + id).withStyle(ChatFormatting.DARK_GRAY);
            lines.add(vanillaIdIndex(lines, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()), line);
        });
    }

    // below vanilla's F3+H id line, else last
    private static int vanillaIdIndex(List<Component> lines, String vanillaId) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).getString().equals(vanillaId)) return i + 1;
        }
        return lines.size();
    }
}
