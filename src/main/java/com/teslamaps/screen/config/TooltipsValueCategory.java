package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class TooltipsValueCategory {

    private TooltipsValueCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Tooltips");
        b.toggle("Scrollable Tooltip", () -> config.scrollableTooltip, v -> config.scrollableTooltip = v);
        b.toggle("Fit Tooltip To Screen", () -> config.tooltipFitScreen, v -> config.tooltipFitScreen = v);
        b.info("Long tooltips shrink instead of running off the top");
        b.toggle("Compactor/Deletor Preview", () -> config.compactorPreview, v -> config.compactorPreview = v);
        b.info("Tooltip shows on/off status and configured items");
        b.label("Item Value");
        b.toggle("Estimated Item Value", () -> config.estimatedValue, v -> config.estimatedValue = v);
        b.toggle("Value Breakdown Panel (hover)", () -> config.itemValueGui, v -> config.itemValueGui = v);
        b.slider("  Panel Scale", 0.5f, 2.0f,
                () -> config.itemValueScale, v -> config.itemValueScale = v, 2);
        b.toggle("  Free Position", () -> config.itemValueFixedPos, v -> config.itemValueFixedPos = v);
        b.info("Drag it in /tmap gui instead of pinning it to the container");
        b.keybind("Value Screen Key (over slot)", () -> config.itemValueScreenKey, v -> config.itemValueScreenKey = v);
        b.info("Pinned breakdown, sorted by price with % share, scroll to read");
        b.slider("Hover Delay (ms)", 0f, 2000f,
                () -> (float) config.itemValueHoverDelay, v -> config.itemValueHoverDelay = Math.round(v / 50f) * 50);
        b.info("Only the first item of a container waits, the rest show instantly");
        b.slider("Enchantments Cap", 1f, 30f,
                () -> (float) config.itemValueEnchantmentsCap, v -> config.itemValueEnchantmentsCap = Math.round(v));
        b.slider("Star Material Cap", 1f, 15f,
                () -> (float) config.itemValueStarMaterialCap, v -> config.itemValueStarMaterialCap = Math.round(v));
        b.dropdown("Price Source",
                new String[]{"BZ Instant Buy", "BZ Instant Sell", "Lowest BIN", "NPC Sell"},
                () -> config.itemValuePriceSource, v -> config.itemValuePriceSource = v);
        b.toggle("Craft Cost Fallback", () -> config.priceCraftFallback, v -> config.priceCraftFallback = v);
        b.info("Items with no market price use their raw craft cost (Croesus, trackers, value)");
        b.toggle("Lowest BIN / Bazaar Line", () -> config.itemValueShowBin, v -> config.itemValueShowBin = v);
        b.info("Market price on every tooltip, BZ insta buy/sell or lowest BIN");
        b.toggle("  Hide Insta Buy/Sell", () -> config.priceLineHideInstant, v -> config.priceLineHideInstant = v);
        b.toggle("  Buy Order / Sell Offer", () -> config.priceLineOrders, v -> config.priceLineOrders = v);
        b.toggle("  NPC Sell Price", () -> config.priceLineNpc, v -> config.priceLineNpc = v);
        b.toggle("  Raw Craft Cost", () -> config.priceLineCraft, v -> config.priceLineCraft = v);
        b.toggle("  Total + Each, Aligned", () -> config.priceLineEach, v -> config.priceLineEach = v);
        b.info("\"1,200 coins (100 each)\" in two columns, no Shift needed");
        b.toggle("  Only While Key Held", () -> config.priceLineRequireKey, v -> config.priceLineRequireKey = v);
        b.keybind("  Price Key", () -> config.priceLineKey, v -> config.priceLineKey = v);
        b.toggle("Show Exact Price", () -> config.itemValueExactPrice, v -> config.itemValueExactPrice = v);
        b.toggle("Ignore Helmet Skins", () -> config.itemValueIgnoreHelmetSkins, v -> config.itemValueIgnoreHelmetSkins = v);
        b.toggle("Ignore Armor Dyes", () -> config.itemValueIgnoreArmorDyes, v -> config.itemValueIgnoreArmorDyes = v);
        b.toggle("Ignore Runes", () -> config.itemValueIgnoreRunes, v -> config.itemValueIgnoreRunes = v);
        b.toggle("Container Value (Top 5)", () -> config.containerValue, v -> config.containerValue = v);
        b.toggle("  Group Same Items", () -> config.containerValueGroup, v -> config.containerValueGroup = v);
        b.toggle("  Hover Breakdown + Slots", () -> config.containerValueHover, v -> config.containerValueHover = v);
        b.slider("  Items Shown", 1f, 20f,
                () -> (float) config.containerValueTop, v -> config.containerValueTop = Math.round(v));
        b.slider("  Hide Below (k coins)", 0f, 1000f,
                () -> (float) config.containerValueHideBelow, v -> config.containerValueHideBelow = Math.round(v / 10f) * 10);
        b.toggle("  Free Position", () -> config.containerValueFreePos, v -> config.containerValueFreePos = v);
        b.info("Drag it in /tmap gui instead of pinning it to the container");
        b.label("Enchant Colors");
        b.toggle("Book Abbreviations (Leg I)", () -> config.enchantAbbrev, v -> config.enchantAbbrev = v);
        b.info("Enchant + level written on enchanted book icons");
        b.color("  Abbreviation Color", () -> config.colorEnchantAbbrev, v -> config.colorEnchantAbbrev = v);
        b.color("  Ultimate Color", () -> config.colorEnchantAbbrevUltimate, v -> config.colorEnchantAbbrevUltimate = v);
        b.color("  Level Color", () -> config.colorEnchantAbbrevLevel, v -> config.colorEnchantAbbrevLevel = v);
        b.toggle("Enchant Colors", () -> config.enchantColors, v -> config.enchantColors = v);
        b.toggle("Levels as Numbers", () -> config.enchantLevelsAsNumbers, v -> config.enchantLevelsAsNumbers = v);
        b.toggle("Chroma Max Enchants (rainbow)", () -> config.enchantChromaMax, v -> config.enchantChromaMax = v);
        b.slider("  Chroma Size", 5f, 120f,
                () -> config.chromaSize, v -> config.chromaSize = v, 0);
        b.slider("  Chroma Speed", 0f, 30f,
                () -> config.chromaSpeed, v -> config.chromaSpeed = v, 1);
        b.slider("  Chroma Saturation", 0f, 1f,
                () -> config.chromaSaturation, v -> config.chromaSaturation = v, 2);
        b.toggle("  Chroma Slant Forward", () -> config.chromaForward, v -> config.chromaForward = v);
        b.toggle("  Chroma Bold", () -> config.enchantChromaBold, v -> config.enchantChromaBold = v);
        b.toggle("Bold Max Level", () -> config.enchantBoldMax, v -> config.enchantBoldMax = v);
        b.color("Max Level Color", () -> config.enchantColorMax, v -> config.enchantColorMax = v);
        b.color("High Level Color", () -> config.enchantColorHigh, v -> config.enchantColorHigh = v);
        b.color("Normal Level Color", () -> config.enchantColorNormal, v -> config.enchantColorNormal = v);
        b.color("Bad Level Color", () -> config.enchantColorBad, v -> config.enchantColorBad = v);
    }
}
