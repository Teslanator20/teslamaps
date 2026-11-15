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
        b.toggle("  Other Players' Catches",
                () -> config.rareCatchAlertOthers, v -> config.rareCatchAlertOthers = v).fresh()
                .desc("\"<Name> NEARBY!\" once when someone else's rare sea creature first shows up near you")
                .tags("nearby", "other", "jawbus", "thunder", "lobby");
        b.toggle("Announce Rare Catch",
                () -> config.rareCatchAnnounce, v -> config.rareCatchAnnounce = v);
        b.info("Sends /pc I caught a <creature>! (double hook noted)");
        b.button("  Per Sea Creature...",
                () -> Minecraft.getInstance().gui.setScreen(new SeaCreatureScreen(Minecraft.getInstance().gui.screen())));
        b.info("Pick alert and announce per creature, overrides Counts As Rare");
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
        b.slider("  World Label Scale", 1f, 4f,
                () -> config.hookWorldLabelScale, v -> config.hookWorldLabelScale = v, 1).fresh()
                .desc("Enlarges Hypixel's countdown over your own bobber. 1 = vanilla size, other bobbers stay as they are")
                .tags("hook", "bobber", "label", "scale", "countdown");
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
        b.toggle("  Mark Unknown Hotspot",
                () -> config.hotspotRadarUnknown, v -> config.hotspotRadarUnknown = v).fresh()
                .desc("Red label when you get close and no HOTSPOT stand is at the guess");

        b.label("Hotspot Sharing");
        b.toggle("Share Hotspots To Party",
                () -> config.hotspotShare, v -> config.hotspotShare = v);
        b.info("Crimson Isle / Backwater Bayou, needs a rod in hand");
        b.channel("  Channel",
                () -> config.hotspotShareChannel, v -> config.hotspotShareChannel = v);
        b.toggle("  Ask Before Sharing",
                () -> config.hotspotShareManual, v -> config.hotspotShareManual = v);
        b.info("Chat line with a [Share] button instead of sending");
        b.toggle("  x: y: z: Format",
                () -> config.hotspotShareXyz, v -> config.hotspotShareXyz = v);
        b.info("x: 1, y: 2, z: 3 | <perk>, other mods read it too");
        b.toggle("  Share Safari Hunting Hotspot",
                () -> config.safariHotspotPost, v -> config.safariHotspotPost = v).fresh()
                .desc("Critter Safari: posts \"My hotspot: <biome>\" when Hypixel tells you your Hunting Hotspot");
        b.channel("    Safari Channel",
                () -> config.safariHotspotPostChannel, v -> config.safariHotspotPostChannel = v).fresh();
        b.toggle("All Fishing Islands",
                () -> config.hotspotShareAllIslands, v -> config.hotspotShareAllIslands = v);
        b.info("Sharing, found alert and waypoints outside the two islands");
        b.toggle("Only Selected Perks",
                () -> config.hotspotShareFilter, v -> config.hotspotShareFilter = v);
        b.info("Filters sharing and the found alert");
        b.toggle("  Sea Creature Chance",
                () -> config.hotspotPerkSeaCreature, v -> config.hotspotPerkSeaCreature = v);
        b.toggle("  Fishing Speed",
                () -> config.hotspotPerkFishingSpeed, v -> config.hotspotPerkFishingSpeed = v);
        b.toggle("  Double Hook Chance",
                () -> config.hotspotPerkDoubleHook, v -> config.hotspotPerkDoubleHook = v);
        b.toggle("  Treasure Chance",
                () -> config.hotspotPerkTreasure, v -> config.hotspotPerkTreasure = v);
        b.toggle("  Trophy Chance",
                () -> config.hotspotPerkTrophy, v -> config.hotspotPerkTrophy = v);
        b.toggle("  Shard Chance",
                () -> config.hotspotPerkShard, v -> config.hotspotPerkShard = v);
        b.toggle("Hotspot Found Alert",
                () -> config.hotspotFoundAlert, v -> config.hotspotFoundAlert = v);
        b.info("Title, sound and a 30s waypoint when a new hotspot shows up");
        b.toggle("Waypoints For Shared Hotspots",
                () -> config.hotspotShareWaypoints, v -> config.hotspotShareWaypoints = v);
        b.toggle("  Tracer To Hotspot Waypoints",
                () -> config.hotspotShareTracer, v -> config.hotspotShareTracer = v);
        b.toggle("Hotspot Closed Title",
                () -> config.hotspotClosedTitle, v -> config.hotspotClosedTitle = v);
        b.toggle("Hotspot Circle",
                () -> config.hotspotCircle, v -> config.hotspotCircle = v).fresh()
                .desc("Exact hotspot radius as a circle on the water/lava, colored by perk")
                .tags("radius", "area", "perk");
        b.toggle("  Filled",
                () -> config.hotspotCircleFill, v -> config.hotspotCircleFill = v).fresh();
        b.slider("  Fill Opacity", 0f, 1f,
                () -> config.hotspotCircleAlpha, v -> config.hotspotCircleAlpha = v, 2).fresh();
        b.toggle("  Rim",
                () -> config.hotspotCircleRim, v -> config.hotspotCircleRim = v).fresh();
        b.toggle("  Hide Hotspot Particles",
                () -> config.hotspotCircleHideParticles, v -> config.hotspotCircleHideParticles = v).fresh()
                .desc("Drops Hypixel's hotspot edge particles once the circle replaces them");

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
        b.color("  Color",
                () -> config.colorBobberTimer, v -> config.colorBobberTimer = v);
        b.toggle("  20s Threshold",
                () -> config.bobberTimerThreshold, v -> config.bobberTimerThreshold = v);
        b.info("Switches color once the sea creature window is over");
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
        b.toggle("  Crimson Isle Only",
                () -> config.translucentLavaCrimsonOnly, v -> config.translucentLavaCrimsonOnly = v).fresh()
                .desc("Lava stays normal everywhere else (Dungeons, Kuudra). /tmap lava toggles the feature")
                .tags("lava", "crimson", "area", "command");
    }
}
