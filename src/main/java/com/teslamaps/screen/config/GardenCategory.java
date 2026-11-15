package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class GardenCategory {

    private GardenCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Plot Teleport Grid");
        b.toggle("Plots Overlay",
                () -> config.gardenPlotsOverlay, v -> config.gardenPlotsOverlay = v);
        b.toggle("  Close Inventory On Teleport",
                () -> config.gardenPlotsCloseOnClick, v -> config.gardenPlotsCloseOnClick = v);
        b.toggle("  Pest Count Per Plot",
                () -> config.gardenPlotsPestCount, v -> config.gardenPlotsPestCount = v).fresh()
                .desc("Gold pest count on each plot in the grid, and a red backdrop with the count in the Desk's Configure Plots menu")
                .tags("pests", "desk", "configure plots");
        b.label("Visitors");
        b.toggle("Charmed Visitor List",
                () -> config.charmedVisitorList, v -> config.charmedVisitorList = v);
        b.info("Visitors charmed with the Gift Vinyl Set, click a name to drop it");
        b.button("Clear Charmed List",
                () -> com.teslamaps.features.CharmedVisitors.clear());
        b.toggle("6th Visitor Warning",
                () -> config.sixthVisitorWarning, v -> config.sixthVisitorWarning = v);
        b.info("Hypixel hides the timer at 5 visitors, so this estimates it");
        b.soundDropdown("  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.sixthVisitorSound, v -> config.sixthVisitorSound = v);
        b.slider("  Volume", 0f, 4f,
                () -> config.sixthVisitorVolume, v -> config.sixthVisitorVolume = v, 2);
        b.toggle("  Title On Visitor Arrival",
                () -> config.visitorArrivalTitle, v -> config.visitorArrivalTitle = v).fresh()
                .desc("Title with the visitor's name in its rarity colour when one arrives on your Garden.")
                .tags("visitor", "arrival", "title", "notification");
        b.toggle("  Warp Link When Away",
                () -> config.visitorArrivalWarp, v -> config.visitorArrivalWarp = v).fresh()
                .desc("Outside the Garden: clickable chat line that runs /warp garden when a visitor arrives.");
        b.toggle("  Queue Full Title + Sound",
                () -> config.visitorQueueFullTitle, v -> config.visitorQueueFullTitle = v).fresh()
                .desc("Title and a low pling when the tab list says Next Visitor: Queue Full!");
        b.toggle("  Show Item Of Rare Visitors",
                () -> config.visitorRareArrivalItem, v -> config.visitorRareArrivalItem = v).fresh()
                .desc("Jerry, Spaceman and Rhino: their rare item shows big on screen for 5 seconds.");
        b.toggle("  Visitor HUD",
                () -> config.visitorHud, v -> config.visitorHud = v).fresh()
                .desc("\"3 visitors (Next in 3m 12s)\", keeps estimating once the queue is full. Move it in /tmap gui")
                .tags("visitor", "timer", "hud", "next visitor");
        b.toggle("    Visitor Names",
                () -> config.visitorHudNames, v -> config.visitorHudNames = v).fresh();
        b.label("Harvest Feast");
        b.toggle("In-Season Display",
                () -> config.feastSeasonDisplay, v -> config.feastSeasonDisplay = v);
        b.toggle("  Show Outside The Garden",
                () -> config.feastSeasonEverywhere, v -> config.feastSeasonEverywhere = v);
        b.info("Open Feast Chef Ted > All Crops once to fill it in");
        b.toggle("  Fetch From EliteSkyblock",
                () -> config.feastAutoFetch, v -> config.feastAutoFetch = v);
        b.info("Loads the rotation from api.eliteskyblock.com when none was read · /tmap feast fetch");
        b.toggle("  Outdated Data Warning",
                () -> config.feastOutdatedHint, v -> config.feastOutdatedHint = v);
        b.channel("  Share Channel",
                () -> config.feastShareChannel, v -> config.feastShareChannel = v);
        b.button("Share In-Season Crops",
                () -> com.teslamaps.features.HarvestFeast.share());
        b.label("Pests");
        b.info("Pest ESP moved to Mob Visuals - it tells the 15 species apart");
        b.button("Pest Visuals (Mob Visuals)",
                () -> Minecraft.getInstance().gui.setScreen(new MobVisualScreen(Minecraft.getInstance().gui.screen())));
        b.toggle("Mute Vacuum Sound",
                () -> config.muteVacuum, v -> config.muteVacuum = v).fresh()
                .desc("Silences the vacuum's wither shoot sound, only in the Garden and Torrhus Canyon")
                .tags("vacuum", "sound", "mute", "wither");
        b.label("Pest Tracker");
        b.toggle("Pest Overlay",
                () -> config.pestOverlay, v -> config.pestOverlay = v);
        b.toggle("  Alive + Infested Plots",
                () -> config.pestOverlayAlivePlots, v -> config.pestOverlayAlivePlots = v);
        b.toggle("  Last Spawn + Average Spawn Time",
                () -> config.pestSpawnStats, v -> config.pestSpawnStats = v).fresh()
                .desc("Time since the last pest spawn, and the average over cycles you farmed through without a 30s break")
                .tags("pest", "spawn", "timer", "average");
        b.toggle("  Chat: Pests Spawned In <time>",
                () -> config.pestSpawnChat, v -> config.pestSpawnChat = v).fresh();
        b.toggle("  Spawn Title",
                () -> config.pestSpawnTitle, v -> config.pestSpawnTitle = v).fresh()
                .desc("'N Pests Spawned in Plot X!' as a title on every spawn, offline spawns included");
        b.toggle("  Compact Clickable Spawn Message",
                () -> config.pestSpawnCompact, v -> config.pestSpawnCompact = v).fresh()
                .desc("Replaces Hypixel's spawn message and its CLICK HERE line with one line, click = /plottp");
        b.toggle("  Pest Finder Panel",
                () -> config.pestFinder, v -> config.pestFinder = v).fresh()
                .desc("Total pests and every infested plot with its pest count. With 'Move Panels From Inventory' on, a click on a plot teleports there. Position in /tmap gui")
                .tags("pest", "finder", "infested", "plot");
        b.toggle("  Infested Plot Walls",
                () -> config.pestFinderBorders, v -> config.pestFinderBorders = v).fresh()
                .desc("Borders of infested plots in the world, your own plot red, others gold");
        b.toggle("    Plot Labels",
                () -> config.pestFinderLabels, v -> config.pestFinderLabels = v).fresh();
        b.toggle("    Only With Vacuum / Lasso",
                () -> config.pestFinderHeldOnly, v -> config.pestFinderHeldOnly = v).fresh();
        b.keybind("  Teleport To Nearest Infested Plot",
                () -> config.pestFinderKey, v -> config.pestFinderKey = v).fresh()
                .desc("Nearest plot instead of the first one · /tmap tpinfested does the same");
        b.toggle("  'No Pests!' Title",
                () -> config.pestFinderNoPestTitle, v -> config.pestFinderNoPestTitle = v).fresh();
        b.toggle("  Pest Trap Warnings",
                () -> config.pestTrapWarn, v -> config.pestTrapWarn = v).fresh()
                .desc("Warns in an interval about full traps, traps without bait and traps not placed")
                .tags("pest", "trap", "bait");
        b.toggle("    Full Traps", () -> config.pestTrapWarnFull, v -> config.pestTrapWarnFull = v).fresh();
        b.toggle("    No Bait", () -> config.pestTrapWarnNoBait, v -> config.pestTrapWarnNoBait = v).fresh();
        b.toggle("    Unplaced Traps", () -> config.pestTrapWarnUnplaced, v -> config.pestTrapWarnUnplaced = v).fresh();
        b.toggle("    Chat", () -> config.pestTrapWarnChat, v -> config.pestTrapWarnChat = v).fresh();
        b.toggle("    Title", () -> config.pestTrapWarnTitle, v -> config.pestTrapWarnTitle = v).fresh();
        b.slider("    Interval (sec)", 10f, 600f,
                () -> (float) config.pestTrapWarnInterval, v -> config.pestTrapWarnInterval = Math.round(v), 0).fresh();
        b.soundDropdown("    Sound", com.teslamaps.utils.SoundOptions.keys(),
                () -> config.pestTrapWarnSound, v -> config.pestTrapWarnSound = v).fresh();
        b.slider("    Volume", 0f, 4f,
                () -> config.pestTrapWarnVolume, v -> config.pestTrapWarnVolume = v, 2).fresh();
        b.toggle("  Trap Release Key",
                () -> config.pestTrapRelease, v -> config.pestTrapRelease = v).fresh().cheat()
                .desc("In a Pest / Mouse / Vermin Trap menu the key clicks the release slot");
        b.keybind("    Release Key",
                () -> config.pestTrapReleaseKey, v -> config.pestTrapReleaseKey = v).fresh();
        b.toggle("  Crop Fever Timer",
                () -> config.cropFeverTimer, v -> config.cropFeverTimer = v).fresh()
                .desc("60 second countdown row while Crop Fever is active.");
        b.toggle("    Crop Fever Alert",
                () -> config.cropFeverAlert, v -> config.cropFeverAlert = v).fresh()
                .desc("Title and sound the moment Crop Fever starts.");
        b.soundDropdown("    Crop Fever Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.cropFeverSound, v -> config.cropFeverSound = v).fresh();
        b.toggle("  Spray Per Plot",
                () -> config.pestOverlaySprayList, v -> config.pestOverlaySprayList = v).fresh()
                .desc("Lists every sprayed plot with its time left or Expired, and Not sprayed! for the plot you stand on")
                .tags("spray", "sprayonator", "plot");
        b.toggle("  Spray Expired Chat",
                () -> config.sprayExpiryChat, v -> config.sprayExpiryChat = v).fresh()
                .desc("Chat line when a plot's spray runs out, also \"While you were away\" when you come back to the Garden")
                .tags("spray", "sprayonator", "expired");
        b.toggle("  Always Show Pest Chance",
                () -> config.pestChanceAlways, v -> config.pestChanceAlways = v).fresh()
                .desc("Bonus Pest Chance line even without the pest chance loadout. Struck through with OFF while Hypixel marks it disabled.");
        b.slider("Profit Idle After (sec)", 30f, 600f,
                () -> (float) config.farmIdleSeconds,
                v -> config.farmIdleSeconds = (int) (float) v, 0);
        b.toggle("Remind: Pest Chance Gear",
                () -> config.pestGearReminder, v -> config.pestGearReminder = v);
        b.toggle("Remind: Teleport To Pests",
                () -> config.pestTpReminder, v -> config.pestTpReminder = v);
        b.slider("  Min Pests (0 = Fortune Debuff)", 0f, 8f,
                () -> (float) config.pestTpReminderMin, v -> config.pestTpReminderMin = Math.round(v), 0).fresh()
                .desc("0 warns from 8 pests on, where the farming fortune debuff starts");
        b.slider("  Repeat Every (sec)", 1f, 60f,
                () -> (float) config.pestTpReminderInterval, v -> config.pestTpReminderInterval = Math.round(v), 0).fresh();
        b.toggle("Custom Cooldown",
                () -> config.pestCustomCooldown, v -> config.pestCustomCooldown = v);
        b.slider("  Custom Cooldown (sec)", 30f, 600f,
                () -> (float) config.pestCustomCooldownSeconds,
                v -> config.pestCustomCooldownSeconds = (int) (float) v, 0);
        b.toggle("  Own Length With Pest Eradicator",
                () -> config.pestFinneganCooldown, v -> config.pestFinneganCooldown = v).fresh()
                .desc("While Finnegan's Pest Eradicator perk is active the custom cooldown uses the length below");
        b.slider("    Pest Eradicator Cooldown (sec)", 30f, 600f,
                () -> (float) config.pestFinneganCooldownSeconds,
                v -> config.pestFinneganCooldownSeconds = (int) (float) v, 0).fresh();
        b.slider("Warn Seconds Before Ready", 0f, 30f,
                () -> (float) config.pestCooldownWarnAt, v -> config.pestCooldownWarnAt = (int) (float) v, 0);
        b.toggle("Cooldown Title",
                () -> config.pestCooldownTitle, v -> config.pestCooldownTitle = v);
        b.toggle("Cooldown Sound",
                () -> config.pestCooldownSound, v -> config.pestCooldownSound = v);
        b.soundDropdown("Cooldown Sound Type",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.pestCooldownSoundName, v -> config.pestCooldownSoundName = v);
        b.slider("Cooldown Volume", 0f, 4f,
                () -> config.pestCooldownVolume, v -> config.pestCooldownVolume = v, 2);
        b.label("Panels");
        b.toggle("Move Panels From Inventory",
                () -> config.hudDragInInventory, v -> config.hudDragInInventory = v);
        b.toggle("  Container Panels Too (Alt + Drag)",
                () -> config.containerPanelDrag, v -> config.containerPanelDrag = v).fresh()
                .desc("Croesus profit panel and shard overlay next to chest menus: hold Alt and drag to move, Alt + right click resets. Kept on screen.")
                .tags("croesus", "shard", "move", "position");
        b.label("Spray");
        b.toggle("Queue Trip When Spray Runs Out",
                () -> config.sprayRoutine, v -> config.sprayRoutine = v);
        b.label("Profit");
        b.toggle("Profit Tracker",
                () -> config.farmProfit, v -> config.farmProfit = v);
        b.slider("  Items Listed", 0f, 12f,
                () -> (float) config.farmProfitRows, v -> config.farmProfitRows = (int) (float) v, 0);
        b.dropdown("Price Basis",
                new String[]{"Instant Sell", "Sell Offer", "NPC"},
                () -> config.farmPriceMode, v -> config.farmPriceMode = v);
        b.toggle("Subtract Replenish Cost",
                () -> config.farmProfitReplenish, v -> config.farmProfitReplenish = v);
        b.toggle("Pest Species In Breakdown",
                () -> config.farmProfitPestTypes, v -> config.farmProfitPestTypes = v);
        b.toggle("  Pest Tracker Panel",
                () -> config.pestKillTracker, v -> config.pestKillTracker = v).fresh()
                .desc("Kills per pest species, their drops, kill coins and spray costs, session and total. Position in /tmap gui")
                .tags("pest", "kills", "spray", "profit", "tracker");
        b.toggle("    Subtract Spray Cost",
                () -> config.pestKillTrackerSprays, v -> config.pestKillTrackerSprays = v).fresh();
        b.toggle("    Rare Drop Rates",
                () -> config.pestKillTrackerRates, v -> config.pestKillTrackerRates = v).fresh();
        b.toggle("    Count Kill Coins (Purse)",
                () -> config.pestKillTrackerCoins, v -> config.pestKillTrackerCoins = v).fresh();
        b.toggle("    Hide Pest Drop Chat",
                () -> config.pestKillTrackerHideDropChat, v -> config.pestKillTrackerHideDropChat = v).fresh();
        b.priceSource("    Price Source",
                () -> config.pestKillTrackerPriceSource, v -> config.pestKillTrackerPriceSource = v).fresh();
        b.button("    Reset Pest Tracker Session", () -> {
            com.teslamaps.features.PestKillTracker.tracker().resetSession();
            com.teslamaps.features.PestKillTracker.resetRates();
        }).fresh();
        b.button("    Reset Pest Tracker Total",
                () -> com.teslamaps.features.PestKillTracker.tracker().resetTotal()).fresh();
        b.button("    Session / Total",
                () -> com.teslamaps.features.PestKillTracker.tracker().toggleView()).fresh();
        b.info("/tmap profit exclude <item> leaves an item out, run again to count it");
        b.toggle("  Item Options On Click",
                () -> config.farmProfitItemOptions, v -> config.farmProfitItemOptions = v).fresh()
                .desc("With the inventory open: left click an item in the panel to pick its own price source (blue value), right click twice to hide it")
                .tags("exclude", "price source", "profit");
        b.button("  Restore Hidden Items + Price Sources",
                () -> com.teslamaps.features.FarmProfit.clearItemOptions()).fresh();
        b.toggle("  Confirm Reset",
                () -> config.farmProfitResetConfirm, v -> config.farmProfitResetConfirm = v).fresh()
                .desc("The panel's reset needs a second click within 3 seconds");
        b.toggle("  Today + Mayor Windows",
                () -> config.farmProfitExtraWindows, v -> config.farmProfitExtraWindows = v).fresh()
                .desc("Adds Today and This Mayor to the time windows; counts from when this version was installed");
        b.button("Show Breakdown In Chat",
                () -> com.teslamaps.features.FarmProfit.printBreakdown());
        b.button("Reset Session Profit",
                () -> com.teslamaps.features.FarmProfit.resetSession());
        b.button("Reset All Time Profit",
                () -> com.teslamaps.features.FarmProfit.resetAllTime());
        b.label("Mouse");
        b.toggle("Lower Sensitivity With Tool",
                () -> config.farmingSensitivity, v -> config.farmingSensitivity = v);
        b.slider("Sensitivity While Farming", 0f, 1f,
                () -> config.farmingSensitivityValue, v -> config.farmingSensitivityValue = v, 2);
        b.toggle("  Only On Ground",
                () -> config.farmingSensitivityGroundOnly, v -> config.farmingSensitivityGroundOnly = v);
        b.toggle("  Pause In The Barn",
                () -> config.farmingSensitivityPauseBarn, v -> config.farmingSensitivityPauseBarn = v);
        b.info("/tmap mouselock locks turning by hand until used again");
        b.toggle("Lock Mouse On Mousemat Snap",
                () -> config.farmingMouseLockOnMousemat, v -> config.farmingMouseLockOnMousemat = v);
        b.keybind("  Release Key",
                () -> config.mousematReleaseKey, v -> config.mousematReleaseKey = v).fresh();
        b.toggle("Unlock Mouse On Teleport",
                () -> config.farmingMouseUnlockOnTeleport, v -> config.farmingMouseUnlockOnTeleport = v);
        b.toggle("Mouse Lock HUD",
                () -> config.farmingSensitivityHud, v -> config.farmingSensitivityHud = v);
        b.label("Farm Lane Bar");
        b.toggle("Show Lane Bar",
                () -> config.farmBar, v -> config.farmBar = v);
        b.slider("  Warn Distance", 0f, 20f,
                () -> config.farmWarnDistance, v -> config.farmWarnDistance = v, 0);
        b.soundDropdown("  Warn Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.farmWarnSound, v -> config.farmWarnSound = v);
        b.slider("  Warn Volume", 0f, 4f,
                () -> config.farmWarnVolume, v -> config.farmWarnVolume = v, 2);
        b.toggle("  Lane Per Crop",
                () -> config.farmLanePerCrop, v -> config.farmLanePerCrop = v);
        b.info("Breaking a crop picks its lane · /tmap farm detect: farm two rows to save one");
        b.toggle("  Show Time Until Lane End",
                () -> config.farmLaneTimeLeft, v -> config.farmLaneTimeLeft = v);
        b.slider("  Lane Switch Title (sec, 0 = off)", 0f, 10f,
                () -> (float) config.farmLaneWarnSeconds, v -> config.farmLaneWarnSeconds = Math.round(v), 0);
        b.button("Set Lane Point 1 (here)",
                () -> com.teslamaps.features.FarmProgress.setPoint(1));
        b.button("Set Lane Point 2 (here)",
                () -> com.teslamaps.features.FarmProgress.setPoint(2));
        b.button("Detect Lane (farm two rows)",
                () -> com.teslamaps.features.FarmProgress.toggleDetection());
        b.toggle("Lane Corner Beacons",
                () -> config.farmLaneCorners, v -> config.farmLaneCorners = v);
        b.label("Pest Fortune Buff");
        b.toggle("Warn When Expired",
                () -> config.pestBuffWarning, v -> config.pestBuffWarning = v);
        b.slider("Warn Seconds Before Expiry", 0f, 300f,
                () -> (float) config.philipWarnSeconds,
                v -> config.philipWarnSeconds = (int) (float) v, 0);
        b.soundDropdown("Warning Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.pestBuffSound, v -> config.pestBuffSound = v);
        b.slider("Warning Volume", 0f, 4f,
                () -> config.pestBuffVolume, v -> config.pestBuffVolume = v, 2);
        b.label("Pest Routine");
        b.keybind("Routine Key",
                () -> config.pestRoutineKey, v -> config.pestRoutineKey = v);
        b.dropdown("Pest Chance Loadout", MapConfigScreen.LOADOUT_NAMES,
                () -> config.pestChanceLoadout, v -> config.pestChanceLoadout = v);
        b.dropdown("Rose Drag Loadout", MapConfigScreen.LOADOUT_NAMES,
                () -> config.roseDragLoadout, v -> config.roseDragLoadout = v);
        b.toggle("Resume Attack After Loadout",
                () -> config.pestResumeAttack, v -> config.pestResumeAttack = v);
        b.keybind("Attack Key (empty = vanilla)",
                () -> config.pestAttackKey, v -> config.pestAttackKey = v);
        b.toggle("Show Next Step HUD",
                () -> config.pestRoutineHud, v -> config.pestRoutineHud = v);
        b.slider("Warp Delay", 0.0f, 5.0f,
                () -> config.pestWarpDelay, v -> config.pestWarpDelay = v, 1);
        b.label("Greenhouse Planner");
        b.toggle("Layout Ghost",
                () -> config.greenhousePlanner, v -> config.greenhousePlanner = v);
        b.info("Only active in the Garden · paste a code with /tmap greenhouse <code>");
        b.info("SkyShards / SkyMutations links work too · /tmap greenhouse preset save|load <name>");
        b.dropdown("  Phase",
                new String[]{"Auto", "Floor", "Plants"},
                () -> com.teslamaps.features.GreenhousePlanner.phaseName(config.greenhousePhase),
                v -> config.greenhousePhase = "Floor".equals(v) ? 1 : "Plants".equals(v) ? 2 : 0);
        b.info("Auto shows the floor blocks first, plants once the floor is right");
        b.toggle("  Dim Everything But The Mutation",
                () -> config.greenhouseDimOthers, v -> config.greenhouseDimOthers = v);
        b.info("Rarest plant of the layout · /tmap greenhouse focus <plant|all>");
        b.toggle("  Growth Stage Colors",
                () -> config.greenhouseStages, v -> config.greenhouseStages = v);
        b.info("Red young to green ripe, with the stage over the plant");
        b.slider("    Highest Stage (0 = auto)", 0f, 20f,
                () -> (float) config.greenhouseStageMax, v -> config.greenhouseStageMax = Math.round(v), 0);
        b.toggle("    Stage 1 Is Ripe",
                () -> config.greenhouseStageInverted, v -> config.greenhouseStageInverted = v);
        b.info("/tmap greenhouse stages lists what is actually on the plot");
        b.toggle("  Plant Names",
                () -> config.greenhouseLabels, v -> config.greenhouseLabels = v);
        b.toggle("  Hide Placed Cells",
                () -> config.greenhouseHideDone, v -> config.greenhouseHideDone = v);
        b.toggle("  Through Walls",
                () -> config.greenhouseThroughWalls, v -> config.greenhouseThroughWalls = v);
        b.slider("  Ghost Opacity", 0.05f, 1f,
                () -> config.greenhouseAlpha, v -> config.greenhouseAlpha = v, 2);
        b.slider("  Rotation (x90°)", 0f, 3f,
                () -> (float) config.greenhouseRotation, v -> config.greenhouseRotation = Math.round(v), 0);
        b.toggle("  Mirror",
                () -> config.greenhouseMirror, v -> config.greenhouseMirror = v);
        b.toggle("  Real Plant Models",
                () -> config.greenhouseModels, v -> config.greenhouseModels = v).fresh()
                .desc("Missing plants show as see-through tinted heads of the plant itself instead of a coloured box. "
                        + "Block crops (wheat, carrot, potato, wart, cane) stay boxes.");
        b.button("Manage Presets",
                () -> Minecraft.getInstance().gui.setScreen(new com.teslamaps.screen.GreenhousePresetsScreen(Minecraft.getInstance().gui.screen()))).fresh();
        b.button("Paste Code From Clipboard", () -> {
            String err = com.teslamaps.features.GreenhousePlanner.load(
                    net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard());
            com.teslamaps.features.GreenhousePlanner.rescan();
            net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(err == null
                            ? "§b[TeslaMaps] §fLayout loaded · " + com.teslamaps.features.GreenhousePlanner.status()
                            : "§b[TeslaMaps] §c" + err));
        });
    }
}
