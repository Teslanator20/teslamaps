package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class MiningCategory {

    private MiningCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Crystal Hollows Scanner");
        b.toggle("Crystal Hollows Scanner", () -> config.chScanner, v -> config.chScanner = v);
        b.info("Scans loaded chunks, boxes + chat alert");
        b.label("Structures");
        b.toggle("Golden Dragon Nest", () -> config.chScanDragon, v -> config.chScanDragon = v);
        b.toggle("Crystals (King/Queen/Temple/City/Divan/Bal)", () -> config.chScanCrystals, v -> config.chScanCrystals = v);
        b.toggle("Mob Spots (Corleone/Keys/Xalx/Pete/Odawa)", () -> config.chScanMobSpots, v -> config.chScanMobSpots = v);
        b.toggle("Fairy Grottos", () -> config.chScanGrottos, v -> config.chScanGrottos = v);
        b.toggle("Landmarks (Goblin Hall, Ruined Forge, Mansion, ...)", () -> config.chScanLandmarks, v -> config.chScanLandmarks = v).fresh()
                .desc("26 smaller Crystal Hollows spots: jungle caverns, forges, railways, trees, pillars.")
                .tags("structure", "landmark", "goblin hall", "ruined forge", "jade pedestal", "crystal train", "mansion");
        b.label("Display");
        b.toggle("Tracers", () -> config.chScanTracers, v -> config.chScanTracers = v);
        b.toggle("Chat Alert", () -> config.chScanChat, v -> config.chScanChat = v);
        b.label("Named Waypoints").fresh();
        b.toggle("Named Waypoints (Chat / Manual / Shared)", () -> config.chWaypoints, v -> config.chWaypoints = v).fresh()
                .desc("Waypoints from coords in chat, /tmap chwp add <name>, /tmap chwp share <name> to the party. Kept per lobby for 24h. Needs the scanner on.")
                .tags("crystal hollows", "waypoint", "coords", "share", "divan", "temple", "queen", "city", "khazad");
        b.toggle("  Read Coords From Chat", () -> config.chWaypointsFromChat, v -> config.chWaypointsFromChat = v).fresh()
                .desc("Coords with a place name become a waypoint; without a name you get a clickable list. Also reads SBE's $SBECHWP.");
        b.toggle("  Waypoint From Scoreboard Area", () -> config.chWaypointsSubzone, v -> config.chWaypointsSubzone = v).fresh()
                .desc("Entering Mines of Divan, Jungle Temple, Dragon's Lair, ... sets a waypoint there");
        b.toggle("  Keep Across Lobby Rejoins", () -> config.chWaypointsPersist, v -> config.chWaypointsPersist = v).fresh();
        b.label("Individual Structures").fresh();
        String[] groups = {"Crystals", "Mob Spots", "Fairy Grottos", "Golden Dragon", null, "Landmarks"};
        java.util.Map<String, Integer> names = com.teslamaps.features.WorldScanner.structureNames();
        for (int g = 0; g < groups.length; g++) {
            if (groups[g] == null) continue;
            b.info(groups[g]).fresh();
            for (java.util.Map.Entry<String, Integer> e : names.entrySet()) {
                if (e.getValue() != g) continue;
                String name = e.getKey();
                b.toggle("  " + name, () -> com.teslamaps.features.WorldScanner.structureOn(name), v -> {
                    if (v) config.chScanStructures.remove(name);
                    else config.chScanStructures.put(name, false);
                }).fresh().tags("structure", "crystal hollows");
            }
        }
        b.label("Glacite Corpses");
        b.toggle("Corpse Finder (Legit)", () -> config.corpseFinder, v -> config.corpseFinder = v)
                .desc("Marks a corpse once you had line of sight to it. Never through walls.")
                .tags("lapis", "tungsten", "umber", "vanguard", "mineshaft", "share");
        b.toggle("  Read Coords From Chat", () -> config.corpseFinderParseChat, v -> config.corpseFinderParseChat = v);
        b.channel("  Share Channel", () -> config.corpseFinderChannel, v -> config.corpseFinderChannel = v);
        b.info("Click the Found line or /tmap corpse share to send it");
        b.toggle("Mineshaft Exit Waypoint", () -> config.mineshaftExitWaypoint, v -> config.mineshaftExitWaypoint = v).fresh()
                .desc("Green Exit waypoint where you entered the Glacite Mineshaft. Works on its own; replaces the Entrance marker when both are on.")
                .tags("mineshaft", "exit", "waypoint", "glacite");
        b.toggle("Mineshaft Waypoints", () -> config.mineshaftWaypoints, v -> config.mineshaftWaypoints = v).fresh()
                .desc("Entrance and ladder waypoints on entering a mineshaft, found corpses as labelled waypoints.")
                .tags("mineshaft", "glacite", "ladder", "entrance", "corpse", "share", "waypoint");
        b.toggle("  Entrance", () -> config.mineshaftWpEntrance, v -> config.mineshaftWpEntrance = v).fresh();
        b.toggle("  Ladder", () -> config.mineshaftWpLadder, v -> config.mineshaftWpLadder = v).fresh();
        b.toggle("  Corpse Waypoints", () -> config.mineshaftWpCorpses, v -> config.mineshaftWpCorpses = v).fresh()
                .desc("Seen corpses get a yellow label, green once looted. Party coords become waypoints too.");
        b.toggle("  Auto-Share Corpses In Party", () -> config.mineshaftAutoShareCorpses, v -> config.mineshaftAutoShareCorpses = v).fresh()
                .desc("Sends the corpse you stand at to party chat once, unless someone already sent it.");
        b.keybind("  Share Nearest Waypoint Key", () -> config.mineshaftShareKey, v -> config.mineshaftShareKey = v).fresh();
        b.label("Drill / Pickaxe Ability");
        b.toggle("Ability Cooldown Display",
                () -> config.pickaxeAbilityDisplay, v -> config.pickaxeAbilityDisplay = v);
        b.toggle("  Only On Mining Islands",
                () -> config.pickaxeAbilityMiningOnly, v -> config.pickaxeAbilityMiningOnly = v);
        b.toggle("  Hide While Available",
                () -> config.pickaxeAbilityOnlyOnCooldown, v -> config.pickaxeAbilityOnlyOnCooldown = v);
        b.toggle("  Show As Bar", () -> config.pickaxeAbilityBar, v -> config.pickaxeAbilityBar = v)
                .desc("Animated cooldown bar, reacts to the ability chat line right away.");
        b.toggle("    Vertical Bar", () -> config.pickaxeAbilityBarVertical, v -> config.pickaxeAbilityBarVertical = v);
        b.dropdown("    Ready Text", new String[] {"Off", "Right", "Left", "Top", "Bottom"},
                () -> config.pickaxeAbilityReadyText, v -> config.pickaxeAbilityReadyText = v);
        b.color("    Ready Color", () -> config.pickaxeAbilityReadyColor, v -> config.pickaxeAbilityReadyColor = v);
        b.color("    Empty Color", () -> config.pickaxeAbilityEmptyColor, v -> config.pickaxeAbilityEmptyColor = v);
        b.info("Needs the Pickaxe Ability tab widget: /tab");
        b.label("Mineshaft Pity");
        b.toggle("Pity Counter Display",
                () -> config.mineshaftPityDisplay, v -> config.mineshaftPityDisplay = v);
        b.toggle("  Needed To Pity Per Block",
                () -> config.mineshaftPityShowNeeded, v -> config.mineshaftPityShowNeeded = v);
        b.toggle("  Average Blocks Per Mineshaft",
                () -> config.mineshaftPityShowAverage, v -> config.mineshaftPityShowAverage = v);
        b.toggle("  Hide Chance Line", () -> config.mineshaftPityHideChance, v -> config.mineshaftPityHideChance = v);
        b.toggle("  Hide Last Mineshaft Line", () -> config.mineshaftPityHideLast, v -> config.mineshaftPityHideLast = v);
        b.toggle("  Hide Found Line", () -> config.mineshaftPityHideFound, v -> config.mineshaftPityHideFound = v);
        b.toggle("Chat Line On Mineshaft Found",
                () -> config.mineshaftFoundChat, v -> config.mineshaftFoundChat = v);
        b.toggle("  Block Breakdown Hover",
                () -> config.mineshaftPityChatHover, v -> config.mineshaftPityChatHover = v);
        b.slider("Mineshaft Chance Bonus (%)", 0f, 100f,
                () -> config.mineshaftChanceBonus, v -> config.mineshaftChanceBonus = v, 1);
        b.info("Set from Surveyor + Core of the Mountain when you open the HOTM menu");
        b.button("Reset Mineshaft Stats",
                () -> com.teslamaps.features.mining.MineshaftPity.resetStats());
        b.info("Needs the Pity widget: /tab > Pity > Glacite Tunnels");
    }
}
