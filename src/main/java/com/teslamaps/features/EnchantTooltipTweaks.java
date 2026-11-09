package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

public final class EnchantTooltipTweaks {

    private EnchantTooltipTweaks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EnchantTooltipTweaks")
                .config("Tooltips & Value", b -> {
                    b.label("Enchant Tooltip Extras");
                    b.toggle("Hide Vanilla Enchant Lines", () -> cfg().hideVanillaEnchants, v -> cfg().hideVanillaEnchants = v)
                            .desc("On SkyBlock, drops every vanilla enchantment line - Hypixel lists them in the lore already.");
                });
    }
}
