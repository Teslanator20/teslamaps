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
        b.label("Items to Refill");
        b.toggle("Ender Pearls", () -> config.autoGFSPearls, v -> config.autoGFSPearls = v);
        b.toggle("Inflatable Jerry", () -> config.autoGFSJerry, v -> config.autoGFSJerry = v);
        b.toggle("Superboom TNT", () -> config.autoGFSTNT, v -> config.autoGFSTNT = v);
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
        b.info("Offers the missing rest from the bazaar");
        b.toggle("Supercraft Grab Link", () -> config.gfsSuperCraft, v -> config.gfsSuperCraft = v);

        b.label("Special");
        b.toggle("Auto Draft on Puzzle Fail", () -> config.autoGFSDraft, v -> config.autoGFSDraft = v);
        b.toggle("Draft Alert Title", () -> config.autoGFSDraftAlert, v -> config.autoGFSDraftAlert = v);
        b.label("Auto Wish");
        b.toggle("Auto Wish (Healer Ult)", () -> config.autoWish, v -> config.autoWish = v);
        b.toggle("Last Breath Pull Sound", () -> config.lastBreathSound, v -> config.lastBreathSound = v);
        b.label("Chests");
        b.toggle("Auto Close Chests", () -> config.autoCloseChests, v -> config.autoCloseChests = v);
        b.slider("  Auto Close Delay (ticks)", 0, 20, () -> (float) config.autoCloseDelay, v -> config.autoCloseDelay = v.intValue());
        b.slider("  Randomization (ticks)", 0, 10, () -> (float) config.autoCloseRandomization, v -> config.autoCloseRandomization = v.intValue());
        b.toggle("  Close Chest On Input", () -> config.closeChestOnInput, v -> config.closeChestOnInput = v);
        b.label("Movement");
        b.toggle("Auto Sprint", () -> config.autoSprint, v -> config.autoSprint = v);
    }
}
