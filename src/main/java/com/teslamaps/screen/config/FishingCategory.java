package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class FishingCategory {

    private FishingCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Rare Sea Creatures");
        b.toggle("Rare Catch Warning",
                () -> config.rareCatchAlert, v -> config.rareCatchAlert = v);
        b.info("Title and sound when you catch a rare one");
        b.dropdown("  Counts As Rare",
                new String[] { "Rare Flag", "Epic+", "Rare+" },
                () -> config.rareCatchRarity, v -> config.rareCatchRarity = v);
        b.toggle("  Show Creature Name",
                () -> config.rareCatchName, v -> config.rareCatchName = v);
        b.soundDropdown("  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.rareCatchSound, v -> config.rareCatchSound = v);
        b.slider("  Volume", 0f, 4f,
                () -> config.rareCatchVolume, v -> config.rareCatchVolume = v, 2);
        b.toggle("Announce Rare Catch",
                () -> config.rareCatchAnnounce, v -> config.rareCatchAnnounce = v);
        b.info("Sends /pc I caught a <creature>! (double hook noted)");
        b.toggle("Misclick Guard",
                () -> config.rareCatchKeySwap, v -> config.rareCatchKeySwap = v);
        b.info("After a Drowned Captain / Magma Pillar catch, Attack = O and Use = I briefly - mouse does nothing");
        b.slider("  Guard Duration (s)", 0f, 5f,
                () -> config.rareCatchKeySwapSeconds, v -> config.rareCatchKeySwapSeconds = v, 2);
        b.toggle("  Also On Party Catches",
                () -> config.rareCatchKeySwapAll, v -> config.rareCatchKeySwapAll = v);

        b.label("Hook Timer");
        b.toggle("Hook Countdown",
                () -> config.hookDisplay, v -> config.hookDisplay = v);
        b.info("Hypixel's 3.0 / 2.0 / !!! over the bobber, big");
        b.toggle("  Hide Hypixel's Label",
                () -> config.hookHideArmorStand, v -> config.hookHideArmorStand = v);
        b.soundDropdown("  Alert Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.hookAlertSound, v -> config.hookAlertSound = v);
        b.slider("  Alert Volume", 0f, 4f,
                () -> config.hookAlertVolume, v -> config.hookAlertVolume = v, 2);
        b.slider("  Alert Pitch", 0.5f, 2f,
                () -> config.hookAlertPitch, v -> config.hookAlertPitch = v, 2);
        b.info("Volume 0 = no sound. Fires once when !!! appears");
        b.info("Position and scale in /tmap gui");

        b.label("Hotspot Radar");
        b.toggle("Guess Hotspot",
                () -> config.hotspotRadar, v -> config.hotspotRadar = v);
        b.info("Reads the radar's particle trail, shows the target");
        b.toggle("  Line To Hotspot",
                () -> config.hotspotLine, v -> config.hotspotLine = v);
        b.toggle("  Beacon Beam",
                () -> config.hotspotBeam, v -> config.hotspotBeam = v);
        b.color("  Color",
                () -> config.colorHotspot, v -> config.colorHotspot = v);

        b.label("Hotspot Sharing");
        b.toggle("Share Hotspots To Party",
                () -> config.hotspotShare, v -> config.hotspotShare = v);
        b.info("Crimson Isle / Backwater Bayou, needs a rod in hand");
        b.toggle("Waypoints For Shared Hotspots",
                () -> config.hotspotShareWaypoints, v -> config.hotspotShareWaypoints = v);
        b.toggle("Hotspot Closed Title",
                () -> config.hotspotClosedTitle, v -> config.hotspotClosedTitle = v);
        b.label("Hotspot Gone");
        b.toggle("Warn When Hotspot Ends",
                () -> config.hotspotGoneWarning, v -> config.hotspotGoneWarning = v);
        b.info("Fires when the whirlpool you stand at stops");
        b.toggle("  Show Title",
                () -> config.hotspotGoneTitle, v -> config.hotspotGoneTitle = v);
        b.soundDropdown("  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.hotspotGoneSound, v -> config.hotspotGoneSound = v);
        b.slider("  Volume", 0f, 4f,
                () -> config.hotspotGoneVolume, v -> config.hotspotGoneVolume = v, 2);

        b.label("Exploding Frog");
        b.toggle("Exploding Frog ESP",
                () -> config.explodingFrogESP, v -> config.explodingFrogESP = v);
        b.info("Boxes the lily pad the frog leaves behind");
        b.toggle("  Tracer",
                () -> config.explodingFrogTracer, v -> config.explodingFrogTracer = v);
        b.toggle("  Show Name",
                () -> config.explodingFrogName, v -> config.explodingFrogName = v);
        b.color("  Color",
                () -> config.colorExplodingFrog, v -> config.colorExplodingFrog = v);

        b.label("Bobber Timer");
        b.toggle("Bobber Timer",
                () -> config.bobberTimer, v -> config.bobberTimer = v);
        b.toggle("  Start on Liquid Touch",
                () -> config.bobberTimerOnLiquid, v -> config.bobberTimerOnLiquid = v);
        b.info("Off = the timer starts when you cast");
        b.toggle("  Show Over Bobber",
                () -> config.bobberTimerWorld, v -> config.bobberTimerWorld = v);
        b.toggle("  20s Threshold",
                () -> config.bobberTimerThreshold, v -> config.bobberTimerThreshold = v);
        b.info("Switches color once the sea creature window is over");
        b.color("    Color",
                () -> config.colorBobberTimer, v -> config.colorBobberTimer = v);
        b.color("    Color After",
                () -> config.colorBobberTimerLate, v -> config.colorBobberTimerLate = v);
        b.toggle("    Ping",
                () -> config.bobberTimerPing, v -> config.bobberTimerPing = v);
        b.toggle("    Slug Pet Check",
                () -> config.bobberTimerSlug, v -> config.bobberTimerSlug = v);
        b.info("-0.5% per Slug level, needs the Pet Overlay on");
        b.info("Position and scale in /tmap gui");
        b.label("Umberella");
        b.toggle("Expiry Warning",
                () -> config.umbrellaWarning, v -> config.umbrellaWarning = v);
        b.slider("  Warn Seconds Before", 0f, 120f,
                () -> (float) config.umbrellaWarnBefore, v -> config.umbrellaWarnBefore = v.intValue());
        b.toggle("  Show Title",
                () -> config.umbrellaWarningTitle, v -> config.umbrellaWarningTitle = v);
        b.soundDropdown("  Sound",
                com.teslamaps.utils.SoundOptions.keys(),
                () -> config.umbrellaWarningSound, v -> config.umbrellaWarningSound = v);
        b.slider("  Volume", 0f, 4f,
                () -> config.umbrellaWarningVolume, v -> config.umbrellaWarningVolume = v, 2);
        b.label("Liquids");
        b.toggle("Translucent Lava",
                () -> config.translucentLava, v -> config.translucentLava = v);
        b.color("  Lava Tint",
                () -> config.colorTranslucentLava, v -> config.colorTranslucentLava = v);
        b.toggle("Tinted Water",
                () -> config.tintedWater, v -> config.tintedWater = v);
        b.color("  Water Tint",
                () -> config.colorTintedWater, v -> config.colorTintedWater = v);
    }
}
