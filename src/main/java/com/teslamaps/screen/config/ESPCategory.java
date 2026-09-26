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
        b.slider("  Glow Outline Width", 1f, com.teslamaps.render.GlowOutlineWidth.MAX,
                () -> (float) config.glowOutlineWidth, v -> config.glowOutlineWidth = Math.round(v), 0).fresh()
                .desc("Every glow outline (ESP, Mob Visuals). 1 = vanilla");
        b.toggle("  Glow In Unloaded Chunks", () -> config.glowUnbuiltSections, v -> config.glowUnbuiltSections = v).fresh()
                .desc("Glowing mobs past your render distance still render, so glow and tracer match");
        b.toggle("Filled ESP (vs Outline)", () -> config.filledESP, v -> config.filledESP = v);
        b.toggle("Box ESP (Hitbox)", () -> config.boxESP, v -> config.boxESP = v);
        b.slider("ESP Transparency", 0.0f, 1.0f,
                () -> config.espAlpha, v -> config.espAlpha = v);
        b.slider("  Distance Fade", 0f, 256f,
                () -> config.espFadeDistance, v -> config.espFadeDistance = v, 0).fresh()
                .desc("Boxes fade out with distance, half visible at this many blocks. Far ones are skipped. 0 = off");
        b.button("Custom Entries (/tmap esp)",
                () -> Minecraft.getInstance().setScreen(new EspListScreen()));
        b.customEspList("Custom ESP Mobs").desc("Tick mobs seen nearby to highlight them. Right-click a row for its colour.")
                .tags("custom", "mob", "highlight");
        b.fresh(() -> com.teslamaps.features.MobSpawnAlert.config(b));
        b.label("Dungeon ESP");
        b.toggle("Color Portal by Score", () -> config.colorPortal, v -> config.colorPortal = v);
        b.color("  Portal < 270", () -> config.colorPortalLow, v -> config.colorPortalLow = v);
        b.color("  Portal S", () -> config.colorPortalS, v -> config.colorPortalS = v);
        b.color("  Portal S+", () -> config.colorPortalSPlus, v -> config.colorPortalSPlus = v);
        b.toggle("Starred Mob ESP", () -> config.starredMobESP, v -> config.starredMobESP = v);
        b.toggle("  Hide Non-Starred Nametags", () -> config.hideNonStarredNametags, v -> config.hideNonStarredNametags = v).fresh()
                .desc("Dungeon only: hides the name tags of mobs without a ✯ (works without the ESP too)");
        b.toggle("  Tracer When Few Left", () -> config.starredTracerWhenFew, v -> config.starredTracerWhenFew = v);
        b.slider("    Tracer Threshold", 1f, 5f,
                () -> (float) config.starredTracerThreshold, v -> config.starredTracerThreshold = Math.round(v));
        b.color("  Starred Mobs", () -> config.colorESPStarred, v -> config.colorESPStarred = v);
        b.color("  Lost Adventurer", () -> config.colorESPLostAdventurer, v -> config.colorESPLostAdventurer = v);
        b.color("  Diamond Guy", () -> config.colorESPDiamondGuy, v -> config.colorESPDiamondGuy = v);
        b.toggle("  Starred Fel / Withermancer Colors", () -> config.espStarSpecialColors, v -> config.espStarSpecialColors = v);
        b.color("    Starred Fel", () -> config.colorESPStarredFel, v -> config.colorESPStarredFel = v);
        b.color("    Starred Withermancer", () -> config.colorESPStarredWithermancer, v -> config.colorESPStarredWithermancer = v);
        b.toggle("  Link Label to Mob by Entity ID", () -> config.starMobIdLink, v -> config.starMobIdLink = v).fresh()
                .desc("Pairs each ✯ nametag with its mob through the entity id (nearest free mob as fallback). Cheaper and right in crowded rooms. A label with no mob gets its own box.");
        b.toggle("  Starred Type Colors", () -> config.starMobTypeColors, v -> config.starMobTypeColors = v).fresh()
                .desc("Own colors for chunky mobs (Withermancer, Lords, Commander, Super Archer), Fels, Skeleton Master, minibosses and Shadow Assassins");
        b.color("    Chunky Mobs", () -> config.colorESPStarredChunky, v -> config.colorESPStarredChunky = v).fresh();
        b.color("    Skeleton Master", () -> config.colorESPStarredSkeletonMaster, v -> config.colorESPStarredSkeletonMaster = v).fresh();
        b.color("    Minibosses", () -> config.colorESPStarredMini, v -> config.colorESPStarredMini = v).fresh();
        b.slider("  Line Width Mobs", 1f, 8f, () -> config.espLineWidthNormal, v -> config.espLineWidthNormal = v, 1);
        b.slider("  Line Width Fel / Withermancer", 1f, 8f, () -> config.espLineWidthSpecial, v -> config.espLineWidthSpecial = v, 1);
        b.slider("  Line Width Minibosses", 1f, 8f, () -> config.espLineWidthMiniboss, v -> config.espLineWidthMiniboss = v, 1);
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
        b.toggle("  Bigger Bat Models", () -> config.biggerBatModels, v -> config.biggerBatModels = v).fresh()
                .desc("Draws dungeon bats larger so they are easier to hit (visual only)");
        b.slider("    Bat Scale", 1f, 5f, () -> config.biggerBatScale, v -> config.biggerBatScale = v, 1).fresh();
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
        b.toggle("  Door Tracers", () -> config.doorTracers, v -> config.doorTracers = v);
        b.toggle("  Door Color Based on Key", () -> config.doorColorBasedOnKey, v -> config.doorColorBasedOnKey = v);
        b.toggle("  Only Show Next Door", () -> config.onlyShowNextDoor, v -> config.onlyShowNextDoor = v);
        b.toggle("  Doors In Route Order", () -> config.doorEspRoute, v -> config.doorEspRoute = v);
        b.slider("    Route Doors Ahead", 0f, 10f, () -> config.doorEspAhead, v -> config.doorEspAhead = v, 0);
        b.toggle("  Green Only At Current Room", () -> config.doorEspKeyAtCurrentRoom, v -> config.doorEspKeyAtCurrentRoom = v);
        b.toggle("  Normal Doorways (Current Room)", () -> config.doorEspNormalDoors, v -> config.doorEspNormalDoors = v);
        b.toggle("    Hide Useless Doorways", () -> config.doorEspHideUseless, v -> config.doorEspHideUseless = v);
        b.espStyle("  Doorway", () -> config.doorEspNormalStyle);
        b.classFilter("  Doors: Only as Class", config.doorEspClasses);
        b.color("  Wither Door", () -> config.colorESPWitherDoor, v -> config.colorESPWitherDoor = v);
        b.color("  Blood Door", () -> config.colorESPBloodDoor, v -> config.colorESPBloodDoor = v);
        b.toggle("Fairy Door ESP", () -> config.fairyDoorESP, v -> config.fairyDoorESP = v);
        b.label("Livid");
        b.toggle("Livid Finder", () -> config.lividFinder, v -> config.lividFinder = v);
        b.toggle("  Livid Glow", () -> config.lividGlow, v -> config.lividGlow = v).fresh();
        b.toggle("  Livid Box", () -> config.lividBox, v -> config.lividBox = v).fresh();
        b.toggle("  Livid Tracer", () -> config.lividTracer, v -> config.lividTracer = v);
        b.toggle("  Livid Label", () -> config.lividLabel, v -> config.lividLabel = v).fresh()
                .desc("\"Livid\" above the correct Livid, in the highlight color.");
        b.toggle("  Use Livid's Own Color", () -> config.lividUseLividColor, v -> config.lividUseLividColor = v).fresh()
                .desc("Hockey red, Arcade yellow, Smile green, ... instead of the picked color.");
        b.color("  Livid Color", () -> config.lividColor, v -> config.lividColor = v).fresh();
        b.slider("  Livid Box Line Width", 1f, 8f, () -> config.lividLineWidth, v -> config.lividLineWidth = v, 1).fresh();
        b.slider("  Livid Tracer Width", 1f, 8f, () -> config.lividTracerWidth, v -> config.lividTracerWidth = v, 1).fresh();
        b.toggle("  Livid Dead Message (/pc)", () -> config.lividDeathMessage, v -> config.lividDeathMessage = v);
        b.toggle("  Livid Color Title", () -> config.lividColorTitle, v -> config.lividColorTitle = v).fresh();
        b.toggle("  Announce Livid Color (/pc)", () -> config.lividAnnounce, v -> config.lividAnnounce = v).fresh();
        b.button("    Edit Announce Text", () -> net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.teslamaps.screen.LividMessageScreen(net.minecraft.client.Minecraft.getInstance().screen))).fresh();
        b.toggle("  Livid HP Label (3D)", () -> config.lividHpLabel, v -> config.lividHpLabel = v).fresh();
        b.toggle("  Livid HP HUD", () -> config.lividHpHud, v -> config.lividHpHud = v).fresh();
        b.toggle("  Keep Wrong Livids Visible", () -> config.lividShowWrong, v -> config.lividShowWrong = v).legit();
        b.toggle("  Hide Wrong Livid Nametags", () -> config.lividHideWrongNametags, v -> config.lividHideWrongNametags = v).legit();
        b.toggle("  Skin Detection (learns Livid skins)", () -> config.lividSkinDetection, v -> config.lividSkinDetection = v).fresh();
        b.label("Mimic");
        b.toggle("Mimic Chest ESP", () -> config.mimicChestESP, v -> config.mimicChestESP = v);
        b.toggle("  Mimic Chest Tracers", () -> config.mimicChestTracers, v -> config.mimicChestTracers = v);
        b.label("Other ESP");
        b.toggle("Highlight Teammates", () -> config.highlightTeammates, v -> config.highlightTeammates = v);
        b.toggle("  Also Outside Dungeons (Party Glow)", () -> config.highlightPartyOutside, v -> config.highlightPartyOutside = v).fresh()
                .desc("Party members get an outline glow anywhere in SkyBlock");
        b.color("    Party Glow Color", () -> config.highlightPartyColor, v -> config.highlightPartyColor = v).fresh();
        b.toggle("Teammate Health Warning", () -> config.teammateHealth, v -> config.teammateHealth = v).fresh().legit()
                .desc("Reads the scoreboard hearts: yellow = LOW, red = CRITICAL, shown over the teammate through walls and on the map.");
        b.toggle("  Name Labels ([M] Name)", () -> config.teammateHealthLabels, v -> config.teammateHealthLabels = v).fresh();
        b.toggle("  Low/Critical Outline", () -> config.teammateHealthOutline, v -> config.teammateHealthOutline = v).fresh();
        b.toggle("  Ring On Map Head", () -> config.teammateHealthMap, v -> config.teammateHealthMap = v).fresh();
        b.toggle("  Only As Healer", () -> config.teammateHealthOnlyHealer, v -> config.teammateHealthOnlyHealer = v).fresh();
        b.toggle("Invisible Armor Stand ESP", () -> config.invisibleArmorStandESP, v -> config.invisibleArmorStandESP = v);
        b.toggle("Hoppity Egg ESP", () -> config.hoppityEggESP, v -> config.hoppityEggESP = v);
        b.toggle("  Hoppity Egg Tracers", () -> config.hoppityEggTracers, v -> config.hoppityEggTracers = v);
        b.toggle("  Exact Meals & Respawn Timer", () -> config.hoppityEggMealTimer, v -> config.hoppityEggMealTimer = v).fresh()
                .desc("Labels show the real meal (Brunch, Déjeuner, Supper on alternate days). Collected eggs keep a grey label with the time until they respawn and come back on their own at the next spawn. Only during spring");
        b.toggle("  Mark Clicked Egg Exactly", () -> config.hoppityEggClickMark, v -> config.hoppityEggClickMark = v).fresh()
                .desc("Right-clicking an egg marks exactly that egg as collected, instead of the nearest one on the chat message");
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
        b.toggle("  Rarity Color", () -> config.droppedItemRarityColor, v -> config.droppedItemRarityColor = v);
        b.toggle("  Show Name & Amount", () -> config.droppedItemLabel, v -> config.droppedItemLabel = v);
        b.toggle("Item Pickup Readiness", () -> config.secretItemReadiness, v -> config.secretItemReadiness = v);
        b.slider("  Item Pickup Delay (ticks)", 0f, 200f,
                () -> (float) config.secretItemPickupTicks, v -> config.secretItemPickupTicks = Math.round(v));
    }
}
