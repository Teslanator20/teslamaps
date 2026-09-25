package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class CroesusCategory {

    private CroesusCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.toggle("Croesus Profit Overlay", () -> config.croesusProfitOverlay, v -> config.croesusProfitOverlay = v);
        b.toggle("  Highlight Most Profitable", () -> config.croesusHighlightBest, v -> config.croesusHighlightBest = v);
        b.toggle("  Gray Out Other Chests", () -> config.croesusDimOthers, v -> config.croesusDimOthers = v);
        b.toggle("  Hide Opened Chests", () -> config.croesusHideOpened, v -> config.croesusHideOpened = v);
        b.toggle("  Compact Mode (profit only)", () -> config.croesusCompact, v -> config.croesusCompact = v);
        b.priceSource("  Price Source", () -> config.croesusPriceSource, v -> config.croesusPriceSource = v);
        b.toggle("  Subtract Kismet Feather", () -> config.croesusKismetCost, v -> config.croesusKismetCost = v);
        b.toggle("  Kismet Feathers On Reroll Button", () -> config.croesusKismetCount, v -> config.croesusKismetCount = v)
                .desc("Feathers in your inventory plus your Dungeon Sack (the sack count needs 'Index Opened Containers' and the sack opened once)");
        b.toggle("Highlight Unopened (green)", () -> config.croesusHighlightUnopened, v -> config.croesusHighlightUnopened = v);
        b.label("Kuudra Chests");
        b.info("Paid chests subtract the key: coins + Mycelium/Red Sand + 2 Nether Stars");
        b.info("Open your faction's shop once to read your key prices and faction");
        b.slider("Kuudra Pet Essence Bonus %", 0, 20, () -> config.croesusKuudraPetBonus,
                v -> config.croesusKuudraPetBonus = v, 1);
        b.toggle("Kuudra Armor at Salvage Value", () -> config.croesusKuudraSalvage, v -> config.croesusKuudraSalvage = v);
        b.toggle("Warn Without Kuudra Key", () -> config.croesusKuudraNoKeyWarn, v -> config.croesusKuudraNoKeyWarn = v);
    }
}
