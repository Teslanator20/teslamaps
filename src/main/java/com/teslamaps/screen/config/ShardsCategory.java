package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ShardsCategory {

    private ShardsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Hunting Box");
        b.toggle("Hunting Box Value", () -> config.huntingBoxValue, v -> config.huntingBoxValue = v);
        b.info("Bazaar value of every shard in the box, most valuable first");
        b.fresh(() -> com.teslamaps.features.hunting.HuntingProfitTracker.config(b, config));
        b.toggle("Mark Syphon-Ready Shards", () -> config.shardSyphonReady, v -> config.shardSyphonReady = v).fresh()
                .desc("Green: enough owned to level up. Yellow: enough, but the attribute is still locked");
        b.label("Shard Overlay");
        b.toggle("Shard Overlay", () -> config.shardOverlay, v -> config.shardOverlay = v);
        b.info("Shows in /attributemenu and the bazaar shard pages");
        b.dropdown("  Sort By",
                com.teslamaps.features.hunting.ShardOverlay.SORTS,
                () -> config.shardSort, v -> config.shardSort = v);
        b.dropdown("  Price Source",
                com.teslamaps.features.hunting.ShardOverlay.PRICE_SOURCES,
                () -> config.shardPriceSource, v -> config.shardPriceSource = v);
        b.toggle("  Hide Maxed", () -> config.shardHideMaxed, v -> config.shardHideMaxed = v);
        b.toggle("  Only Not Unlocked", () -> config.shardOnlyMissing, v -> config.shardOnlyMissing = v);
        b.toggle("Count Hunting Box", () -> config.shardIncludeBox, v -> config.shardIncludeBox = v);
        b.toggle("Count Bazaar Orders", () -> config.shardIncludeOrders, v -> config.shardIncludeOrders = v);
        b.info("Open your bazaar orders once so they are known");
        b.toggle("Only Current Menu", () -> config.shardOnlyCurrent, v -> config.shardOnlyCurrent = v);
        b.info("Only the shards on the open bazaar / attribute page");
        b.toggle("Search Box In Panel", () -> config.shardPanelSearch, v -> config.shardPanelSearch = v);
        b.toggle("Track Fusions", () -> config.shardFusionTracking, v -> config.shardFusionTracking = v)
                .desc("Open Confirm Fusion: the FUSION! line takes the inputs off and adds the output.");
        b.toggle("Fusion Display", () -> config.fusionDisplay, v -> config.fusionDisplay = v).fresh()
                .desc("In the Fusion Machine: last fused shard with its Hunting Box amount and PURE REPTILE procs. Position in /tmap gui.");
        b.toggle("Fusion Keybinds", () -> config.fusionKeybinds, v -> config.fusionKeybinds = v).fresh().cheat()
                .desc("Repeat key clicks Repeat in the Fusion Box, Confirm key clicks Confirm. One click per press.");
        b.keybind("  Repeat Fusion Key", () -> config.fusionRepeatKey, v -> config.fusionRepeatKey = v).fresh();
        b.keybind("  Confirm Fusion Key", () -> config.fusionConfirmKey, v -> config.fusionConfirmKey = v).fresh();
        b.toggle("Syphon Overflow Hint", () -> config.shardSyphonHint, v -> config.shardSyphonHint = v)
                .desc("Clickable /attributemenu line when a syphon says \"and N more...\"");
        b.toggle("Tier As Stack Size", () -> config.shardTierStack, v -> config.shardTierStack = v);
        b.toggle("Tier Color Scale", () -> config.shardTierColors, v -> config.shardTierColors = v);
        b.info("0 red, 1-5 yellow, 6-9 green, 10 gold");
        b.toggle("Highlight Maxed / Unfinished", () -> config.shardHighlightProgress, v -> config.shardHighlightProgress = v);
        b.toggle("Highlight Disabled Attributes", () -> config.shardHighlightDisabled, v -> config.shardHighlightDisabled = v);
        b.info("Click a row to look the shard up on the bazaar");
        b.toggle("  Mark Searched Item On Bazaar Page", () -> config.bzPurchaseHelper, v -> config.bzPurchaseHelper = v).fresh()
                .desc("After our own /bz jump (shard list, Get From Sacks, sack table) the searched item is marked green until you buy or sell it");
        b.button("Reset Hunting Box Data",
                com.teslamaps.features.hunting.ShardData::resetBox);
    }
}
