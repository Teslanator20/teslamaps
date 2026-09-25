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
        b.toggle("Worm Fishing Spots", () -> config.chScanWorm, v -> config.chScanWorm = v);
        b.label("Display");
        b.toggle("Tracers", () -> config.chScanTracers, v -> config.chScanTracers = v);
        b.toggle("Chat Alert", () -> config.chScanChat, v -> config.chScanChat = v);
        b.toggle("Last Death Waypoint", () -> config.chDeathWaypoint, v -> config.chDeathWaypoint = v)
                .desc("After a death in the Crystal Hollows, click the chat line for a waypoint where you died.");
        b.label("Glacite Corpses");
        b.toggle("Corpse ESP", () -> config.corpseESP, v -> config.corpseESP = v)
                .desc("Highlights Glacite Mineshaft corpses through walls.")
                .tags("esp", "lapis", "tungsten", "umber", "vanguard", "mineshaft");
        b.toggle("  Type Colors", () -> config.corpseESPTypeColors, v -> config.corpseESPTypeColors = v);
        b.color("  Corpse Color", () -> config.colorCorpseESP, v -> config.colorCorpseESP = v);
        b.toggle("  Key Count On Spawn", () -> config.corpseKeyAnnounce, v -> config.corpseKeyAnnounce = v)
                .desc("When the corpses spawn: one chat line per type with the corpse count and your keys (inventory, sacks, ender chest, backpacks)")
                .tags("corpse", "key", "umber", "tungsten", "skeleton", "mineshaft");
        b.toggle("Corpse Finder (Legit)", () -> config.corpseFinder, v -> config.corpseFinder = v)
                .desc("Marks a corpse once you had line of sight to it. Never through walls.")
                .tags("lapis", "tungsten", "umber", "vanguard", "mineshaft", "share");
        b.toggle("  Read Coords From Chat", () -> config.corpseFinderParseChat, v -> config.corpseFinderParseChat = v);
        b.channel("  Share Channel", () -> config.corpseFinderChannel, v -> config.corpseFinderChannel = v);
        b.info("Click the Found line or /tmap corpse share to send it");
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
