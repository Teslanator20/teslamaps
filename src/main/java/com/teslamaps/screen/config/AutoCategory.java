package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class AutoCategory {

    private AutoCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Auto GFS");
        b.toggle("Enable Auto GFS", () -> config.autoGFS, v -> config.autoGFS = v);
        b.label("Triggers");
        b.toggle("Refill on Dungeon Start", () -> config.autoGFSOnStart, v -> config.autoGFSOnStart = v);
        b.toggle("Refill on Timer", () -> config.autoGFSTimer, v -> config.autoGFSTimer = v);
        b.slider("  Timer Interval", 5f, 120f,
                () -> (float) config.autoGFSInterval, v -> config.autoGFSInterval = v.intValue());
        b.toggle("Dungeon Only", () -> config.autoGFSDungeonOnly, v -> config.autoGFSDungeonOnly = v);
        b.toggle("  Also in Kuudra", () -> config.autoGFSKuudra, v -> config.autoGFSKuudra = v).fresh();
        b.toggle("Skip While Dead", () -> config.autoGFSSkipDead, v -> config.autoGFSSkipDead = v).fresh();
        b.slider("Min Missing Before Refill", 1f, 16f,
                () -> (float) config.autoGFSMinMissing, v -> config.autoGFSMinMissing = Math.round(v), 0).fresh()
                .desc("Pearls, Jerry and Superboom only refill once at least this many are missing");
        b.label("Items to Refill");
        b.toggle("Ender Pearls", () -> config.autoGFSPearls, v -> config.autoGFSPearls = v);
        b.toggle("Inflatable Jerry", () -> config.autoGFSJerry, v -> config.autoGFSJerry = v);
        b.toggle("Superboom TNT", () -> config.autoGFSTNT, v -> config.autoGFSTNT = v);
        b.toggle("Spirit Leaps", () -> config.autoGFSLeaps, v -> config.autoGFSLeaps = v).fresh()
                .desc("Up to 16, only once 4 or more are missing");
        b.toggle("Twilight Arrow Poison (M7)", () -> config.autoGFSTwilight, v -> config.autoGFSTwilight = v).fresh()
                .desc("Archer after Storm's lightning, Archer/Berserk at the Core, others at Wither King P5");
        b.slider("  Twilight Amount", 4f, 8f,
                () -> (float) config.autoGFSTwilightAmount, v -> config.autoGFSTwilightAmount = Math.round(v), 0).fresh();
        b.keybind("Pearl Refill Key (next 16)", () -> config.autoGFSPearlKey, v -> config.autoGFSPearlKey = v).fresh();
        b.info("Also /tmap ep, ij, sl, sb, dd, tap, twap [amount] (fills up to the amount)").fresh();
        b.label("Get From Sacks");
        b.toggle("Queued GFS", () -> config.gfsQueued, v -> config.gfsQueued = v);
        b.info("Spaces /gfs out so Hypixel stops dropping them");
        if (com.teslamaps.features.GetFromSacks.SKYHANNI) {
            b.info("§cInactive: SkyHanni handles /gfs");
        }
        b.slider("  Default Amount", 1f, 64f,
                () -> (float) config.gfsDefaultAmount, v -> config.gfsDefaultAmount = v.intValue());
        b.keybind("GFS Key (over a slot)",
                () -> config.gfsKeybind, v -> config.gfsKeybind = v);
        b.toggle("Tab-Complete Sack Items", () -> config.gfsSuggestions, v -> config.gfsSuggestions = v);
        b.toggle("Bazaar Fallback", () -> config.gfsBazaar, v -> config.gfsBazaar = v);
        b.toggle("  Mark Item On Bazaar Page", () -> config.bzPurchaseHelper, v -> config.bzPurchaseHelper = v).fresh()
                .desc("Marks the searched item green on the /bz page until you buy or sell it");
        b.info("Offers the missing rest from the bazaar");
        b.toggle("Supercraft Grab Link", () -> config.gfsSuperCraft, v -> config.gfsSuperCraft = v);
        b.toggle("Piggy Bank Grab Link", () -> config.gfsPiggyBank, v -> config.gfsPiggyBank = v).fresh()
                .desc("Clickable /gfs for 8 Enchanted Pork when your piggy bank cracks or breaks on death");

        b.label("Special");
        b.toggle("Auto Draft on Puzzle Fail", () -> config.autoGFSDraft, v -> config.autoGFSDraft = v);
        b.dropdown("  Draft Mode", com.teslamaps.features.AutoGFS.DRAFT_MODES,
                () -> config.draftMode, v -> config.draftMode = v).fresh()
                .desc("Click in Chat: a clickable line instead of the automatic /gfs, also works in Legit Mode");
        b.toggle("  Also on Party Fails", () -> config.draftPartyFails, v -> config.draftPartyFails = v).fresh();
        b.toggle("  Draft Alert Title", () -> config.autoGFSDraftAlert, v -> config.autoGFSDraftAlert = v);
        b.toggle("  Announce Draft Reset (/pc)", () -> config.autoGFSDraftAnnounce, v -> config.autoGFSDraftAnnounce = v).fresh();
        b.label("Auto Wish");
        b.toggle("Auto Wish (Healer Ult)", () -> config.autoWish, v -> config.autoWish = v);
        b.toggle("Last Breath Pull Sound", () -> config.lastBreathSound, v -> config.lastBreathSound = v);
        b.toggle("  Charge HUD (LB: ticks)", () -> config.lastBreathHud, v -> config.lastBreathHud = v).fresh();
        b.toggle("  Target Per Dragon (M7 P5)", () -> config.lastBreathPerDragon, v -> config.lastBreathPerDragon = v).fresh()
                .desc("Target ticks follow the dragon spawn box you look at; elsewhere the global threshold applies.");
        b.slider("    Red", 0f, 21f, () -> (float) config.lastBreathRed, v -> config.lastBreathRed = Math.round(v), 0).fresh();
        b.slider("    Orange", 0f, 21f, () -> (float) config.lastBreathOrange, v -> config.lastBreathOrange = Math.round(v), 0).fresh();
        b.slider("    Green", 0f, 21f, () -> (float) config.lastBreathGreen, v -> config.lastBreathGreen = Math.round(v), 0).fresh();
        b.slider("    Blue", 0f, 21f, () -> (float) config.lastBreathBlue, v -> config.lastBreathBlue = Math.round(v), 0).fresh();
        b.slider("    Purple", 0f, 21f, () -> (float) config.lastBreathPurple, v -> config.lastBreathPurple = Math.round(v), 0).fresh();
        b.label("Chests");
        b.toggle("Auto Close Chests", () -> config.autoCloseChests, v -> config.autoCloseChests = v);
        b.slider("  Auto Close Delay (ticks)", 0, 20, () -> (float) config.autoCloseDelay, v -> config.autoCloseDelay = v.intValue());
        b.slider("  Randomization (ticks)", 0, 10, () -> (float) config.autoCloseRandomization, v -> config.autoCloseRandomization = v.intValue());
        b.toggle("Close Chest On Input", () -> config.closeChestOnInput, v -> config.closeChestOnInput = v);
        b.label("Movement");
        b.toggle("Auto Sprint", () -> config.autoSprint, v -> config.autoSprint = v);
        b.toggle("  In Garden", () -> config.autoSprintGarden, v -> config.autoSprintGarden = v).fresh()
                .desc("Off = no auto sprint on the Garden, most crops don't need it");
        b.toggle("  Pause With Farming Tool", () -> config.autoSprintFarmTool, v -> config.autoSprintFarmTool = v).fresh()
                .desc("No auto sprint while a hoe, dicer or other farming tool is in your hand");
    }
}
