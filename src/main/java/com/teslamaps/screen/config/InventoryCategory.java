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
        b.toggle("  Block Unsalvageable Items", () -> config.salvageBlockOthers, v -> config.salvageBlockOthers = v)
                .desc("Salvage menu: clicks on inventory items without a base stat boost do nothing. Hold ctrl to override.");
        b.toggle("Salvage: Mark Undonated Museum Items", () -> config.salvageMuseumGuard, v -> config.salvageMuseumGuard = v)
                .desc("Salvage menu: items the museum still wants get a red background (museum API data).");
        b.toggle("  Block Salvaging Them", () -> config.salvageMuseumBlock, v -> config.salvageMuseumBlock = v)
                .desc("Clicking such an item plays an anvil sound instead. Hold ctrl to override.");
        b.toggle("Sellable Items Highlighter", () -> config.sellableHighlighter, v -> config.sellableHighlighter = v);
        b.toggle("  More Junk + Trades Menu", () -> config.sellableExtended, v -> config.sellableExtended = v);
        b.color("  Sellable Color", () -> config.colorSellable, v -> config.colorSellable = v);
        b.toggle("  Trash Highlighter (every menu)", () -> config.trashHighlighter, v -> config.trashHighlighter = v)
                .desc("Marks items matching your own trash filter list in every container, inventory included.");
        b.button("    Edit Trash Filters",
                () -> Minecraft.getInstance().setScreen(new TrashFilterScreen(b.screen())));
        b.color("    Trash Color", () -> config.colorTrash, v -> config.colorTrash = v);
        b.dropdown("    Trash Style", com.teslamaps.features.TrashHighlighter.STYLES,
                () -> config.trashHighlightStyle, v -> config.trashHighlightStyle = v);
        b.dropdown("  Ophelia Potion Lock", com.teslamaps.features.OpheliaPotLock.LEVELS,
                () -> config.opheliaPotLock, v -> config.opheliaPotLock = v);
        b.info("  Ophelia: only this Dungeon Potion can be bought, other shop clicks are blocked");
        b.toggle("Stat Boost Quality Highlight", () -> config.salvageQuality, v -> config.salvageQuality = v);
        b.info("Dungeon gear in any menu: below 50% vs perfect 50% base stat boost");
        b.color("  Below 50% Color", () -> config.colorStatBoostLow, v -> config.colorStatBoostLow = v);
        b.color("  50% Color", () -> config.colorStatBoostMax, v -> config.colorStatBoostMax = v);
        b.toggle("Attribute Overlay (legacy attributes)", () -> config.attributeOverlay, v -> config.attributeOverlay = v);
        b.info("Two attribute short names + levels in the slot, yellow = good roll, green = partial");
        b.toggle("  Highlight Good Rolls", () -> config.attributeOverlayGoodRolls, v -> config.attributeOverlayGoodRolls = v);
        b.toggle("  Only Good Rolls", () -> config.attributeOverlayOnlyGood, v -> config.attributeOverlayOnlyGood = v);
        b.slider("  Min Level", 0f, 10f,
                () -> (float) config.attributeOverlayMinLevel, v -> config.attributeOverlayMinLevel = Math.round(v), 0);
        b.toggle("Brewing Stand Overlay", () -> config.brewingStandOverlay, v -> config.brewingStandOverlay = v);
        b.info("Ingredient, progress and output names next to the slots");
        b.toggle("Color Brewing Stands", () -> config.brewingStandColors, v -> config.brewingStandColors = v)
                .desc("Private Island: stands turn red while brewing, green when done. Open each stand once.");
        b.toggle("Magical Power As Stack Size", () -> config.magicalPowerStack, v -> config.magicalPowerStack = v)
                .desc("Accessory Bag and auction house: MP of each accessory on its slot");
        b.toggle("  Rarity Color", () -> config.magicalPowerRarityColor, v -> config.magicalPowerRarityColor = v);
        b.info("Open your Abiphone once so Abicases count its contacts");
        b.toggle("Show Selected Pet", () -> config.showSelectedPet, v -> config.showSelectedPet = v);
        b.toggle("Mark Disabled Potion Effects", () -> config.highlightDisabledPotions, v -> config.highlightDisabledPotions = v)
                .desc("Toggle Potion Effects menu: disabled effects get a red background.");
        b.toggle("Move Insert Into Sack Button", () -> config.moveInsertIntoSack, v -> config.moveInsertIntoSack = v)
                .desc("Sack menus show the Insert Into Sack chest three slots further left. Clicks still hit the real slot.");
        b.toggle("Bingo Card Tips", () -> config.bingoCardTips, v -> config.bingoCardTips = v)
                .desc("Bingo Card goals tinted by difficulty, tooltip shows the difficulty, a guide and who found it.");
        b.toggle("  Skip Finished Goals", () -> config.bingoHideDoneDifficulty, v -> config.bingoHideDoneDifficulty = v);
        b.toggle("Bingo Card Display", () -> config.bingoCardDisplay, v -> config.bingoCardDisplay = v)
                .desc("Bingo profile: HUD with the open personal and community goals from /bingo. Move it in /tmap gui.");
        b.toggle("  Hide Community Goals", () -> config.bingoHideCommunity, v -> config.bingoHideCommunity = v);
        b.toggle("  Next Hint Timer", () -> config.bingoNextHint, v -> config.bingoNextHint = v);
        b.toggle("  Community % Change in Chat", () -> config.bingoCommunityChange, v -> config.bingoCommunityChange = v);
        b.toggle("  Next Step Helper", () -> config.bingoStepHelper, v -> config.bingoStepHelper = v)
                .desc("Breaks goals into steps (islands, skills, items) and shows what to do next");
        b.toggle("Pet Menu Shift Right-Click", () -> config.petShiftTakeOut, v -> config.petShiftTakeOut = v);
        b.info("Pets menu: right-click only works with shift held");
        b.toggle("Middle Click GUI", () -> config.middleClickGui, v -> config.middleClickGui = v)
                .desc("SkyBlock menus: a left-click on a menu button is sent as a middle-click, so nothing sticks to the cursor. Hold Ctrl for a normal click.");
        b.keybind("  Blacklist Menu Key", () -> config.middleClickGuiBlacklistKey, v -> config.middleClickGuiBlacklistKey = v);
        b.info("  Press it in a menu to add or remove that menu from the blacklist");
        b.button("  Clear Blacklist", () -> { config.middleClickGuiBlacklist.clear(); TeslaMapsConfig.save(); });
        b.toggle("Page Keybinds (A/D, W/S)", () -> config.pageKeybinds, v -> config.pageKeybinds = v);
        b.info("A/D = Previous/Next Page, W/S = Scroll Up/Down");
        b.toggle("  Mouse Wheel Pages", () -> config.pageScrollWheel, v -> config.pageScrollWheel = v)
                .desc("Wheel clicks Next/Previous Page and Scroll Up/Down. Off in plain chests, hold Shift over an item.");
        b.toggle("    Invert Wheel", () -> config.pageScrollInvert, v -> config.pageScrollInvert = v);
        b.toggle("Ignore Filler Items", () -> config.hideFillerItems, v -> config.hideFillerItems = v)
                .desc("No tooltip on blank glass panes, no clicks on fillers and locked backpack slots (off in dungeons and experiments)");
        b.toggle("Sign Enter Key", () -> config.signEnterKey, v -> config.signEnterKey = v);
        b.toggle("  Shift+Enter = New Line", () -> config.signEnterShiftNewline, v -> config.signEnterShiftNewline = v);
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
        b.toggle("  Calculator In Signs", () -> config.signCalculator, v -> config.signCalculator = v)
                .desc("Bazaar/AH/craft amount signs: shows 1.5k*3 = 4,500 live and puts the result in on confirm. k/m/b/t, s=64, e=160, x, purse");
        b.toggle("    Only With = Prefix", () -> config.signCalculatorEquals, v -> config.signCalculatorEquals = v);
        b.toggle("Backpack/Ender Preview", () -> config.backpackPreview, v -> config.backpackPreview = v);
        b.toggle("  Only While Shift Held", () -> config.backpackPreviewShift, v -> config.backpackPreviewShift = v);
        b.keybind("  Freeze Preview Key", () -> config.backpackPreviewFreezeKey, v -> config.backpackPreviewFreezeKey = v);
        b.info("Freeze pins the preview, hover its items for their tooltip");
        b.dropdown("  Preview Style", com.teslamaps.features.BackpackPreview.STYLES,
                () -> config.backpackPreviewStyle, v -> config.backpackPreviewStyle = v);
        b.toggle("  Backpack Dye Colors", () -> config.backpackPreviewColors, v -> config.backpackPreviewColors = v)
                .desc("Tints previews and opened backpacks in the backpack's dye colour.");
        b.toggle("  New Year Cake Bag", () -> config.backpackPreviewCakeBag, v -> config.backpackPreviewCakeBag = v)
                .desc("Open the cake bag once, then hover it to see its cakes.");
        b.toggle("Backpack Slot Next To Hotbar", () -> config.backpackSlot, v -> config.backpackSlot = v);
        b.info("Select it, arrow keys switch backpack, Enter opens it. Uses cached storage");
        b.keybind("  Select Key", () -> config.backpackSlotKey, v -> config.backpackSlotKey = v);
        b.toggle("  Select By Scrolling Past The Hotbar", () -> config.backpackSlotScroll, v -> config.backpackSlotScroll = v);
        b.toggle("  Left Of Hotbar", () -> config.backpackSlotLeft, v -> config.backpackSlotLeft = v);
        b.toggle("  Show Contents When Selected", () -> config.backpackSlotPreview, v -> config.backpackSlotPreview = v);
        b.slider("  Background Opacity", 0.1f, 1f,
                () -> config.backpackSlotOpacity, v -> config.backpackSlotOpacity = v, 2);
        b.toggle("  Preview From Item Data", () -> config.itemDataPreview, v -> config.itemDataPreview = v)
                .desc("Cake bags, Builder's Wand and backpack items show their contents, even never opened");
        b.toggle("    Cake Bag From Item Data", () -> config.itemDataPreviewCakeBag, v -> config.itemDataPreviewCakeBag = v);
        b.toggle("    Also In Auction House", () -> config.itemDataPreviewInAh, v -> config.itemDataPreviewInAh = v);
        b.label("Item Search");
        b.toggle("Index Opened Containers", () -> config.itemSearchIndex, v -> config.itemSearchIndex = v);
        b.info("/tmap search finds items in museum, sacks, vault, chests, storage");
        b.toggle("  Island Chest Tracker", () -> config.islandChestTracker, v -> config.islandChestTracker = v);
        b.info("Private Island: large chests per half, minion chests skipped, broken chests removed");
        b.toggle("    Warp To Island On Click", () -> config.itemSearchWarpIsland, v -> config.itemSearchWarpIsland = v)
                .desc("Clicking an island chest hit away from your island sends /warp island");
        b.keybind("Open Item Search", () -> config.itemSearchKey, v -> config.itemSearchKey = v);
        b.toggle("  Search Hovered Item", () -> config.itemSearchFromHover, v -> config.itemSearchFromHover = v)
                .desc("The search key over an item in a menu opens the search with its name");
        b.keybind("  Highlight Hovered Item Key", () -> config.itemHighlightKey, v -> config.itemHighlightKey = v);
        b.info("Marks that item in every menu for 30s, plus waypoints on chests holding it");
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
        b.toggle("  Copy Lowest BIN - 1", () -> config.auctionCopyUnderbid, v -> config.auctionCopyUnderbid = v)
                .desc("Putting an item into Create BIN Auction copies its lowest BIN minus one coin to the clipboard. Works without the price input too.");
        b.toggle("Escrow Fix (Reopen AH/BZ)", () -> config.escrowFix, v -> config.escrowFix = v).cheat()
                .desc("After an escrow message closed the menu (expired auction, invalid bid, BIN claim, bazaar refund) /ah or /bz is sent once. 5s cooldown, never while another menu is open.");
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
        b.toggle("  Every \"Enter query\" Sign", () -> config.searchOverlayAllSigns, v -> config.searchOverlayAllSigns = v)
                .desc("Also replaces search signs outside AH / Bazaar / recipes (museum, other menus). Their searches get their own history.");
        b.toggle("  Keep Previous Text", () -> config.searchOverlayKeepText, v -> config.searchOverlayKeepText = v);
        b.toggle("  Recent Searches", () -> config.searchOverlayHistory, v -> config.searchOverlayHistory = v);
        b.toggle("  Paste Copied Name", () -> config.searchPasteCopied, v -> config.searchPasteCopied = v);
        b.toggle("  Also In /recipes", () -> config.searchOverlayRecipes, v -> config.searchOverlayRecipes = v);
        b.toggle("  Also In The Museum", () -> config.searchOverlayMuseum, v -> config.searchOverlayMuseum = v)
                .desc("Museum search sign: suggestions from your donations (armor sets by name), own history. Without Museum Donation Tooltip on it suggests every museum item.");
        b.slider("  History Size", 1f, 30f,
                () -> (float) config.searchOverlayHistorySize, v -> config.searchOverlayHistorySize = Math.round(v));
        b.toggle("  /ahs, /bzs Commands", () -> config.searchOverlayCommands, v -> config.searchOverlayCommands = v)
                .desc("/ahs, /ahsearch and /bzs open the search from anywhere, with item suggestions. Off: the command goes to the server unchanged.");
        b.toggle("    Ctrl+F In AH / Bazaar", () -> config.searchOverlayCtrlF, v -> config.searchOverlayCtrlF = v);
        b.toggle("  Pet Level / Star Filters", () -> config.searchOverlayFilters, v -> config.searchOverlayFilters = v)
                .desc("Auction search buttons: Max Lvl puts [Lvl 100/200] before pet names (off: \"] \" so only pets show), the star button adds ✪ / master stars to starrable items.");
        b.toggle("Middle Click Copies Item Name", () -> config.middleClickCopyName, v -> config.middleClickCopyName = v);
        b.toggle("Price Lookup Key", () -> config.priceLookup, v -> config.priceLookup = v)
                .desc("Press the key over an item in any menu: /bz for bazaar items, /ahs for auction items. Pets and enchanted books are looked up by their real name.");
        b.keybind("  Lookup Key", () -> config.priceLookupKey, v -> config.priceLookupKey = v);
        b.keybind("  Recipe Key (/viewrecipe)", () -> config.priceLookupRecipeKey, v -> config.priceLookupRecipeKey = v);
        b.label("Storage Overlay");
        b.toggle("Custom Storage Overlay", () -> config.customStorageOverlay, v -> config.customStorageOverlay = v);
        b.toggle("  Show Page Names", () -> config.storageShowNames, v -> config.storageShowNames = v);
        b.toggle("    Rename Pages (click title)", () -> config.storageRenamePages, v -> config.storageRenamePages = v);
        b.info("Needs Show Page Names. & for color codes, empty = default name");
        b.slider("  Overlay Scale", 0.5f, 1.5f,
                () -> config.storageOverlayScale, v -> config.storageOverlayScale = v);
        b.toggle("  Vanilla Menu After Backpack Swap", () -> config.storageVanillaAfterSwap, v -> config.storageVanillaAfterSwap = v);
        b.toggle("  GUI Scale Override", () -> config.storageGuiScaleOverride, v -> config.storageGuiScaleOverride = v)
                .desc("Own GUI scale while ender chest / backpack / storage menus are open, restored on close.");
        b.slider("    Storage GUI Scale", 1f, 5f,
                () -> (float) config.storageGuiScale, v -> config.storageGuiScale = Math.round(v), 0);
        b.toggle("  Prefill Empty Storage From API", () -> config.storageApiFill, v -> config.storageApiFill = v);
        b.toggle("  Toolkit / Home / Hide Buttons", () -> config.storageQuickButtons, v -> config.storageQuickButtons = v)
                .desc("Header buttons: Farming Toolkit, Hunting Toolkit, Home (back to /storage, also reloads) and Hide (vanilla menu until reopened).");
        b.toggle("  Vanilla Textures", () -> config.storageVanillaTextures, v -> config.storageVanillaTextures = v)
                .desc("Pages drawn with the chest texture, so resource packs apply.");
        b.slider("  Columns", 1f, 10f,
                () -> (float) config.storageColumns, v -> config.storageColumns = Math.round(v), 0);
        b.slider("  Scroll Speed", 5f, 80f,
                () -> (float) config.storageScrollSpeed, v -> config.storageScrollSpeed = Math.round(v), 0);
        b.toggle("  Compact Vertically", () -> config.storageCompact, v -> config.storageCompact = v);
        b.toggle("  Center On Open Page", () -> config.storageCenterActive, v -> config.storageCenterActive = v);
        b.toggle("  Focus Search On Open", () -> config.storageSearchFocus, v -> config.storageSearchFocus = v);
        b.toggle("  Remember Last Page", () -> config.storageRememberLast, v -> config.storageRememberLast = v)
                .desc("/storage opens the page you used last (/ec n or /bp n)");
        b.toggle("  Settle Page Swaps", () -> config.storageSettleSwap, v -> config.storageSettleSwap = v)
                .desc("WASD and clicks move a local selection first, one command once you stop on a page");
        b.toggle("  Show Cached Items While Loading", () -> config.storageCachedWhileLoading, v -> config.storageCachedWhileLoading = v);
        b.slider("  Overlay Width (%)", 40f, 100f,
                () -> (float) config.storageWidthPercent, v -> config.storageWidthPercent = Math.round(v), 0);
        b.toggle("  Show Never Opened Pages", () -> config.storageShowUnopened, v -> config.storageShowUnopened = v)
                .desc("Open /storage once: unlocked pages show up even without cached content, empty slots are hidden");
        b.toggle("  Search Result List", () -> config.storageSearchResults, v -> config.storageSearchResults = v)
                .desc("All hits with page name; click one to open its page and mark the slot. Ctrl+A/C/V/X in the search box");
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
        b.toggle("  Per SkyBlock Profile", () -> config.slotLockPerProfile, v -> config.slotLockPerProfile = v)
                .desc("Each SkyBlock profile keeps its own locks. A new profile starts from the shared set.");
        b.toggle("  Separate Rift Locks", () -> config.slotLockRiftSet, v -> config.slotLockRiftSet = v)
                .desc("The Rift inventory gets its own lock set.");
        b.toggle("  Bound Slots Count As Locked", () -> config.slotLockBinds, v -> config.slotLockBinds = v)
                .desc("Slots in a Slot Bind are locked too. Shift still swaps them.");
        b.slider("  Drop Delay After Slot Change (ms)", 0f, 500f,
                () -> (float) config.slotLockDropDelay, v -> config.slotLockDropDelay = Math.round(v / 10f) * 10, 0)
                .desc("Q does nothing for this long after scrolling to another hotbar slot. 0 = off.");
        b.button("  Reset All Locks", com.teslamaps.features.SlotLock::resetAll);
        b.toggle("  Lock Icon In Hotbar", () -> config.slotLockHotbarIcon, v -> config.slotLockHotbarIcon = v);
        b.keybind("  Lock Selected Hotbar Slot Key", () -> config.slotLockWorldKey, v -> config.slotLockWorldKey = v)
                .desc("Locks or unlocks the selected hotbar slot without opening the inventory");
        b.toggle("  Block Item Frames", () -> config.slotLockItemFrames, v -> config.slotLockItemFrames = v)
                .desc("Items from locked slots can't go into empty item frames on your island or garden");
        b.toggle("  Drop Shining Coins In Haunted Biome", () -> config.slotLockShiningCoins, v -> config.slotLockShiningCoins = v)
                .desc("Critter Safari, Haunted biome only: Q still throws a Shining Coin from a locked slot");
        b.label("Item Protection");
        b.toggle("Protect Valuable Items", () -> config.itemProtection, v -> config.itemProtection = v);
        b.slider("  Min Value (million)", 1f, 500f,
                () -> (float) config.itemProtectionValue, v -> config.itemProtectionValue = Math.round(v), 0);
        b.toggle("  Block Salvage & Selling", () -> config.itemProtectionSell, v -> config.itemProtectionSell = v);
        b.info("Blocks the Q drop and dropping onto empty space");
        b.info("Hold ctrl to override. Uses the estimated item value");
        b.toggle("  Also Guard Trade, AH & Shops", () -> config.itemProtectionMenus, v -> config.itemProtectionMenus = v);
        b.info("Trade menu, auction house, dungeon chests, any NPC shop");
        b.toggle("Protect By Rarity", () -> config.itemProtectionRarity, v -> config.itemProtectionRarity = v)
                .desc("Protects items from a minimum rarity on, also untradables without a price. Hold ctrl to override");
        b.dropdown("  Hotbar Min Rarity", com.teslamaps.features.ItemProtection.RARITY_OPTIONS,
                () -> config.itemProtectionHotbarRarity, v -> config.itemProtectionHotbarRarity = v);
        b.dropdown("  Inventory Min Rarity", com.teslamaps.features.ItemProtection.RARITY_OPTIONS,
                () -> config.itemProtectionInvRarity, v -> config.itemProtectionInvRarity = v);
        b.toggle("  Inventory: Drop After 3 Tries", () -> config.itemProtectionConfirm, v -> config.itemProtectionConfirm = v)
                .desc("From the inventory the drop goes through on the 3rd try within 3s instead of a hard block");
        b.toggle("  Block Drop Right After Slot Switch", () -> config.itemProtectionSwitchGuard, v -> config.itemProtectionSwitchGuard = v)
                .desc("A Q within 200ms after scrolling onto a protected hotbar item is always blocked");
        b.toggle("  Also Block NPC Selling", () -> config.itemProtectionRaritySell, v -> config.itemProtectionRaritySell = v);
        b.toggle("  Always Protect Starred Items", () -> config.itemProtectionStarred, v -> config.itemProtectionStarred = v)
                .desc("Any item with stars counts as valuable, even without price data.");
        b.toggle("Protect Single Items", () -> config.itemProtectionUuid, v -> config.itemProtectionUuid = v);
        b.keybind("  Protect Key (over slot)", () -> config.itemProtectionKey, v -> config.itemProtectionKey = v);
        b.info("Or /tmap protect for the held item. Lock icon, blocks drop, salvage, trade, AH, shops");
        b.toggle("Limit Dropping Items", () -> config.limitDroppingItems, v -> config.limitDroppingItems = v)
                .desc("Max 7 drops per 3 server ticks, spammed Q during lag can't throw the whole stack later");
        b.toggle("Protect Evolving Consumables", () -> config.protectEvolvingConsumables, v -> config.protectEvolvingConsumables = v)
                .desc("Blocks right-click and placing for Bottle of Jyrre, Dark Cacao Truffle, Discrite, Moby Duck, Rosewater Flask");
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
        b.keybind("  Customize Hovered Item Key", () -> config.customizeKey, v -> config.customizeKey = v)
                .desc("Over any slot, opens the customize screen for exactly that item");
        b.toggle("  Items Without UUID", () -> config.customizeNoUuid, v -> {
                    config.customizeNoUuid = v;
                    com.teslamaps.features.customize.Customize.refresh();
                })
                .desc("Stackable items get their item id (+ timestamp) as key, so the change shows on every copy of that item");
        b.info("/tmap customize rename|dye|trim|glint|model|reset ... acts on the held item");
        b.toggle("  Glint Colors", () -> config.customGlintColors, v -> config.customGlintColors = v)
                .desc("Adds a glint color field to the Item tab of the customize screen.");
        b.dropdown("  Mark Modified Items", com.teslamaps.features.customize.ModifyIndicator.MODES,
                () -> config.modifyIndicator, v -> config.modifyIndicator = v)
                .desc("Customized items get a small ✎ before or after their name, or a \"Modified by TeslaMaps\" lore line.");
        b.toggle("Axes In 1.8 Look", () -> config.revertAxes, v -> config.revertAxes = v)
                .desc("Ragnarock and Daedalus Axe render as a golden axe, Axe of the Shredded as a diamond axe. Visual only.");
        b.toggle("  Migrate Renamed Item Models", () -> config.migrateItemModels, v -> config.migrateItemModels = v);
        b.info("On launch: renamed Hypixel models are rewritten, removed ones dropped (NEU repo)");
        b.label("Rarity Backgrounds");
        b.toggle("Enable Rarity Backgrounds", () -> config.rarityBackgrounds, v -> config.rarityBackgrounds = v);
        b.slider("  Opacity", 0f, 1f,
                () -> config.rarityBgOpacity, v -> config.rarityBgOpacity = v);
        b.dropdown("  Shape",
                com.teslamaps.features.RarityBackground.SHAPES, () -> config.rarityBgShape, v -> config.rarityBgShape = v);
        b.dropdown("  Style",
                com.teslamaps.features.RarityBackground.STYLES, () -> config.rarityBgStyle, v -> config.rarityBgStyle = v);
        b.dropdown("  Colors",
                com.teslamaps.features.RarityBackground.PALETTES, () -> config.rarityBgPalette, v -> config.rarityBgPalette = v)
                .desc("Hypixel = chat colors, Modern = softer blue, purple, gold and red");
        b.toggle("  Hide On Hotbar", () -> config.rarityBgHideHotbar, v -> config.rarityBgHideHotbar = v);
        b.toggle("Legacy Attribute Background", () -> config.legacyAttributeBg, v -> config.legacyAttributeBg = v)
                .desc("Red slot behind items that still carry old Kuudra attributes");
        b.color("  Legacy Attribute Color", () -> config.colorLegacyAttributeBg, v -> config.colorLegacyAttributeBg = v);
        b.toggle("Stars As Stack Size", () -> config.starsStackSize, v -> config.starsStackSize = v)
                .desc("Star count of dungeon/Kuudra items in the bottom right of the slot, containers and hotbar");
        b.toggle("  Gradient Colors", () -> config.starsStackSizeColors, v -> config.starsStackSizeColors = v);
        b.toggle("  Old Master Star Style", () -> config.oldMasterStars, v -> {
            config.oldMasterStars = v;
            com.teslamaps.features.OldMasterStar.clear();
        }).desc("§6✪✪✪✪✪§c➌ §7becomes §c✪✪✪§6✪✪§7 in item names, skin stars ✦ included.");
        b.toggle("Red Master Stars In Name", () -> config.redMasterStars, v -> config.redMasterStars = v)
                .desc("Tooltip name shows master stars as red ✪ instead of ➊-➎");
        b.label("Bazaar & Progress");
        b.toggle("Bazaar: Highlight Own Orders", () -> config.bazaarOwnOrders, v -> config.bazaarOwnOrders = v);
        b.toggle("  Only My Orders In Co-op Orders", () -> config.bazaarOnlyMyOrders, v -> config.bazaarOnlyMyOrders = v)
                .desc("Co-op Bazaar Orders: orders of other co-op members are hidden and can't be clicked");
        b.toggle("Bazaar: Own Orders In Create-Order Tooltip", () -> config.bazaarOrderTooltip, v -> config.bazaarOrderTooltip = v)
                .desc("Create Buy Order / Sell Offer: \"N of them are yours\" under each price level. Open Your Bazaar Orders once so it knows them.");
        b.toggle("Bazaar: Check Mark On Filled Orders", () -> config.bazaarOrderFilled, v -> config.bazaarOrderFilled = v);
        b.toggle("Bazaar: Order Status Colors", () -> config.bazaarOrderStatus, v -> config.bazaarOrderStatus = v);
        b.info("Your Bazaar Orders: filled, outbid/undercut, still top, % partly filled, ⏰ expiry");
        b.color("  Filled Color", () -> config.colorBazaarOrderFilled, v -> config.colorBazaarOrderFilled = v);
        b.color("  Outbid Color", () -> config.colorBazaarOrderOutbid, v -> config.colorBazaarOrderOutbid = v);
        b.color("  Top Order Color", () -> config.colorBazaarOrderCompetitive, v -> config.colorBazaarOrderCompetitive = v);
        b.toggle("Bazaar: Shorter Order Tooltips", () -> config.bazaarOrderTooltipClean, v -> config.bazaarOrderTooltipClean = v)
                .desc("Bazaar order menu: removes lines you don't need from the order tooltips");
        b.toggle("  Hide \"By:\" Line (Only Own Orders)", () -> config.bazaarHideOrderOwner, v -> config.bazaarHideOrderOwner = v)
                .desc("Only while every order in the menu is yours; Hypixel shows it in co-ops");
        b.toggle("  Hide Trade Partners", () -> config.bazaarHideTradePartners, v -> config.bazaarHideTradePartners = v)
                .desc("The Vendors / Customers list of players that filled your order");
        b.toggle("Bazaar: Estimate Order Fills", () -> config.bazaarFillEstimate, v -> config.bazaarFillEstimate = v)
                .desc("Guesses how far your orders are filled from the public order book, without opening the menu. HUD in /tmap gui. Rough: other players cancelling at your price looks like a fill.");
        b.toggle("  Chat On New Fill", () -> config.bazaarFillEstimateAlert, v -> config.bazaarFillEstimateAlert = v);
        b.toggle("  Sound On New Fill", () -> config.bazaarFillEstimateSound, v -> config.bazaarFillEstimateSound = v);
        b.toggle("Progress Highlight (Ways to Level Up / Bestiary)", () -> config.progressHighlight, v -> config.progressHighlight = v);
        b.color("  Progress Color", () -> config.colorProgressDone, v -> config.colorProgressDone = v);
        b.toggle("  Bestiary Tier As Number", () -> config.progressHighlightTier, v -> config.progressHighlightTier = v)
                .desc("Writes the family tier as a number in the slot corner, locked families show 0");
        b.toggle("  Mark Unfinished Red", () -> config.progressHighlightRed, v -> config.progressHighlightRed = v);
        b.color("  Unfinished Color", () -> config.colorProgressUnfinished, v -> config.colorProgressUnfinished = v);
        b.toggle("  Slayer LVL Rewards: Unclaimed Green", () -> config.slayerRewardsHighlight, v -> config.slayerRewardsHighlight = v)
                .desc("Slayer LVL Rewards menu: rewards you can still claim get a green background");
        b.toggle("Commission Highlight", () -> config.commissionHighlight, v -> config.commissionHighlight = v);
        b.color("  Commission Color", () -> config.colorCommissionDone, v -> config.colorCommissionDone = v);
        b.toggle("  Status Icons (Open / Started / Claimable)", () -> config.commissionStatusIcons, v -> config.commissionStatusIcons = v)
                .desc("Commissions menu: not started = written book, started = book and quill, ready to claim = knowledge book. No glint.");
        b.dropdown("  Commission Tips", com.teslamaps.features.mining.CommissionTips.MODES,
                () -> config.commissionTips, v -> config.commissionTips = v)
                .desc("Hint in the tooltip where each commission is done, e.g. \"Kill 50 Glacite Walkers (Great Ice Wall)\"");
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
        b.toggle("  Essence Shop: Cost To Max + Coin Value", () -> config.essenceShopCosts, v -> config.essenceShopCosts = v)
                .desc("In the essence shops: coin value of each upgrade, essence left to max, and a summary on the top item (owned, missing, bazaar price).");
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
        b.toggle("  Close After Picking A Set", () -> config.wardrobeAutoClose, v -> config.wardrobeAutoClose = v)
                .desc("Closes wardrobe/loadouts right after you pick a set, by key or click.");
        b.slider("    Close Delay (ms)", 0f, 1000f,
                () -> (float) config.wardrobeCloseDelay, v -> config.wardrobeCloseDelay = Math.round(v), 0);
        b.toggle("    Only When Equipping", () -> config.wardrobeCloseOnlyEquip, v -> config.wardrobeCloseOnlyEquip = v);
        b.toggle("    Confirm Sound", () -> config.wardrobeCloseSound, v -> config.wardrobeCloseSound = v);
        b.keybind("  Next Page",
                () -> config.wardrobeNextPageKey, v -> config.wardrobeNextPageKey = v);
        b.keybind("  Previous Page",
                () -> config.wardrobePrevPageKey, v -> config.wardrobePrevPageKey = v);
        b.toggle("Custom Wardrobe", () -> config.customWardrobe, v -> config.customWardrobe = v);
        b.info("Card grid with 3D preview. Click equips, right-click favorites, Vanilla button switches back");
        b.toggle("  Hide Empty Slots", () -> config.customWardrobeHideEmpty, v -> config.customWardrobeHideEmpty = v);
        b.toggle("  Only Favorites", () -> config.customWardrobeOnlyFavorites, v -> config.customWardrobeOnlyFavorites = v);
        b.toggle("  Show Estimated Value", () -> config.customWardrobeValue, v -> config.customWardrobeValue = v);
        b.toggle("  Head Follows Mouse", () -> config.customWardrobeFollowMouse, v -> config.customWardrobeFollowMouse = v);
        b.slider("  Card Width", 40f, 120f,
                () -> (float) config.customWardrobeCardWidth, v -> config.customWardrobeCardWidth = Math.round(v), 0);
        b.slider("  Card Height", 60f, 200f,
                () -> (float) config.customWardrobeCardHeight, v -> config.customWardrobeCardHeight = Math.round(v), 0);
        b.color("  Equipped Color", () -> config.colorWardrobeEquipped, v -> config.colorWardrobeEquipped = v);
        b.color("  Favorite Color", () -> config.colorWardrobeFavorite, v -> config.colorWardrobeFavorite = v);
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
        b.toggle("Insta Loadout Swap", () -> config.instaLoadoutSwap, v -> config.instaLoadoutSwap = v).cheat()
                .desc("Press a loadout key anywhere outside menus: sends /loadout, clicks that loadout after the delay and closes the menu again.");
        b.info("  One key press = /loadout + click + close, done for you");
        b.toggle("  Auto Close Menu", () -> config.instaLoadoutAutoClose, v -> config.instaLoadoutAutoClose = v);
        b.slider("  Click Delay (s)", 0.1f, 2f, () -> config.instaLoadoutDelay, v -> config.instaLoadoutDelay = v, 2);
        b.slider("  Random Extra Delay (ms)", 0f, 1000f,
                () -> (float) config.instaLoadoutRandomMs, v -> config.instaLoadoutRandomMs = Math.round(v), 0);
        for (int i = 0; i < 9; i++) {
            final int idx = i;
            b.keybind("  Swap To Loadout " + (i + 1),
                    () -> config.instaLoadoutKeys[idx], v -> config.instaLoadoutKeys[idx] = v);
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
        b.dropdown("  Double Click To Run", com.teslamaps.features.invbuttons.InventoryButtons.CONFIRM_MODES,
                () -> config.invButtonConfirm, v -> config.invButtonConfirm = v)
                .desc("First click only arms the button (chime + tooltip), a second click within 1 s runs it.");
    }
}
