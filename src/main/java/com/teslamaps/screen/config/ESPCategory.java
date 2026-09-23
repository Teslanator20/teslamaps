package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ESPCategory {

    private ESPCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("ESP Rendering");
        b.toggle("Show Glow", () -> config.showGlow, v -> config.showGlow = v);
        b.toggle("Filled ESP (vs Outline)", () -> config.filledESP, v -> config.filledESP = v);
        b.toggle("Box ESP (Hitbox)", () -> config.boxESP, v -> config.boxESP = v);
        b.slider("ESP Transparency", 0.0f, 1.0f,
                () -> config.espAlpha, v -> config.espAlpha = v);
        b.button("Custom Entries (/tmap esp)",
                () -> Minecraft.getInstance().setScreen(new EspListScreen()));
        b.label("Dungeon ESP");
        b.toggle("Color Portal by Score", () -> config.colorPortal, v -> config.colorPortal = v);
        b.color("  Portal < 270", () -> config.colorPortalLow, v -> config.colorPortalLow = v);
        b.color("  Portal S", () -> config.colorPortalS, v -> config.colorPortalS = v);
        b.color("  Portal S+", () -> config.colorPortalSPlus, v -> config.colorPortalSPlus = v);
        b.toggle("Starred Mob ESP", () -> config.starredMobESP, v -> config.starredMobESP = v);
        b.toggle("  Tracer When Few Left", () -> config.starredTracerWhenFew, v -> config.starredTracerWhenFew = v);
        b.slider("  Tracer Threshold", 1f, 5f,
                () -> (float) config.starredTracerThreshold, v -> config.starredTracerThreshold = Math.round(v));
        b.color("  Starred Mobs", () -> config.colorESPStarred, v -> config.colorESPStarred = v);
        b.toggle("Fel ESP", () -> config.felESP, v -> config.felESP = v);
        b.slider("  Fel ESP Range", 10f, 100f,
                () -> (float) config.felESPRange, v -> config.felESPRange = v.intValue());
        b.color("  Fels", () -> config.colorESPFel, v -> config.colorESPFel = v);
        b.toggle("Sniper ESP", () -> config.sniperESP, v -> config.sniperESP = v);
        b.color("  Snipers", () -> config.colorESPSniper, v -> config.colorESPSniper = v);
        b.toggle("Shadow Assassin ESP", () -> config.shadowAssassinESP, v -> config.shadowAssassinESP = v);
        b.color("  Shadow Assassins", () -> config.colorESPShadowAssassin, v -> config.colorESPShadowAssassin = v);
        b.toggle("Dungeon Bat ESP", () -> config.dungeonBatESP, v -> config.dungeonBatESP = v);
        b.toggle("  Dungeon Bat Tracers", () -> config.dungeonBatTracers, v -> config.dungeonBatTracers = v);
        b.color("  Dungeon Bats", () -> config.colorESPBat, v -> config.colorESPBat = v);
        b.toggle("Wither Key ESP", () -> config.witherKeyESP, v -> config.witherKeyESP = v);
        b.toggle("  Key Tracers", () -> config.keyTracers, v -> config.keyTracers = v);
        b.color("  Wither Key", () -> config.colorESPWitherKey, v -> config.colorESPWitherKey = v);
        b.color("  Blood Key", () -> config.colorESPBloodKey, v -> config.colorESPBloodKey = v);
        b.toggle("Wither ESP", () -> config.bossESP, v -> config.bossESP = v);
        b.toggle("  Wither ESP Filled", () -> config.bossESPFilled, v -> config.bossESPFilled = v);
        b.toggle("  Wither ESP Depth Check", () -> config.bossESPDepthCheck, v -> config.bossESPDepthCheck = v);
        b.color("  Wither ESP", () -> config.colorBossESP, v -> config.colorBossESP = v);
        b.label("Doors");
        b.toggle("Door ESP", () -> config.doorESP, v -> config.doorESP = v);
        b.toggle("Fairy Door ESP", () -> config.fairyDoorESP, v -> config.fairyDoorESP = v);
        b.toggle("  Door Tracers", () -> config.doorTracers, v -> config.doorTracers = v);
        b.toggle("  Door Color Based on Key", () -> config.doorColorBasedOnKey, v -> config.doorColorBasedOnKey = v);
        b.toggle("  Only Show Next Door", () -> config.onlyShowNextDoor, v -> config.onlyShowNextDoor = v);
        b.classFilter("  Doors: Only as Class", config.doorEspClasses);
        b.color("  Wither Door", () -> config.colorESPWitherDoor, v -> config.colorESPWitherDoor = v);
        b.color("  Blood Door", () -> config.colorESPBloodDoor, v -> config.colorESPBloodDoor = v);
        b.label("Livid");
        b.toggle("Livid Finder", () -> config.lividFinder, v -> config.lividFinder = v);
        b.toggle("  Livid Tracer", () -> config.lividTracer, v -> config.lividTracer = v);
        b.toggle("  Livid Dead Message (/pc)", () -> config.lividDeathMessage, v -> config.lividDeathMessage = v);
        b.label("Mimic");
        b.toggle("Mimic Chest ESP", () -> config.mimicChestESP, v -> config.mimicChestESP = v);
        b.toggle("  Mimic Chest Tracers", () -> config.mimicChestTracers, v -> config.mimicChestTracers = v);
        b.label("Other ESP");
        b.toggle("Highlight Teammates", () -> config.highlightTeammates, v -> config.highlightTeammates = v);
        b.toggle("Invisible Armor Stand ESP", () -> config.invisibleArmorStandESP, v -> config.invisibleArmorStandESP = v);
        b.toggle("Hoppity Egg ESP", () -> config.hoppityEggESP, v -> config.hoppityEggESP = v);
        b.toggle("Hoppity Egg Tracers", () -> config.hoppityEggTracers, v -> config.hoppityEggTracers = v);
        b.label("Head ESP");
        b.info("/tmap head add <name> while looking at a head");
        b.info("/tmap head list | remove <name> | probe [radius]");
        b.toggle("Golden Dragon Egg ESP", () -> config.headGoldenDragonEgg, v -> config.headGoldenDragonEgg = v);
        b.color("  Egg Color", () -> config.headGoldenDragonColor, v -> config.headGoldenDragonColor = v);
        b.toggle("Head ESP (custom list)", () -> config.headESP, v -> config.headESP = v);
        b.toggle("  Show Name", () -> config.headESPName, v -> config.headESPName = v);
        b.toggle("  Head Tracers", () -> config.headESPTracers, v -> config.headESPTracers = v);
        b.toggle("  Head Beacon Beam", () -> config.headESPBeam, v -> config.headESPBeam = v);
        b.color("  New Head Color", () -> config.headESPColor, v -> config.headESPColor = v);
        b.label("Items");
        b.toggle("Dropped Item ESP", () -> config.droppedItemESP, v -> config.droppedItemESP = v);
        b.toggle("Item Pickup Readiness", () -> config.secretItemReadiness, v -> config.secretItemReadiness = v);
        b.slider("Item Pickup Delay (ticks)", 0f, 200f,
                () -> (float) config.secretItemPickupTicks, v -> config.secretItemPickupTicks = Math.round(v));
    }
}
