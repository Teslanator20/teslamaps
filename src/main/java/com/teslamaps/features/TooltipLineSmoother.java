package com.teslamaps.features;

// Ported from Skyblocker's skyblock/item/tooltip/adders/LineSmoothener.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Hypixel's dashed "-----" separators become one solid strikethrough line. */
public final class TooltipLineSmoother {

    private static Feature feature;

    private TooltipLineSmoother() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("TooltipLineSmoother").when(() -> cfg().smoothTooltipLines)
                .config("Tooltips & Value", b -> b.toggle("Smooth Tooltip Lines",
                        () -> cfg().smoothTooltipLines, v -> cfg().smoothTooltipLines = v)
                        .desc("Replaces the dashed ----- separator lines with a solid line"));
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (cfg().smoothTooltipLines && feature.active()) modify(lines);
        });
    }

    private static void modify(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (isDashes(lines.get(i).getString())) {
                lines.set(i, Component.literal("                    ")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH, ChatFormatting.BOLD));
            }
        }
    }

    private static boolean isDashes(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§') { i++; continue; }
            if (ch != '-') return false;
            n++;
        }
        return n >= 10;
    }
}
