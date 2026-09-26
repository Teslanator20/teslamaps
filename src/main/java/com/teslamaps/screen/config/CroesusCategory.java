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
        b.toggle("  Subtract Dungeon Chest Key", () -> config.croesusKeyCost, v -> config.croesusKeyCost = v).fresh()
                .desc("Chests that need a Dungeon Chest Key get its bazaar price added to the cost");
        b.toggle("  Exclude Essence", () -> config.croesusNoEssence, v -> config.croesusNoEssence = v).fresh()
                .desc("Essence is still listed, but not counted in the profit");
        b.toggle("  Highlight 2nd Best (yellow)", () -> config.croesusHighlightSecond, v -> config.croesusHighlightSecond = v).fresh();
        b.toggle("  Derpy Warning", () -> config.croesusDerpyWarn, v -> config.croesusDerpyWarn = v).fresh()
                .desc("Note in the panel while Derpy is mayor, essence values are unreliable then");
        b.toggle("Reroll Protection", () -> config.croesusRerollProtect, v -> config.croesusRerollProtect = v).fresh()
                .desc("Blocks the Reroll Chest click when the chest is worth at least the amount below. Can be bypassed: hold CTRL while clicking to reroll anyway.");
        b.slider("  Block From Profit (M)", 0.5f, 50f, () -> config.croesusRerollProtectMin,
                v -> config.croesusRerollProtectMin = v, 1).fresh();
        b.info("  Hold CTRL to bypass the reroll block").fresh();
        b.toggle("Chest Profit HUD", () -> config.chestProfitHud, v -> config.chestProfitHud = v).fresh()
                .desc("Movable list of all dungeon chests by profit, in the dungeon after opening them and near Croesus. Move it in /tmap gui.");
        b.toggle("  Include Essence", () -> config.chestProfitHudEssence, v -> config.chestProfitHudEssence = v).fresh();
        b.toggle("  Compact (profit only)", () -> config.chestProfitHudCompact, v -> config.chestProfitHudCompact = v).fresh();
        b.toggle("  Kismet Feathers On Reroll Button", () -> config.croesusKismetCount, v -> config.croesusKismetCount = v).fresh()
                .desc("Feathers in your inventory plus your Dungeon Sack (the sack count needs 'Index Opened Containers' and the sack opened once)");
        b.toggle("Highlight Unopened (green)", () -> config.croesusHighlightUnopened, v -> config.croesusHighlightUnopened = v);
        b.toggle("  Also in Vesuvius (Kuudra)", () -> config.croesusVesuvius, v -> config.croesusVesuvius = v).fresh()
                .desc("The run highlights (green unopened, red partly opened) also work in Vesuvius' Kuudra chest menu");
        b.toggle("Loot Logger", () -> config.lootLogger, v -> config.lootLogger = v).fresh()
                .desc("Saves every bought dungeon chest with items, cost, key and kismet. /tmap loot [floor] [days] shows spent and profit.");
        b.label("Kuudra Chests");
        b.info("Paid chests subtract the key: coins + Mycelium/Red Sand + 2 Nether Stars");
        b.info("Open your faction's shop once to read your key prices and faction");
        b.slider("Kuudra Pet Essence Bonus %", 0, 20, () -> config.croesusKuudraPetBonus,
                v -> config.croesusKuudraPetBonus = v, 1);
        b.toggle("Kuudra Armor at Salvage Value", () -> config.croesusKuudraSalvage, v -> config.croesusKuudraSalvage = v);
        b.toggle("Warn Without Kuudra Key", () -> config.croesusKuudraNoKeyWarn, v -> config.croesusKuudraNoKeyWarn = v);
    }
}
