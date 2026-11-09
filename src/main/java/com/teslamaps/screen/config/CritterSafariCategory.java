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
        b.dropdown("  Show Where", com.teslamaps.safari.SafariTracker.SHOW_WHERE,
                () -> config.safariShowWhere, v -> config.safariShowWhere = v).fresh()
                .desc("Auto: panel in the Safari and at its entrance, line HUD only during a run. Otherwise the HUD also shows \"ready\" / \"last run\" where chosen.")
                .tags("scope", "entrance", "last run");
        b.lineOrder("  HUD Lines (drag to reorder)", com.teslamaps.safari.SafariTracker.LINES.id()).fresh()
                .desc("Drag lines to change their order, click the box to hide one. Reset restores the default.");
        b.info("Bars per biome, players, stays up as \"ready\" / \"last run\" at the entrance");
        b.info("Position and scale in /tmap gui");
        b.button("Run Screen (/tmap safari gui)",
                () -> Minecraft.getInstance().gui.setScreen(new com.teslamaps.gui.screen().SafariScreen(Minecraft.getInstance().gui.screen())));
        b.button("  Import Old Runs From Logs", com.teslamaps.safari.SafariLogImport::start).fresh()
                .desc("Rebuilds past runs from latest.log and the .log.gz files, fills per-critter data into saved runs")
                .tags("import", "history", "logs");
        b.info("Per-critter stats (own, tries, shards, never caught): /tmap safari species").fresh();
        b.toggle("  Shard Value", () -> config.safariShardValue, v -> config.safariShardValue = v).fresh()
                .desc("Values the shards you got (captures + loot share) at the bazaar: Value line and coins/h in the HUD, coins per run, total and average in /tmap safari. Top species: /tmap safari prices")
                .tags("shard", "value", "profit", "coins", "bazaar");
        b.dropdown("    Price", com.teslamaps.safari.SafariShardValue.MODES,
                () -> config.safariShardPrice, v -> config.safariShardPrice = v).fresh()
                .desc("Instant Sell = best buy order, Sell Offer = cheapest sell offer, Both shows the two");
        b.toggle("    Minus Bazaar Tax (1.25%)", () -> config.safariShardTax, v -> config.safariShardTax = v).fresh();
        b.toggle("  Per-Player Breakdown At Run End", () -> config.safariPlayersOnEnd, v -> config.safariPlayersOnEnd = v).fresh()
                .desc("Who caught what: unique critters per biome and the species per player, green = you have it too, aqua = only them. Also /tmap safari players.")
                .tags("players", "split", "biome");
        b.toggle("  Compact Reward Summary", () -> config.safariSummaryLine, v -> config.safariSummaryLine = v).fresh()
                .desc("Hides the SAFARI REWARD SUMMARY block and posts one line instead: SAFARI +14 Shards · +80 Essence · +24.4k Hunting Exp")
                .tags("chat", "compact", "summary", "reward");
        b.label("Missing Panel");
        b.toggle("Missing Panel", () -> config.safariMissingPanel, v -> config.safariMissingPanel = v);
        b.info("What the biome you stand in still needs: quota, tries, walls");
        b.toggle("  Count Critters In Sight", () -> config.safariMissingNear, v -> config.safariMissingNear = v);
        b.info("\"n near\" only counts critters with a free line of sight");
        b.toggle("  Tries And Escapes Per Critter", () -> config.safariAttemptCounts, v -> config.safariAttemptCounts = v).fresh()
                .desc("Uncaught critters show \"3 tried, 2 esc\" here, \"3t 2e\" in the run screen and the checklist.");
        b.toggle("  Mounds Broken Counter", () -> config.safariMoundCounter, v -> config.safariMoundCounter = v).fresh()
                .desc("Cavern: \"Mounds broken 7 → 1 Rockmite\" for this run").tags("mound", "rockmite");
        b.label("Checklist");
        b.toggle("Show Checklist", () -> config.safariChecklist, v -> config.safariChecklist = v);
        b.dropdown("  Biome",
                new String[] { "Auto", "Cavern", "Forest", "Haunted", "Icy", "All" },
                () -> config.safariChecklistBiome, v -> config.safariChecklistBiome = v);
        b.toggle("  Check/Cross Icons", () -> config.safariChecklistIcons, v -> config.safariChecklistIcons = v);
        b.toggle("  Spawn Quotas", () -> config.safariQuotas, v -> config.safariQuotas = v).fresh()
                .desc("Gemzie 3, Troodon 3, Gazer 4: only done once every spawn is caught. Checklist shows 1/3, biome bars, Biome Done alert and the missing line follow it.");
        b.info("Green = caught, red = escaped, grey = not seen");
        b.button("  Copy Missing List", com.teslamaps.safari.SafariTracker::copyMissing).fresh();
        b.button("  Share Missing List", com.teslamaps.safari.SafariTracker::shareMissing).fresh();
        b.channel("  Share Missing To", () -> config.safariMissingChannel, v -> config.safariMissingChannel = v).fresh();
        b.info("  One line per biome, also /tmap safari missing|copy|share").fresh();
        b.label("Alerts");
        b.toggle("Encounter Banners", () -> config.safariEncounterBanner, v -> config.safariEncounterBanner = v);
        b.info("Gemzie / Wumpa / Doomspiral: READY, STARTED, DONE on screen");
        b.toggle("  Gemzie", () -> config.safariBannerGemzie, v -> config.safariBannerGemzie = v);
        b.toggle("  Wumpa", () -> config.safariBannerWumpa, v -> config.safariBannerWumpa = v);
        b.toggle("  Doomspiral", () -> config.safariBannerDoomspiral, v -> config.safariBannerDoomspiral = v);
        b.toggle("  Only In Its Biome", () -> config.safariBannerBiomeOnly, v -> config.safariBannerBiomeOnly = v);
        b.channel("  Post Stages To", () -> config.safariBannerChannel, v -> config.safariBannerChannel = v);
        b.toggle("Wumpa Spawn Title", () -> config.safariWumpaTitle, v -> config.safariWumpaTitle = v).fresh()
                .desc("WUMPA SPAWNED! / Icy Biome as a title when the footsteps message comes");
        b.toggle("  Re-enter Waypoint", () -> config.safariWumpaReenter, v -> config.safariWumpaReenter = v).fresh()
                .desc("Marks the hole back into the Wumpa arena while Wumpa is up");
        b.toggle("Capsules Used On Catch", () -> config.safariLegendCapsules, v -> config.safariLegendCapsules = v).fresh()
                .desc("Wumpa / Doomspiral caught: chat line with how many capsules you threw at it");
        b.toggle("Sparkling Alert", () -> config.safariSparklingAlert, v -> config.safariSparklingAlert = v);
        b.channel("  Sparkling Announce", () -> config.safariSparklingChannel, v -> config.safariSparklingChannel = v);
        b.toggle("  Full Screen Alert", () -> config.safariSparklingScreen, v -> config.safariSparklingScreen = v);
        b.info("Screen wash with SPARKLING!, the critter, biome and coords, 7s");
        b.toggle("  Only When Visible", () -> config.safariSparklingVisible, v -> config.safariSparklingVisible = v);
        b.toggle("  Sparkling HUD", () -> config.safariSparklingHud, v -> config.safariSparklingHud = v).fresh()
                .desc("Label stays on screen while a sparkling is within 64 blocks. Position in /tmap gui");
        b.toggle("  Focus Sparkling", () -> config.safariSparklingFocus, v -> config.safariSparklingFocus = v).fresh()
                .desc("Box + tracer on the sparkling (or its sparkles), other Safari ESP hidden meanwhile");
        b.toggle("  Also Outside Safari", () -> config.safariSparklingOutside, v -> config.safariSparklingOutside = v).fresh()
                .desc("HUD and focus only");
        b.toggle("Force Sparkle Particles", () -> config.safariSparkleForce, v -> config.safariSparkleForce = v);
        b.info("Sparkles render at any distance and particle setting");
        b.toggle("Announce Biome Done", () -> config.safariAnnounceBiomeDone, v -> config.safariAnnounceBiomeDone = v);
        b.toggle("Announce Unique Done", () -> config.safariAnnounceUniqueDone, v -> config.safariAnnounceUniqueDone = v);
        b.toggle("  Snoozle 'None This Run'", () -> config.safariSnoozleNone, v -> config.safariSnoozleNone = v).fresh()
                .desc("All 5 Snoozle walls seen broken and no Snoozle caught or near: it counts as done for biome and dex");
        b.toggle("  Everything Except Macaw", () -> config.safariAnnounceAllButMacaw, v -> config.safariAnnounceAllButMacaw = v).fresh()
                .desc("Extra milestone once every critter but the Macaw is caught. Skipped when the last catch completes the dex.");
        b.channel("Share Time", () -> config.safariShareTimeChannel, v -> config.safariShareTimeChannel = v);
        b.toggle("Warn If Not Full", () -> config.safariWarnNotFull, v -> config.safariWarnNotFull = v);
        b.info("Screen warning + sound when fewer than 4 players are in");
        b.slider("Alert Volume", 0.0f, 3.0f,
                () -> config.safariAlertVolume, v -> config.safariAlertVolume = v);
        b.button("  Test Alerts (/tmap safari testalert)", com.teslamaps.safari.SafariBanners::testAll).fresh()
                .desc("Plays every banner stage, milestone and the sparkling screen once");
        b.label("Quality of Life");
        b.toggle("Hide Cold Overlay (Icy)", () -> config.safariHideCold, v -> config.safariHideCold = v);
        b.toggle("Auto Accept Hideyho", () -> config.safariAutoHideyho, v -> config.safariAutoHideyho = v);
        b.slider("  Min Delay (ms)", 0f, 2000f,
                () -> (float) config.safariAutoHideyhoMinMs, v -> config.safariAutoHideyhoMinMs = (int) (float) v, 0);
        b.slider("  Extra Random (ms)", 0f, 2000f,
                () -> (float) config.safariAutoHideyhoJitterMs, v -> config.safariAutoHideyhoJitterMs = (int) (float) v, 0);
        b.toggle("Hideyho Finder", () -> config.safariHideyhoFinder, v -> config.safariHideyhoFinder = v).fresh()
                .desc("After \"No peeking!\" a waypoint walks you through the 20 known hiding spots until Hideyho is in sight");
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
        b.toggle("  Hideonwall Perches", () -> config.safariHideonwallPerches, v -> config.safariHideonwallPerches = v).fresh()
                .desc("The 8 Haunted perches: resolved by looking at one or throwing a capsule through it");
        b.color("    Perch Color", () -> config.colorHideonwallPerch, v -> config.colorHideonwallPerch = v).fresh();
        b.color("    Found Color", () -> config.colorHideonwallFound, v -> config.colorHideonwallFound = v).fresh();
        b.toggle("Filled Boxes", () -> config.safariFilled, v -> config.safariFilled = v);
        b.toggle("Legit Mode (Depth Check)", () -> config.safariLegitMode, v -> config.safariLegitMode = v);
        b.info("Nothing drawn through walls");
        b.toggle("Bee Nest ESP", () -> config.safariNestESP, v -> config.safariNestESP = v);
        b.toggle("Scrappy Fish Highlight", () -> config.safariScrappyFish, v -> config.safariScrappyFish = v).fresh()
                .desc("The fish you feed to Scrappy (Cavern)").tags("scrappy", "fish");
        b.toggle("  Stop When Scrappy Caught", () -> config.safariScrappyFishStopCaught, v -> config.safariScrappyFishStopCaught = v).fresh();
        b.fresh(() -> b.espStyle("Fish", () -> config.safariScrappyFishStyle));
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
