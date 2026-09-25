package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class CritterSafariCategory {

    private CritterSafariCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Run Tracker");
        b.toggle("Track Run", () -> config.safariTracker, v -> config.safariTracker = v);
        b.toggle("Show HUD", () -> config.safariHud, v -> config.safariHud = v);
        b.toggle("  Panel Style", () -> config.safariHudPanel, v -> config.safariHudPanel = v);
        b.info("Bars per biome, players, stays up as \"ready\" / \"last run\" at the entrance");
        b.info("Position and scale in /tmap gui");
        b.button("Run Screen (/tmap safari gui)",
                () -> Minecraft.getInstance().setScreen(new com.teslamaps.screen.SafariScreen(Minecraft.getInstance().screen)));
        b.label("Missing Panel");
        b.toggle("Missing Panel", () -> config.safariMissingPanel, v -> config.safariMissingPanel = v);
        b.info("What the biome you stand in still needs: quota, tries, walls");
        b.toggle("  Count Critters In Sight", () -> config.safariMissingNear, v -> config.safariMissingNear = v);
        b.info("\"n near\" only counts critters with a free line of sight");
        b.label("Checklist");
        b.toggle("Show Checklist", () -> config.safariChecklist, v -> config.safariChecklist = v);
        b.dropdown("  Biome",
                new String[] { "Auto", "Cavern", "Forest", "Haunted", "Icy" },
                () -> config.safariChecklistBiome, v -> config.safariChecklistBiome = v);
        b.toggle("  Check/Cross Icons", () -> config.safariChecklistIcons, v -> config.safariChecklistIcons = v);
        b.info("Green = caught, red = escaped, grey = not seen");
        b.label("Alerts");
        b.toggle("Encounter Banners", () -> config.safariEncounterBanner, v -> config.safariEncounterBanner = v);
        b.info("Gemzie / Wumpa / Doomspiral: READY, STARTED, DONE on screen");
        b.toggle("  Gemzie", () -> config.safariBannerGemzie, v -> config.safariBannerGemzie = v);
        b.toggle("  Wumpa", () -> config.safariBannerWumpa, v -> config.safariBannerWumpa = v);
        b.toggle("  Doomspiral", () -> config.safariBannerDoomspiral, v -> config.safariBannerDoomspiral = v);
        b.toggle("  Only In Its Biome", () -> config.safariBannerBiomeOnly, v -> config.safariBannerBiomeOnly = v);
        b.channel("  Post Stages To", () -> config.safariBannerChannel, v -> config.safariBannerChannel = v);
        b.toggle("Sparkling Alert", () -> config.safariSparklingAlert, v -> config.safariSparklingAlert = v);
        b.channel("  Sparkling Announce", () -> config.safariSparklingChannel, v -> config.safariSparklingChannel = v);
        b.toggle("  Full Screen Alert", () -> config.safariSparklingScreen, v -> config.safariSparklingScreen = v);
        b.info("Screen wash with SPARKLING!, the critter, biome and coords, 7s");
        b.toggle("  Only When Visible", () -> config.safariSparklingVisible, v -> config.safariSparklingVisible = v);
        b.toggle("Force Sparkle Particles", () -> config.safariSparkleForce, v -> config.safariSparkleForce = v);
        b.info("Sparkles render at any distance and particle setting");
        b.toggle("Announce Biome Done", () -> config.safariAnnounceBiomeDone, v -> config.safariAnnounceBiomeDone = v);
        b.toggle("Announce Unique Done", () -> config.safariAnnounceUniqueDone, v -> config.safariAnnounceUniqueDone = v);
        b.channel("Share Time", () -> config.safariShareTimeChannel, v -> config.safariShareTimeChannel = v);
        b.toggle("Warn If Not Full", () -> config.safariWarnNotFull, v -> config.safariWarnNotFull = v);
        b.info("Screen warning + sound when fewer than 4 players are in");
        b.slider("Alert Volume", 0.0f, 3.0f,
                () -> config.safariAlertVolume, v -> config.safariAlertVolume = v);
        b.button("  Test Alerts (/tmap safari testalert)", com.teslamaps.safari.SafariBanners::testAll)
                .desc("Plays every banner stage, milestone and the sparkling screen once");
        b.label("Quality of Life");
        b.toggle("Hide Cold Overlay (Icy)", () -> config.safariHideCold, v -> config.safariHideCold = v);
        b.toggle("Auto Accept Hideyho", () -> config.safariAutoHideyho, v -> config.safariAutoHideyho = v);
        b.slider("  Min Delay (ms)", 0f, 2000f,
                () -> (float) config.safariAutoHideyhoMinMs, v -> config.safariAutoHideyhoMinMs = (int) (float) v, 0);
        b.slider("  Extra Random (ms)", 0f, 2000f,
                () -> (float) config.safariAutoHideyhoJitterMs, v -> config.safariAutoHideyhoJitterMs = (int) (float) v, 0);
        b.toggle("Show PB in HUD", () -> config.safariShowPb, v -> config.safariShowPb = v);
        b.toggle("Show Hotspot in HUD", () -> config.safariShowHotspot, v -> config.safariShowHotspot = v);
        b.toggle("Show Head Start in HUD", () -> config.safariShowHeadStart, v -> config.safariShowHeadStart = v);
        b.info("Run history: /tmap safari");
        b.label("ESP");
        b.toggle("Critter ESP (Safari)", () -> config.safariCritterESP, v -> config.safariCritterESP = v);
        b.toggle("  Only Uncaught", () -> config.safariOnlyMissing, v -> config.safariOnlyMissing = v);
        b.toggle("  Name Over Every Critter", () -> config.safariNames, v -> config.safariNames = v);
        b.toggle("  Tracers To Named", () -> config.safariTracers, v -> config.safariTracers = v);
        b.toggle("  Tracer To Hideyho", () -> config.safariHideyhoTracer, v -> config.safariHideyhoTracer = v);
        b.toggle("Filled Boxes", () -> config.safariFilled, v -> config.safariFilled = v);
        b.toggle("Legit Mode (Depth Check)", () -> config.safariLegitMode, v -> config.safariLegitMode = v);
        b.info("Nothing drawn through walls");
        b.toggle("Bee Nest ESP", () -> config.safariNestESP, v -> config.safariNestESP = v);
        b.toggle("Floor Spot ESP", () -> config.critterSpotESP, v -> config.critterSpotESP = v);
        b.toggle("Recatch Spot", () -> config.safariRecatchSpot, v -> config.safariRecatchSpot = v);
        b.info("Marks where an escaped critter comes back");
        b.toggle("Hidden Critter Walls", () -> config.safariWallMarkers, v -> config.safariWallMarkers = v);
        b.info("Unbroken Snoozle / Troodon walls, while uncaught");
        b.toggle("  Broken Only Once Seen", () -> config.safariWallSight, v -> config.safariWallSight = v);
        b.info("A wall a partymate broke stays marked until you look at the spot");
        b.toggle("  Labels With Distance", () -> config.safariWallLabels, v -> config.safariWallLabels = v);
        b.color("Sparkling Color", () -> config.colorSafariSparkling, v -> config.colorSafariSparkling = v);
        b.color("Bee Nest Color", () -> config.colorSafariNest, v -> config.colorSafariNest = v);
        b.color("Floor Spot Color", () -> config.colorCritterSpot, v -> config.colorCritterSpot = v);
        b.color("Recatch Spot Color", () -> config.colorSafariRecatch, v -> config.colorSafariRecatch = v);
        b.color("Wall Color", () -> config.colorSafariWall, v -> config.colorSafariWall = v);
        b.color("Troodon Wall Color",
                () -> config.colorSafariWallTroodon != null ? config.colorSafariWallTroodon : config.colorSafariWall,
                v -> config.colorSafariWallTroodon = v);
        b.info("Hideyho and legendaries always get a name label");
        b.info("Critter boxes are coloured by shard rarity");
    }
}
