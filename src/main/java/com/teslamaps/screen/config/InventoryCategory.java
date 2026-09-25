package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class InventoryCategory {

    private InventoryCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Container Helpers");
        b.toggle("Salvage Helper", () -> config.salvageHelper, v -> config.salvageHelper = v);
        b.toggle("Sellable Items Highlighter", () -> config.sellableHighlighter, v -> config.sellableHighlighter = v);
        b.toggle("  More Junk + Trades Menu", () -> config.sellableExtended, v -> config.sellableExtended = v);
        b.color("  Sellable Color", () -> config.colorSellable, v -> config.colorSellable = v);
        b.toggle("Stat Boost Quality Highlight", () -> config.salvageQuality, v -> config.salvageQuality = v);
        b.info("Dungeon gear in any menu: below 50% vs perfect 50% base stat boost");
        b.color("  Below 50% Color", () -> config.colorStatBoostLow, v -> config.colorStatBoostLow = v);
        b.color("  50% Color", () -> config.colorStatBoostMax, v -> config.colorStatBoostMax = v);
        b.toggle("Show Selected Pet", () -> config.showSelectedPet, v -> config.showSelectedPet = v);
        b.toggle("Pet Menu Shift Right-Click", () -> config.petShiftTakeOut, v -> config.petShiftTakeOut = v);
        b.info("Pets menu: right-click only works with shift held");
        b.toggle("Page Keybinds (A/D, W/S)", () -> config.pageKeybinds, v -> config.pageKeybinds = v);
        b.info("A/D = Previous/Next Page, W/S = Scroll Up/Down");
        b.toggle("Sign Enter Key", () -> config.signEnterKey, v -> config.signEnterKey = v);
        b.toggle("No Cursor Reset", () -> config.noCursorReset, v -> config.noCursorReset = v);
        b.slider("  Hold Time (s)", 0.5f, 30f,
                () -> config.noCursorResetHold, v -> config.noCursorResetHold = v);
        b.toggle("  Ignore Hold Time", () -> config.noCursorResetForever, v -> config.noCursorResetForever = v);
        b.info("Also restores after closing a menu, walking and reopening");
        b.toggle("Own Nametag (3rd person)", () -> config.ownNameTag, v -> config.ownNameTag = v);
        b.toggle("Searchbar (type to search)", () -> config.searchbar, v -> config.searchbar = v);
        b.toggle("  Ctrl+F To Search", () -> config.searchbarCtrlF, v -> config.searchbarCtrlF = v);
        b.toggle("    Hide Until Ctrl+F", () -> config.searchbarHidden, v -> config.searchbarHidden = v);
        b.dropdown("  Highlight", com.teslamaps.features.Searchbar.MODES, () -> config.searchbarMode, v -> config.searchbarMode = v);
        b.color("  Match Color", () -> config.colorSearchbarMatch, v -> config.colorSearchbarMatch = v);
        b.toggle("  Name Only (skip lore)", () -> config.searchbarNameOnly, v -> config.searchbarNameOnly = v);
        b.toggle("Backpack/Ender Preview", () -> config.backpackPreview, v -> config.backpackPreview = v);
        b.toggle("  Only While Shift Held", () -> config.backpackPreviewShift, v -> config.backpackPreviewShift = v);
        b.keybind("  Freeze Preview Key", () -> config.backpackPreviewFreezeKey, v -> config.backpackPreviewFreezeKey = v);
        b.info("Freeze pins the preview, hover its items for their tooltip");
        b.label("Item Search");
        b.toggle("Index Opened Containers", () -> config.itemSearchIndex, v -> config.itemSearchIndex = v);
        b.info("/tmap search finds items in museum, sacks, vault, chests, storage");
        b.toggle("  Island Chest Tracker", () -> config.islandChestTracker, v -> config.islandChestTracker = v);
        b.info("Private Island: large chests per half, minion chests skipped, broken chests removed");
        b.keybind("Open Item Search", () -> config.itemSearchKey, v -> config.itemSearchKey = v);
        b.toggle("Right-Click Sack Item: /gfs", () -> config.itemSearchGfsClick, v -> config.itemSearchGfsClick = v);
        b.button("Open Item Search",
                () -> Minecraft.getInstance().setScreen(new ItemSearchScreen(null)));
        b.button("Clear Item Index",
                com.teslamaps.features.ItemIndex::clear);
        b.button("Clear Island Chests",
                com.teslamaps.features.ItemIndex::clearChests);
        b.label("Auction Price Input");
        b.toggle("Auction Price Input", () -> config.auctionPriceInput, v -> config.auctionPriceInput = v);
        b.dropdown("  Mode",
                new String[]{"Normal", "Undercut"}, () -> config.auctionDefaultMode, v -> config.auctionDefaultMode = v);
        b.label("Auction Price Comparison");
        b.toggle("Highlight Value Deals", () -> config.auctionPriceCompare, v -> config.auctionPriceCompare = v);
        b.info("Green when it sells below the estimated item value, red above");
        b.toggle("  Only Good Deals", () -> config.auctionCompareOnlyGood, v -> config.auctionCompareOnlyGood = v);
        b.slider("  Threshold (%)", 1f, 50f,
                () -> config.auctionCompareThreshold, v -> config.auctionCompareThreshold = v, 0);
        b.slider("  Opacity", 0.05f, 1f,
                () -> config.auctionCompareOpacity, v -> config.auctionCompareOpacity = v, 2);
        b.toggle("  Gradient Relative To Page", () -> config.auctionCompareGradient, v -> config.auctionCompareGradient = v);
        b.info("Best deal on the page gets the Very Good color, the rest fade toward Good");
        b.color("  Very Good Color", () -> config.colorAuctionVeryGood, v -> config.colorAuctionVeryGood = v);
        b.color("  Good Color", () -> config.colorAuctionGood, v -> config.colorAuctionGood = v);
        b.color("  Bad Color", () -> config.colorAuctionBad, v -> config.colorAuctionBad = v);
        b.color("  Very Bad Color", () -> config.colorAuctionVeryBad, v -> config.colorAuctionVeryBad = v);
        b.toggle("  Toggle Button In Auction House", () -> config.auctionCompareButton, v -> config.auctionCompareButton = v);
        b.label("Auction / Bazaar Search");
        b.toggle("Custom Search Input", () -> config.searchOverlay, v -> config.searchOverlay = v);
        b.info("Textbox with item name suggestions instead of the search sign");
        b.slider("  Suggestions", 0f, 12f,
                () -> (float) config.searchOverlaySuggestions, v -> config.searchOverlaySuggestions = Math.round(v));
        b.toggle("  Keep Previous Text", () -> config.searchOverlayKeepText, v -> config.searchOverlayKeepText = v);
        b.toggle("  Recent Searches", () -> config.searchOverlayHistory, v -> config.searchOverlayHistory = v);
        b.toggle("  Paste Copied Name", () -> config.searchPasteCopied, v -> config.searchPasteCopied = v);
        b.toggle("Middle Click Copies Item Name", () -> config.middleClickCopyName, v -> config.middleClickCopyName = v);
        b.label("Storage Overlay");
        b.toggle("Custom Storage Overlay", () -> config.customStorageOverlay, v -> config.customStorageOverlay = v);
        b.toggle("  Show Page Names", () -> config.storageShowNames, v -> config.storageShowNames = v);
        b.toggle("    Rename Pages (click title)", () -> config.storageRenamePages, v -> config.storageRenamePages = v);
        b.info("Needs Show Page Names. & for color codes, empty = default name");
        b.slider("  Overlay Scale", 0.5f, 1.5f,
                () -> config.storageOverlayScale, v -> config.storageOverlayScale = v);
        b.toggle("  Vanilla Menu After Backpack Swap", () -> config.storageVanillaAfterSwap, v -> config.storageVanillaAfterSwap = v);
        b.toggle("  Prefill Empty Storage From API", () -> config.storageApiFill, v -> config.storageApiFill = v);
        b.slider("  Columns", 1f, 10f,
                () -> (float) config.storageColumns, v -> config.storageColumns = Math.round(v), 0);
        b.slider("  Scroll Speed", 5f, 80f,
                () -> (float) config.storageScrollSpeed, v -> config.storageScrollSpeed = Math.round(v), 0);
        b.toggle("  Compact Vertically", () -> config.storageCompact, v -> config.storageCompact = v);
        b.toggle("  Center On Open Page", () -> config.storageCenterActive, v -> config.storageCenterActive = v);
        b.toggle("  Focus Search On Open", () -> config.storageSearchFocus, v -> config.storageSearchFocus = v);
        b.label("Hovered Slot");
        b.toggle("Hovered Slot Outline", () -> config.hoveredSlotOutline, v -> config.hoveredSlotOutline = v);
        b.color("  Outline Color", () -> config.colorHoveredSlot, v -> config.colorHoveredSlot = v);
        b.slider("  Thickness", 1f, 3f,
                () -> (float) config.hoveredSlotThickness, v -> config.hoveredSlotThickness = Math.round(v));
        b.slider("  Fill Opacity", 0f, 0.5f,
                () -> config.hoveredSlotFill, v -> config.hoveredSlotFill = v);
        b.label("Slot Lock");
        b.toggle("Slot Lock", () -> config.slotLock, v -> config.slotLock = v);
        b.keybind("  Slot Lock Key (over slot)", () -> config.slotLockKey, v -> config.slotLockKey = v);
        b.dropdown("  Lock Style", com.teslamaps.features.SlotLock.STYLES,
                () -> config.slotLockStyle, v -> config.slotLockStyle = v);
        b.color("  Locked Slot Color", () -> config.colorSlotLock, v -> config.colorSlotLock = v);
        b.label("Item Protection");
        b.toggle("Protect Valuable Items", () -> config.itemProtection, v -> config.itemProtection = v);
        b.slider("  Min Value (million)", 1f, 500f,
                () -> (float) config.itemProtectionValue, v -> config.itemProtectionValue = Math.round(v), 0);
        b.toggle("  Block Salvage & Selling", () -> config.itemProtectionSell, v -> config.itemProtectionSell = v);
        b.info("Blocks the Q drop and dropping onto empty space");
        b.info("Hold ctrl to override. Uses the estimated item value");
        b.toggle("  Also Guard Trade, AH & Shops", () -> config.itemProtectionMenus, v -> config.itemProtectionMenus = v);
        b.info("Trade menu, auction house, dungeon chests, any NPC shop");
        b.toggle("Protect Single Items", () -> config.itemProtectionUuid, v -> config.itemProtectionUuid = v);
        b.keybind("  Protect Key (over slot)", () -> config.itemProtectionKey, v -> config.itemProtectionKey = v);
        b.info("Or /tmap protect for the held item. Lock icon, blocks drop, salvage, trade, AH, shops");
        b.toggle("Limit Dropping Items", () -> config.limitDroppingItems, v -> config.limitDroppingItems = v)
                .desc("Max 7 drops per 3 server ticks, spammed Q during lag can't throw the whole stack later");
        b.label("Prevent Placing");
        b.toggle("Prevent Placing Weapons", () -> config.preventPlacingWeapons, v -> config.preventPlacingWeapons = v)
                .desc("Flower of Truth, Bouquet, Grappleshot, Bat Wand, Tuba, Pumpkin Launcher, Fire Freeze, Gauntlet");
        b.toggle("Prevent Placing Ability Heads", () -> config.preventPlacingHeads, v -> config.preventPlacingHeads = v)
                .desc("Player heads with a right-click ability use it instead of being placed");
        b.toggle("  Except Garden", () -> config.preventPlacingExceptGarden, v -> config.preventPlacingExceptGarden = v);
        b.keybind("  Allow Held Head Key", () -> config.preventPlacingBlacklistKey, v -> config.preventPlacingBlacklistKey = v);
        b.info("Toggles the held head between guarded and placeable");
        b.label("Slot Binds");
        b.toggle("Slot Binds", () -> config.slotBinds, v -> config.slotBinds = v);
        b.keybind("  Bind Set Key (over slot)", () -> config.slotBindsKey, v -> config.slotBindsKey = v);
        b.color("  Bind Color", () -> config.colorSlotBinds, v -> config.colorSlotBinds = v);
        b.slider("  Line Width", 0.1f, 2f,
                () -> config.slotBindsLineWidth, v -> config.slotBindsLineWidth = v);
        b.dropdown("  Line Display",
                com.teslamaps.features.SlotBinds.LINE_MODES, () -> config.slotBindsLineDisplay, v -> config.slotBindsLineDisplay = v);
        b.dropdown("  Profile",
                com.teslamaps.features.SlotBinds.PROFILES, () -> config.slotBindsProfile, v -> config.slotBindsProfile = v);
        b.toggle("  Work In Every Container", () -> config.slotBindsAllContainers, v -> config.slotBindsAllContainers = v);
        b.toggle("  Per SkyBlock Profile", () -> config.slotBindsPerProfile, v -> config.slotBindsPerProfile = v);
        b.toggle("  Switch Profile By Area", () -> config.slotBindsAreaSwitch, v -> config.slotBindsAreaSwitch = v);
        b.buttonRow(new String[]{"Use Profile Here", "Clear Areas"},
                new Runnable[]{com.teslamaps.features.SlotBinds::bindCurrentArea, com.teslamaps.features.SlotBinds::clearAreas});
        b.label("Customization");
        b.toggle("Button In Inventory", () -> config.showCustomizeButton, v -> config.showCustomizeButton = v);
        b.button("Open Customize Screen",
                () -> Minecraft.getInstance().setScreen(new com.teslamaps.screen.customize.CustomizeScreen(b.screen(), false)));
        b.info("Rename, dye, trim and reskin items - client side only (/tmap customize)");
        b.label("Rarity Backgrounds");
        b.toggle("Enable Rarity Backgrounds", () -> config.rarityBackgrounds, v -> config.rarityBackgrounds = v);
        b.slider("  Opacity", 0f, 1f,
                () -> config.rarityBgOpacity, v -> config.rarityBgOpacity = v);
        b.dropdown("  Shape",
                com.teslamaps.features.RarityBackground.SHAPES, () -> config.rarityBgShape, v -> config.rarityBgShape = v);
        b.dropdown("  Style",
                com.teslamaps.features.RarityBackground.STYLES, () -> config.rarityBgStyle, v -> config.rarityBgStyle = v);
        b.toggle("Legacy Attribute Background", () -> config.legacyAttributeBg, v -> config.legacyAttributeBg = v)
                .desc("Red slot behind items that still carry old Kuudra attributes");
        b.color("  Legacy Attribute Color", () -> config.colorLegacyAttributeBg, v -> config.colorLegacyAttributeBg = v);
        b.label("Bazaar & Progress");
        b.toggle("Bazaar: Highlight Own Orders", () -> config.bazaarOwnOrders, v -> config.bazaarOwnOrders = v);
        b.toggle("Bazaar: Check Mark On Filled Orders", () -> config.bazaarOrderFilled, v -> config.bazaarOrderFilled = v);
        b.toggle("Bazaar: Order Status Colors", () -> config.bazaarOrderStatus, v -> config.bazaarOrderStatus = v);
        b.info("Your Bazaar Orders: filled, outbid/undercut, still top, % partly filled, ⏰ expiry");
        b.color("  Filled Color", () -> config.colorBazaarOrderFilled, v -> config.colorBazaarOrderFilled = v);
        b.color("  Outbid Color", () -> config.colorBazaarOrderOutbid, v -> config.colorBazaarOrderOutbid = v);
        b.color("  Top Order Color", () -> config.colorBazaarOrderCompetitive, v -> config.colorBazaarOrderCompetitive = v);
        b.toggle("Progress Highlight (Ways to Level Up / Bestiary)", () -> config.progressHighlight, v -> config.progressHighlight = v);
        b.color("  Progress Color", () -> config.colorProgressDone, v -> config.colorProgressDone = v);
        b.toggle("Commission Highlight", () -> config.commissionHighlight, v -> config.commissionHighlight = v);
        b.color("  Commission Color", () -> config.colorCommissionDone, v -> config.colorCommissionDone = v);
        b.label("Guide Highlight");
        b.info("Marks what is still MISSING in guide menus");
        b.toggle("Guide Highlight", () -> config.guideHighlight, v -> config.guideHighlight = v);
        b.color("  Missing Color", () -> config.colorGuideMissing, v -> config.colorGuideMissing = v);
        b.toggle("  SkyBlock Level Guide", () -> config.guideMissingTasks, v -> config.guideMissingTasks = v);
        b.toggle("  One Time Completions", () -> config.guideOneTime, v -> config.guideOneTime = v);
        b.info("Bank, Spooky, Dojo, Slaying, Rock/Dolphin, Dungeons");
        b.toggle("  Story & Travels & Harp", () -> config.guideStory, v -> config.guideStory = v);
        b.toggle("  Abiphone Contacts", () -> config.guideAbiphone, v -> config.guideAbiphone = v);
        b.toggle("  Jacob's Contest Guide", () -> config.guideJacob, v -> config.guideJacob = v);
        b.toggle("  Essence Shops", () -> config.guideEssence, v -> config.guideEssence = v);
        b.toggle("  Uncrafted Minions", () -> config.guideMinion, v -> config.guideMinion = v);
        b.toggle("  Consumable Items", () -> config.guideConsumable, v -> config.guideConsumable = v);
        b.toggle("  Tasks Menu", () -> config.guideMenu, v -> config.guideMenu = v);
        b.toggle("  Collections", () -> config.guideCollection, v -> config.guideCollection = v);
        b.toggle("  Century Raffle Tasks", () -> config.guideCentury, v -> config.guideCentury = v);
        b.toggle("  Click Marked Item For Wiki", () -> config.guideWikiClick, v -> config.guideWikiClick = v);
        b.label("Wardrobe & Loadout Keybinds");
        b.toggle("Wardrobe Keybinds", () -> config.wardrobeKeybinds, v -> config.wardrobeKeybinds = v);
        b.info("Unbound slots fall back to your hotbar keys (1-9)");
        b.toggle("  Don't Unequip Worn Set", () -> config.wardrobeKeepEquipped, v -> config.wardrobeKeepEquipped = v);
        b.toggle("  Show Keys On Buttons", () -> config.wardrobeShowKeys, v -> config.wardrobeShowKeys = v);
        b.keybind("  Next Page",
                () -> config.wardrobeNextPageKey, v -> config.wardrobeNextPageKey = v);
        b.keybind("  Previous Page",
                () -> config.wardrobePrevPageKey, v -> config.wardrobePrevPageKey = v);
        b.label("Wardrobe Slots (9 per page)");
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            b.keybind("Wardrobe Slot " + (i + 1),
                    () -> config.wardrobeKeys[idx], v -> config.wardrobeKeys[idx] = v);
        }
        // 12 per page, so 10-12 need binds of their own - the hotbar only has nine keys
        b.label("Loadouts (12 per page)");
        for (int i = 0; i < 12; i++) {
            final int idx = i;
            b.keybind("Loadout " + (i + 1),
                    () -> config.loadoutKeys[idx], v -> config.loadoutKeys[idx] = v);
        }

        b.label("Inventory Buttons");
        b.toggle("Inventory Buttons", () -> config.inventoryButtons, v -> config.inventoryButtons = v);
        b.info("Command buttons around every container screen");
        b.button("Edit Buttons (/tmap buttons)",
                () -> Minecraft.getInstance().setScreen(new InventoryButtonEditorScreen(b.screen())));
        b.toggle("  Hide in Dungeon Menus", () -> config.invButtonsHideInDungeonMenus, v -> config.invButtonsHideInDungeonMenus = v);
        b.dropdown("  Run Command On",
                new String[]{"Mouse Down", "Mouse Up"},
                () -> config.invButtonClickType == 1 ? "Mouse Up" : "Mouse Down",
                v -> config.invButtonClickType = "Mouse Up".equals(v) ? 1 : 0);
        b.slider("  Tooltip Delay (ms)", 0f, 1000f,
                () -> (float) config.invButtonTooltipDelay, v -> config.invButtonTooltipDelay = Math.round(v));
    }
}
