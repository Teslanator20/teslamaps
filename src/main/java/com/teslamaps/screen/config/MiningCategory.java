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
        b.label("Glacite Corpses");
        b.toggle("Corpse ESP", () -> config.corpseESP, v -> config.corpseESP = v);
        b.toggle("  Type Colors", () -> config.corpseESPTypeColors, v -> config.corpseESPTypeColors = v);
        b.color("  Corpse Color", () -> config.colorCorpseESP, v -> config.colorCorpseESP = v);
        b.label("Drill / Pickaxe Ability");
        b.toggle("Ability Cooldown Display",
                () -> config.pickaxeAbilityDisplay, v -> config.pickaxeAbilityDisplay = v);
        b.toggle("  Only On Mining Islands",
                () -> config.pickaxeAbilityMiningOnly, v -> config.pickaxeAbilityMiningOnly = v);
        b.toggle("  Hide While Available",
                () -> config.pickaxeAbilityOnlyOnCooldown, v -> config.pickaxeAbilityOnlyOnCooldown = v);
        b.info("Needs the Pickaxe Ability tab widget: /tab");
        b.label("Mineshaft Pity");
        b.toggle("Pity Counter Display",
                () -> config.mineshaftPityDisplay, v -> config.mineshaftPityDisplay = v);
        b.toggle("Chat Line On Mineshaft Found",
                () -> config.mineshaftFoundChat, v -> config.mineshaftFoundChat = v);
        b.slider("Mineshaft Chance Bonus (%)", 0f, 100f,
                () -> config.mineshaftChanceBonus, v -> config.mineshaftChanceBonus = v, 1);
        b.info("Set from Surveyor + Core of the Mountain when you open the HOTM menu");
        b.button("Reset Mineshaft Stats",
                () -> com.teslamaps.features.mining.MineshaftPity.resetStats());
        b.info("Needs the Pity widget: /tab > Pity > Glacite Tunnels");
    }
}
