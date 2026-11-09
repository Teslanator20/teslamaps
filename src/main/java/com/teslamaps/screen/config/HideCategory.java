package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class HideCategory {

    private HideCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Hide Players");
        b.toggle("Hide Players", () -> config.hidePlayers, v -> config.hidePlayers = v);
        b.toggle("  Only In Dungeon", () -> config.hidePlayersOnlyDungeon, v -> config.hidePlayersOnlyDungeon = v);
        b.toggle("  Hide All (any distance)", () -> config.hidePlayersAll, v -> config.hidePlayersAll = v);
        b.toggle("  Only At SS / 4th Device (P3)", () -> config.hidePlayersOnlyDevs, v -> config.hidePlayersOnlyDevs = v).fresh()
                .desc("Players are only hidden while you stand at the Simon Says or 4th device stand in F7/M7 P3.");
        b.slider("  Hide Distance", 0f, 32f,
                () -> config.hidePlayersDistance, v -> config.hidePlayersDistance = v);
        b.toggle("  Hide After Leap", () -> config.hidePlayersAfterLeap, v -> config.hidePlayersAfterLeap = v);
        b.slider("    Hide After Leap (s)", 0.5f, 10f,
                () -> config.hidePlayersAfterLeapSeconds, v -> config.hidePlayersAfterLeapSeconds = v);
        b.toggle("  See-Through Instead Of Hidden", () -> config.hidePlayersTransparent, v -> config.hidePlayersTransparent = v).fresh()
                .desc("Players that would be hidden are drawn see-through instead. 0% hides them fully.");
        b.slider("    Opacity %", 0f, 100f, () -> config.hidePlayersOpacity, v -> config.hidePlayersOpacity = v, 0).fresh();
        b.label("Hide Dungeon Objects");
        b.button("Toggle All Dungeon Objects", () -> {
            int on = 0, total = 11;
            if (config.hideDeadMobs) on++;
            if (config.hideSoulweaverSkull) on++;
            if (config.hideSkeletonSkull) on++;
            if (config.hideThrownBones) on++;
            if (config.hideSuperboomTnt) on++;
            if (config.hideBlessing) on++;
            if (config.hideReviveStone) on++;
            if (config.hidePremiumFlesh) on++;
            if (config.hideHealerOrbs) on++;
            if (config.hideHealerFairy) on++;
            if (config.hideCheapCoins) on++;
            boolean nv = on <= total / 2;
            config.hideDeadMobs = nv; config.hideSoulweaverSkull = nv; config.hideSkeletonSkull = nv;
            config.hideThrownBones = nv; config.hideSuperboomTnt = nv; config.hideBlessing = nv;
            config.hideReviveStone = nv; config.hidePremiumFlesh = nv;
            config.hideHealerOrbs = nv; config.hideHealerFairy = nv; config.hideCheapCoins = nv;
            TeslaMapsConfig.save();
        });
        b.toggle("Hide Dead Mobs", () -> config.hideDeadMobs, v -> config.hideDeadMobs = v);
        b.toggle("  Dead Mobs Not Clickable", () -> config.deadMobsUnpickable, v -> config.deadMobsUnpickable = v).fresh().cheat()
                .desc("Corpses stop catching your clicks and hits, the attack goes to the mob behind")
                .tags("no mob leftover", "corpse", "pickable", "dead");
        b.toggle("Hide Soulweaver Skull", () -> config.hideSoulweaverSkull, v -> config.hideSoulweaverSkull = v);
        b.toggle("Hide Skeleton Skulls", () -> config.hideSkeletonSkull, v -> config.hideSkeletonSkull = v);
        b.toggle("  Keep Thrown Skulls Visible", () -> config.showMovingSkulls, v -> config.showMovingSkulls = v);
        b.toggle("Highlight Thrown Skulls", () -> config.highlightMovingSkulls, v -> config.highlightMovingSkulls = v);
        b.toggle("Hide Thrown Bones", () -> config.hideThrownBones, v -> config.hideThrownBones = v);
        b.toggle("  Also Archer Passive (Bone Meal)", () -> config.hideArcherPassive, v -> config.hideArcherPassive = v).fresh()
                .desc("Hides the bone meal items circling an Archer.");
        b.toggle("Hide Superboom TNT", () -> config.hideSuperboomTnt, v -> config.hideSuperboomTnt = v);
        b.toggle("Hide Blessings", () -> config.hideBlessing, v -> config.hideBlessing = v);
        b.toggle("Hide Revive Stone", () -> config.hideReviveStone, v -> config.hideReviveStone = v);
        b.toggle("Hide Premium Flesh", () -> config.hidePremiumFlesh, v -> config.hidePremiumFlesh = v);
        b.toggle("Hide Healer Orbs", () -> config.hideHealerOrbs, v -> config.hideHealerOrbs = v);
        b.toggle("  Also Hide Orb Particles", () -> config.hideHealerOrbParticles, v -> config.hideHealerOrbParticles = v).fresh()
                .desc("Drops the dust trail within 8 blocks of a hidden orb.");
        b.toggle("Hide Healer Fairy", () -> config.hideHealerFairy, v -> config.hideHealerFairy = v);
        b.toggle("Hide Hidden Item Particles", () -> config.hideDungeonItemParticles, v -> config.hideDungeonItemParticles = v);
        b.info("Firework/dust at hidden Superboom, Revive Stone, Premium Flesh and orbs");
        b.toggle("Hide Cheap Coins", () -> config.hideCheapCoins, v -> config.hideCheapCoins = v);
        b.toggle("Hide Wither Cloak Creepers", () -> config.hideCloakCreepers, v -> config.hideCloakCreepers = v).fresh()
                .desc("Invisible powered creepers from Wither Cloak are not drawn. They still block clicks.");
        b.label("Screen & World Hiders");
        b.button("Toggle All Screen & World Hiders", () -> {
            int enabled = 0, total = 21;
            if (config.noFire) enabled++;
            if (config.noWaterOverlay) enabled++;
            if (config.noVignette) enabled++;
            if (config.noBlind) enabled++;
            if (config.noNausea) enabled++;
            if (config.noDeathAnimation) enabled++;
            if (config.hideInventoryEffects) enabled++;
            if (config.noEffects) enabled++;
            if (config.noLightning) enabled++;
            if (config.noExplosions) enabled++;
            if (config.noBlockBreaking) enabled++;
            if (config.noFallingBlocks) enabled++;
            if (config.noArrows) enabled++;
            if (config.noStuckArrows) enabled++;
            if (config.noFog) enabled++;
            if (config.noHurtCam) enabled++;
            if (config.noXpOrbs) enabled++;
            if (config.hideClouds) enabled++;
            if (config.noEnchantGlint) enabled++;
            if (config.hideArmorBar) enabled++;
            if (config.hideHungerBar) enabled++;
            boolean newValue = enabled <= total / 2;
            config.noFire = newValue;
            config.noWaterOverlay = newValue;
            config.noVignette = newValue;
            config.noBlind = newValue;
            config.noNausea = newValue;
            config.noDeathAnimation = newValue;
            config.hideInventoryEffects = newValue;
            config.noEffects = newValue;
            config.noLightning = newValue;
            config.noExplosions = newValue;
            config.noBlockBreaking = newValue;
            config.noFallingBlocks = newValue;
            config.noArrows = newValue;
            config.noStuckArrows = newValue;
            config.noFog = newValue;
            config.noHurtCam = newValue;
            config.noXpOrbs = newValue;
            config.hideClouds = newValue;
            config.noEnchantGlint = newValue;
            config.hideArmorBar = newValue;
            config.hideHungerBar = newValue;
            TeslaMapsConfig.save();
        });
        b.label("World & Weather");
        b.toggle("Hide Clouds",
                () -> config.hideClouds, v -> config.hideClouds = v);
        b.toggle("  Only On Selected Islands", () -> config.hideCloudsIslands, v -> config.hideCloudsIslands = v).fresh()
                .desc("Clouds stay visible on the other islands.");
        b.searchList("    Cloud Islands", () -> com.teslamaps.features.IslandClouds.ISLANDS,
                config.cloudHiderIslands::contains, t -> {
                    if (!config.cloudHiderIslands.remove(t)) config.cloudHiderIslands.add(t);
                }).fresh();
        b.toggle("Hide Fog", () -> config.noFog, v -> config.noFog = v);
        b.toggle("Nether Fog (Crimson Isle)", () -> config.noNetherFog, v -> config.noNetherFog = v);
        b.info("Removes the crimson isle fog even while Hide Fog is off");
        b.toggle("  Normal Fog Color Under Night Vision", () -> config.crimsonFogColorFix, v -> config.crimsonFogColorFix = v).fresh()
                .desc("Crimson Isle: night vision no longer tints the fog bright. For when you keep the fog (Nether Fog off)")
                .tags("fog", "crimson", "night vision", "nether");
        b.toggle("Hide Lightning", () -> config.noLightning, v -> config.noLightning = v);
        b.toggle("  Hide Sky Flash", () -> config.noLightningSkyFlash, v -> config.noLightningSkyFlash = v).fresh()
                .desc("The sky no longer lights up when lightning strikes.");
        b.toggle("Hide Falling Blocks", () -> config.noFallingBlocks, v -> config.noFallingBlocks = v);
        b.toggle("Hide Block Breaking", () -> config.noBlockBreaking, v -> config.noBlockBreaking = v);
        b.toggle("  Only In Garden", () -> config.noBlockBreakingGardenOnly, v -> config.noBlockBreakingGardenOnly = v).fresh();
        b.toggle("  Hide Block Hit Particles", () -> config.noBlockHitParticles, v -> config.noBlockHitParticles = v).fresh()
                .desc("Also drops the crack particles on every hit while mining or farming.");
        b.toggle("Particle Hider", () -> config.particleHider, v -> config.particleHider = v).fresh()
                .desc("Hides particles by distance or type.");
        b.toggle("  Far Particles (40+ blocks)", () -> config.particleHideFar, v -> config.particleHideFar = v).fresh()
                .desc("Not in the M7 boss.");
        b.toggle("  Close Redstone Dust", () -> config.particleHideCloseRedstone, v -> config.particleHideCloseRedstone = v).fresh()
                .desc("Redstone dust within 2 blocks of you.");
        b.toggle("  Fireball Smoke", () -> config.particleHideFireball, v -> config.particleHideFireball = v).fresh();
        b.toggle("  Blaze Particles", () -> config.particleHideBlaze, v -> config.particleHideBlaze = v).fresh();
        b.toggle("  Enderman Particles", () -> config.particleHideEnderman, v -> config.particleHideEnderman = v).fresh();
        b.toggle("  Fire Block Particles", () -> config.particleHideFireBlock, v -> config.particleHideFireBlock = v).fresh();
        b.toggle("  All Smoke", () -> config.particleHideSmoke, v -> config.particleHideSmoke = v).fresh();
        b.toggle("Hide XP Orbs", () -> config.noXpOrbs, v -> config.noXpOrbs = v);
        b.toggle("Hide Firework Rockets", () -> config.hideFireworks, v -> config.hideFireworks = v).fresh()
                .desc("Bonzo Staff rockets and every other firework rocket. The spark trail only goes away with Block At Packet Level.");
        b.toggle("Block At Packet Level", () -> config.packetHiders, v -> config.packetHiders = v);
        b.info("Lightning/Falling Blocks/XP Orbs are then never created");
        b.info("Saves ticking them, not just drawing them");
        b.label("Combat Effects");
        b.toggle("Hide Explosions", () -> config.noExplosions, v -> config.noExplosions = v);
        b.toggle("  Hide Wither Shield Hearts", () -> config.hideHypeHearts, v -> config.hideHypeHearts = v).fresh()
                .desc("Heart particles from healing with Wither Shield (Hyperion etc.). Works without Hide Explosions.");
        b.toggle("  Hide Enchant Rune Particles", () -> config.hideEnchantRunes, v -> config.hideEnchantRunes = v).fresh()
                .desc("Enchanting-table glyph particles, e.g. from runes. Pest and burrow particles stay.");
        b.toggle("  Not During Chicken Racing", () -> config.noExplosionsChickenRace, v -> config.noExplosionsChickenRace = v).fresh()
                .desc("Explosions stay visible while CHICKEN RACING shows in the action bar (Jerry's Workshop).");
        b.toggle("Hide Wither Blade Implosion", () -> config.hideImplosions, v -> config.hideImplosions = v);
        b.info("Only near players holding Hyperion/Astraea/Valkyrie/Scylla");
        b.info("Use with Hide Explosions off to keep other smoke");
        b.toggle("Hide Arrows", () -> config.noArrows, v -> config.noArrows = v);
        b.toggle("  Only Arrows In The Ground", () -> config.noArrowsGroundedOnly, v -> config.noArrowsGroundedOnly = v).fresh()
                .desc("Flying arrows stay visible.");
        b.toggle("Hide Stuck Arrows", () -> config.noStuckArrows, v -> config.noStuckArrows = v);
        b.toggle("Hide Death Animation", () -> config.noDeathAnimation, v -> config.noDeathAnimation = v);
        b.toggle("Hide Death Particles", () -> config.hideDeathParticles, v -> config.hideDeathParticles = v).fresh()
                .desc("No smoke puff when a mob dies.");
        b.toggle("Hide Witherborn Withers", () -> config.hideWitherbornWithers, v -> config.hideWitherbornWithers = v).fresh()
                .desc("Hides the withers spawned by the Wither armor set bonus. Dungeon boss withers stay.");
        b.toggle("Hide Armor On Armor Stands", () -> config.hideArmorStandArmor, v -> config.hideArmorStandArmor = v).fresh()
                .desc("Armor worn by armor stands (displays, shops) is not drawn. Heads stay.");
        b.label("Screen Overlays");
        b.toggle("Hide Fire Overlay", () -> config.noFire, v -> config.noFire = v);
        b.toggle("  Hide Fire On All Entities", () -> config.hideEntityFire, v -> config.hideEntityFire = v).fresh()
                .desc("Burning mobs and players are drawn without flames. Render only, nothing else changes.");
        b.toggle("  Shrink / Fade Instead Of Hide", () -> config.fireOverlayAdjust, v -> config.fireOverlayAdjust = v).fresh()
                .desc("Keeps the fire overlay on screen, with the height and opacity below.");
        b.slider("    Fire Height %", 0f, 100f, () -> config.fireOverlayHeight, v -> config.fireOverlayHeight = v, 0).fresh();
        b.slider("    Fire Opacity %", 0f, 100f, () -> config.fireOverlayOpacity, v -> config.fireOverlayOpacity = v, 0).fresh();
        b.toggle("Hide Water Overlay", () -> config.noWaterOverlay, v -> config.noWaterOverlay = v);
        b.toggle("Hide Vignette", () -> config.noVignette, v -> config.noVignette = v);
        b.toggle("Hide Blindness", () -> config.noBlind, v -> config.noBlind = v);
        b.slider("  Blindness Fog Opacity %", 0f, 100f, () -> config.blindnessFogOpacity, v -> config.blindnessFogOpacity = v).fresh()
                .desc("0 = blindness fully removed. Above 0 the effect stays and only the fog is faded to this opacity.");
        b.toggle("Hide Nausea", () -> config.noNausea, v -> config.noNausea = v);
        b.toggle("More Overlay Hiders", () -> config.moreOverlayHiders, v -> config.moreOverlayHiders = v).fresh();
        b.toggle("  Hide Portal Overlay", () -> config.hidePortalOverlay, v -> config.hidePortalOverlay = v).fresh();
        b.toggle("  Hide Pumpkin Overlay", () -> config.hidePumpkinOverlay, v -> config.hidePumpkinOverlay = v).fresh();
        b.toggle("  Hide In-Block Overlay", () -> config.hideInBlockOverlay, v -> config.hideInBlockOverlay = v).fresh()
                .desc("No block texture when your camera is inside a wall.");
        b.toggle("  No Underwater FOV Change", () -> config.noWaterFov, v -> config.noWaterFov = v).fresh();
        b.toggle("  Hide Wither Impact Particles", () -> config.hideWitherImpactParticles, v -> config.hideWitherImpactParticles = v).fresh()
                .desc("Drops witch and heart particles (Hyperion spam).");
        b.toggle("Hide Night Vision", () -> config.noNightVision, v -> config.noNightVision = v).fresh()
                .desc("The night vision effect no longer brightens the world.");
        b.label("Interface");
        b.toggle("Hide Inventory Effects", () -> config.hideInventoryEffects, v -> config.hideInventoryEffects = v);
        b.toggle("Hide Effects (HUD)", () -> config.noEffects, v -> config.noEffects = v);
        b.toggle("Hide Enchant Glint", () -> config.noEnchantGlint, v -> config.noEnchantGlint = v);
        b.toggle("  Only On Worn Armor", () -> config.noEnchantGlintArmorOnly, v -> config.noEnchantGlintArmorOnly = v).fresh()
                .desc("Items in inventory and hand keep their glint.");
        b.toggle("Force Enchant Glint", () -> config.forceEnchantGlint, v -> config.forceEnchantGlint = v).fresh()
                .desc("Every enchanted item glints, SkyBlock items included. Hide Enchant Glint wins over this.");
        b.toggle("Hide Item Durability", () -> config.hideItemDurability, v -> config.hideItemDurability = v).fresh();
        b.toggle("Hide Armor Bar", () -> config.hideArmorBar, v -> config.hideArmorBar = v);
        b.toggle("Hide Hunger Bar", () -> config.hideHungerBar, v -> config.hideHungerBar = v);
        b.toggle("Hide Item Cooldown Overlay", () -> config.hideItemCooldownOverlay, v -> config.hideItemCooldownOverlay = v).fresh()
                .desc("SkyBlock: no white cooldown shading on items (pearls, abilities). TeslaMaps' own Item/Ability Cooldowns still show.");
        b.toggle("Hide Hotbar Item Name", () -> config.hideHotbarItemName, v -> config.hideHotbarItemName = v).fresh()
                .desc("SkyBlock: no item name above the hotbar when switching slots.");
        b.toggle("Hide Health Hearts", () -> config.hideHealthBar, v -> config.hideHealthBar = v).fresh();
        b.toggle("Hide Air Bubbles", () -> config.hideAirBar, v -> config.hideAirBar = v).fresh();
        b.toggle("Hide Absorption Hearts", () -> config.hideAbsorption, v -> config.hideAbsorption = v).fresh()
                .desc("No yellow hearts above the health bar.");
        b.toggle("Hide Mount Health", () -> config.hideMountHealth, v -> config.hideMountHealth = v).fresh()
                .desc("Hearts of the horse or other mount you ride.");
        b.toggle("Hide Boss Bar", () -> config.hideBossBar, v -> config.hideBossBar = v).fresh()
                .desc("Hides every boss bar at the top of the screen, dungeon bosses included.");
        b.toggle("Hide XP Bar", () -> config.hideXpBar, v -> config.hideXpBar = v).fresh()
                .desc("XP bar and level number. Also hides the horse jump bar and locator bar in that slot.");
        b.toggle("Stop Hearts Bouncing", () -> config.stopHeartsBouncing, v -> config.stopHeartsBouncing = v).fresh()
                .desc("No regeneration wave over the health hearts.");
        b.label("Action Bar");
        b.toggle("Filter Action Bar", () -> config.actionBarFilter, v -> config.actionBarFilter = v);
        b.toggle("  Hide Health", () -> config.abHideHealth, v -> config.abHideHealth = v);
        b.toggle("  Hide Defense", () -> config.abHideDefense, v -> config.abHideDefense = v);
        b.toggle("  Hide Mana", () -> config.abHideMana, v -> config.abHideMana = v);
        b.toggle("  Hide Ability Mana Cost", () -> config.abHideAbilityMana, v -> config.abHideAbilityMana = v);
        b.toggle("  Hide Overflow Mana", () -> config.abHideOverflowMana, v -> config.abHideOverflowMana = v);
        b.toggle("  Hide Vitality", () -> config.abHideVitality, v -> config.abHideVitality = v);
        b.toggle("  Hide Armor Stacks", () -> config.abHideArmorStacks, v -> config.abHideArmorStacks = v);
        b.toggle("  Hide Location", () -> config.abHideLocation, v -> config.abHideLocation = v);
        b.toggle("  Hide Rift Time", () -> config.abHideRiftTime, v -> config.abHideRiftTime = v);
        b.toggle("  Hide Drill Fuel", () -> config.abHideDrillFuel, v -> config.abHideDrillFuel = v);
        b.toggle("  Hide Secrets", () -> config.abHideSecrets, v -> config.abHideSecrets = v);
        b.toggle("  Hide Skill XP", () -> config.abHideSkillXp, v -> config.abHideSkillXp = v);
        b.info("/tmap actionbar <text> hides any segment containing that text");
        b.info("/tmap debug actionbar prints the raw action bar + segments");
        b.label("Inventory");
        b.toggle("Hide Recipe Book Button", () -> config.hideRecipeBook, v -> config.hideRecipeBook = v);
        b.toggle("Container Tweaks", () -> config.containerTweaks, v -> config.containerTweaks = v).fresh()
                .desc("Small cosmetic clean-ups for inventory and chest menus.");
        b.toggle("  Remove Dark Background", () -> config.containerNoDim, v -> config.containerNoDim = v).fresh()
                .desc("No dark overlay behind open containers.");
        b.toggle("  Custom Container Color", () -> config.containerTint, v -> config.containerTint = v).fresh()
                .desc("Tints the chest and inventory texture.");
        b.color("  Container Color", () -> config.containerTintColor, v -> config.containerTintColor = v).fresh();
        b.toggle("  Hide Crafting Text", () -> config.hideCraftingText, v -> config.hideCraftingText = v).fresh()
                .desc("Hides the \"Crafting\" label in the inventory.");
        b.toggle("  No Filler Pane Highlight", () -> config.noFillerPaneHighlight, v -> config.noFillerPaneHighlight = v).fresh()
                .desc("No hover highlight on unnamed glass panes.");
        b.toggle("  Hide Offhand Slot Icon", () -> config.hideOffhandSlotIcon, v -> config.hideOffhandSlotIcon = v).fresh()
                .desc("Hides the shield placeholder in the empty offhand slot.");
        b.label("Camera");
        b.toggle("No Hurt Camera", () -> config.noHurtCam, v -> config.noHurtCam = v);
        b.slider("  Hurt Tilt Strength", 0f, 2f,
                () -> config.hurtCamStrength, v -> config.hurtCamStrength = v, 2).fresh()
                .desc("0 = no tilt, 1 = vanilla, 2 = double.");
    }
}
