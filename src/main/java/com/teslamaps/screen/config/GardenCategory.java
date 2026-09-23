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
        b.toggle("Close Inventory On Teleport",
                () -> config.gardenPlotsCloseOnClick, v -> config.gardenPlotsCloseOnClick = v);
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
        b.label("Harvest Feast");
        b.toggle("In-Season Display",
                () -> config.feastSeasonDisplay, v -> config.feastSeasonDisplay = v);
        b.toggle("  Show Outside The Garden",
                () -> config.feastSeasonEverywhere, v -> config.feastSeasonEverywhere = v);
        b.info("Open Feast Chef Ted > All Crops once to fill it in");
        b.label("Pests");
        b.info("Pest ESP moved to Mob Visuals - it tells the 15 species apart");
        b.button("Pest Visuals (Mob Visuals)",
                () -> Minecraft.getInstance().setScreen(new MobVisualScreen(b.screen())));
        b.label("Pest Tracker");
        b.toggle("Pest Overlay",
                () -> config.pestOverlay, v -> config.pestOverlay = v);
        b.toggle("Cooldown Arms Routine",
                () -> config.pestCooldownArmsRoutine, v -> config.pestCooldownArmsRoutine = v);
        b.slider("Profit Idle After (sec)", 30f, 600f,
                () -> (float) config.farmIdleSeconds,
                v -> config.farmIdleSeconds = (int) (float) v, 0);
        b.toggle("Remind: Pest Chance Gear",
                () -> config.pestGearReminder, v -> config.pestGearReminder = v);
        b.toggle("Remind: Teleport To Pests",
                () -> config.pestTpReminder, v -> config.pestTpReminder = v);
        b.toggle("Custom Cooldown",
                () -> config.pestCustomCooldown, v -> config.pestCustomCooldown = v);
        b.slider("Custom Cooldown (sec)", 30f, 600f,
                () -> (float) config.pestCustomCooldownSeconds,
                v -> config.pestCustomCooldownSeconds = (int) (float) v, 0);
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
        b.label("Spray");
        b.toggle("Queue Trip When Spray Runs Out",
                () -> config.sprayRoutine, v -> config.sprayRoutine = v);
        b.label("Round Timer");
        b.toggle("Pest Round Timer",
                () -> config.pestTimer, v -> config.pestTimer = v);
        b.button("Reset Pest PB",
                () -> com.teslamaps.features.PestTimer.resetPersonalBest());
        b.label("Profit");
        b.toggle("Profit Tracker",
                () -> config.farmProfit, v -> config.farmProfit = v);
        b.slider("Items Listed", 0f, 12f,
                () -> (float) config.farmProfitRows, v -> config.farmProfitRows = (int) (float) v, 0);
        b.dropdown("Price Basis",
                new String[]{"Instant Sell", "Sell Offer", "NPC"},
                () -> config.farmPriceMode, v -> config.farmPriceMode = v);
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
        b.label("Farm Lane Bar");
        b.toggle("Show Lane Bar",
                () -> config.farmBar, v -> config.farmBar = v);
        b.slider("Warn Distance", 0f, 20f,
                () -> config.farmWarnDistance, v -> config.farmWarnDistance = v, 0);
        b.soundDropdown("Warn Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.farmWarnSound, v -> config.farmWarnSound = v);
        b.slider("Warn Volume", 0f, 4f,
                () -> config.farmWarnVolume, v -> config.farmWarnVolume = v, 2);
        b.button("Set Lane Point 1 (here)",
                () -> com.teslamaps.features.FarmProgress.setPoint(1));
        b.button("Set Lane Point 2 (here)",
                () -> com.teslamaps.features.FarmProgress.setPoint(2));
        b.label("Pest Fortune Buff");
        b.toggle("Warn When Expired",
                () -> config.pestBuffWarning, v -> config.pestBuffWarning = v);
        b.slider("Warn Seconds Before Expiry", 0f, 300f,
                () -> (float) config.philipWarnSeconds,
                v -> config.philipWarnSeconds = (int) (float) v, 0);
        b.toggle("Expiry Queues Philip Step",
                () -> config.philipArmsRoutine, v -> config.philipArmsRoutine = v);
        b.soundDropdown("Warning Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.pestBuffSound, v -> config.pestBuffSound = v);
        b.slider("Warning Volume", 0f, 4f,
                () -> config.pestBuffVolume, v -> config.pestBuffVolume = v, 2);
        b.label("Pest Routine");
        b.keybind("Routine Key",
                () -> config.pestRoutineKey, v -> config.pestRoutineKey = v);
        b.keybind("Dump Open Menu Key",
                () -> config.dumpGuiKey, v -> config.dumpGuiKey = v);
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
