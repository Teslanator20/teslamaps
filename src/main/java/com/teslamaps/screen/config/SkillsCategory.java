package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class SkillsCategory {

    private SkillsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Skill Tracker");
        b.toggle("Tracker HUD (XP/h and skill rates)", () -> config.skillTracker, v -> config.skillTracker = v);
        b.toggle("  Tracker HUD only in Inventory", () -> config.skillTrackerHidden, v -> config.skillTrackerHidden = v);
        b.toggle("  Slayer Stats (avg kill/spawn, slayer XP/h)", () -> config.skillTrackerSlayerStats, v -> config.skillTrackerSlayerStats = v)
                .desc("Combat panel: average kill and spawn time and slayer XP/h (Aatrox buff included). Resets when the slayer type changes");
        String[] pinOptions = new String[com.teslamaps.features.SkillTracker.skills().length + 1];
        pinOptions[0] = "Auto";
        System.arraycopy(com.teslamaps.features.SkillTracker.skills(), 0, pinOptions, 1, pinOptions.length - 1);
        b.dropdown("  Tracker Skill", pinOptions,
                () -> config.skillTrackerPin == null || config.skillTrackerPin.isEmpty() ? "Auto" : config.skillTrackerPin,
                v -> config.skillTrackerPin = "Auto".equals(v) ? "" : v);
        b.info("Auto follows the XP, a skill keeps the panel on it");
        b.toggle("  Pause Threshold per Skill", () -> config.skillTrackerPauseCustom, v -> config.skillTrackerPauseCustom = v)
                .desc("Idle time before the session clock stops, per skill. Shows (PAUSED) while idle and a level-cap hint");
        String[] pauseSkills = com.teslamaps.features.SkillTracker.skills();
        String[] pauseSel = {pauseSkills[0]};
        b.dropdown("    Threshold Skill", pauseSkills, () -> pauseSel[0], v -> pauseSel[0] = v);
        b.slider("    Pause After (s)", 5f, 300f,
                () -> (float) (com.teslamaps.features.SkillTracker.idleCapMs(pauseSel[0]) / 1000L),
                v -> config.skillTrackerPauseSec.put(pauseSel[0], Math.round(v)), 0);
        b.label("Flare Mode");
        b.toggle("Flare Mode HUD", () -> config.flareMode, v -> config.flareMode = v);
        b.info("Hunting and Combat at once, plus the flare bestiary count");
        b.info("3x check reads the Book of Stats kill counter on the held weapon");
        b.info("Open the Bestiary menu once for the absolute kill total");
        b.toggle("  Chat Warning on 1x", () -> config.flareWarnChat, v -> config.flareWarnChat = v);
        b.toggle("  Warning Sound", () -> config.flareWarnSound, v -> config.flareWarnSound = v);
        b.slider("  Warning Interval (s)", 5f, 120f,
                () -> (float) config.flareWarnSeconds, v -> config.flareWarnSeconds = Math.round(v), 0);
        b.info("/tmap flare on|off · pause · reset · mob <name>");
        b.info("[Pause]/[Reset] sit on the panel while the inventory is open");
        b.label("Pet");
        b.toggle("Pet HUD", () -> config.petOverlay, v -> config.petOverlay = v);
        b.info("Equipped pet with level, XP progress and held item");
        b.info("Needs the Pet tab widget (/widget). Move it in /tmap gui");
        b.info("Open /pets once: rarity, skin and exact XP are then remembered per profile");
        b.toggle("  Skin Icon", () -> config.petOverlaySkinIcon, v -> config.petOverlaySkinIcon = v);
        b.toggle("  XP Ring Around Icon", () -> config.petOverlayRing, v -> config.petOverlayRing = v);
        b.toggle("  XP/h Line", () -> config.petOverlayXpRate, v -> config.petOverlayXpRate = v);
        b.toggle("  Time to Next / Max Level", () -> config.petOverlayEta, v -> config.petOverlayEta = v);
        b.toggle("  Show Last Pet", () -> config.petOverlayLastPet, v -> config.petOverlayLastPet = v);
        b.label("Skill XP Display");
        b.toggle("Skill XP Display", () -> config.skillExpDisplay, v -> config.skillExpDisplay = v);
        b.info("Cuts the +XP out of the action bar into its own HUD element");
        b.toggle("  Skill Icon", () -> config.skillExpIcon, v -> config.skillExpIcon = v);
        b.toggle("  Level Prefix", () -> config.skillExpLevel, v -> config.skillExpLevel = v);
        String[] xpFormats = {"As Sent", "Percent", "XP / Needed"};
        b.dropdown("  Progress Format", xpFormats,
                () -> xpFormats[Math.max(0, Math.min(2, config.skillExpFormat))],
                v -> config.skillExpFormat = java.util.Arrays.asList(xpFormats).indexOf(v));
        b.toggle("  Actions Left", () -> config.skillExpActionsLeft, v -> config.skillExpActionsLeft = v);
        b.toggle("  Progress Bar", () -> config.skillExpBar, v -> config.skillExpBar = v);
        b.slider("    Bar Width", 40f, 250f, () -> (float) config.skillExpBarWidth, v -> config.skillExpBarWidth = Math.round(v), 0);
        b.color("    Bar Color", () -> config.skillExpBarColor, v -> config.skillExpBarColor = v);
        b.info("Level, Percent <-> XP and Actions Left need the Skills tab widget");
        b.info("Move it in /tmap gui");
        b.info("Keeps tracking either way — [Hide]/[Show] in the panel does the same");
        b.toggle("Overflow Levels (menu + chat)", () -> config.skillOverflow, v -> config.skillOverflow = v);
        b.toggle("  Crop Milestones Too", () -> config.cropMilestoneOverflow, v -> config.cropMilestoneOverflow = v)
                .desc("Crop Milestones menu: overflow tier past 46 with progress bar and tier as stack size, "
                        + "plus a chat message on every overflow milestone. Open the menu once to load your crops.")
                .tags("garden", "milestone", "farming");
        b.toggle("    Average Crop Milestone", () -> config.cropMilestoneAverage, v -> config.cropMilestoneAverage = v);
        b.toggle("  Tier On Crop / Composter Upgrades", () -> config.gardenUpgradeStackTier, v -> config.gardenUpgradeStackTier = v)
                .tags("garden", "composter");
        b.toggle("Overflow Skill Avg. (menu tooltip)", () -> config.skillAvgOverflow, v -> config.skillAvgOverflow = v);
        b.toggle("Level as Stack Size (skills menu)", () -> config.skillLevelStackSize, v -> config.skillLevelStackSize = v);
        b.toggle("  Also Dungeon Menus (Cata/Class/SB Level)", () -> config.dungeonLevelSlotText, v -> config.dungeonLevelSlotText = v)
                .desc("Dungeoneering, Dungeon Classes, Ready Up (party class levels) and the SkyBlock Menu profile head");
        b.info("Combat adds kills/bosses/cocoons, Fishing double hooks, Foraging gifts and whispers");
        String[] catchSources = {"Auto", "Sea Creatures", "Every Catch"};
        b.dropdown("Fishing Counts", catchSources,
                () -> catchSources[Math.max(0, Math.min(2, config.fishingCatchSource))],
                v -> config.fishingCatchSource = switch (v) {
                    case "Sea Creatures" -> 1;
                    case "Every Catch" -> 2;
                    default -> 0;
                });
        b.info("Sea Creatures = chat lines only, Every Catch = counts by Fishing XP");
        b.info("Auto counts creatures while they keep coming in, otherwise every catch");
        b.info("The panel shows the session time and a Reset button in the inventory");
        b.info("Open the Skills menu once so the tracker knows your total XP");
        b.label("Deployables");
        b.toggle("Deployable Overlay",
                () -> config.deployableOverlay, v -> config.deployableOverlay = v);
        b.info("Time left on power orbs, lanterns, Umberella");
        b.toggle("  Only Highest Tier",
                () -> config.deployableHighestTierOnly, v -> config.deployableHighestTierOnly = v);
        b.toggle("  Big Icon (strongest only)",
                () -> config.deployableIconMode, v -> config.deployableIconMode = v);
        b.toggle("    Spinning 3D Orb",
                () -> config.deployableOrb3d, v -> config.deployableOrb3d = v)
                .desc("Renders the orb/lantern itself, rotating, instead of the flat head icon.");
        b.toggle("  Power Orbs",
                () -> config.deployableFlux, v -> config.deployableFlux = v);
        b.toggle("  Lanterns",
                () -> config.deployableLantern, v -> config.deployableLantern = v);
        b.toggle("  Umberella",
                () -> config.deployableUmbrella, v -> config.deployableUmbrella = v);
        b.toggle("  Black Hole",
                () -> config.deployableBlackHole, v -> config.deployableBlackHole = v);
        b.toggle("  Flares",
                () -> config.deployableFlare, v -> config.deployableFlare = v);
        b.info("Flares have no nametag, the timer starts when you see them");
        b.toggle("  Other Deployables (flares, ...)",
                () -> config.deployableOthers, v -> config.deployableOthers = v);
        b.toggle("  Totem of Corruption",
                () -> config.deployableTotem, v -> config.deployableTotem = v)
                .desc("Time left and owner of your own or the nearest Totem of Corruption, plus its 16 block range")
                .tags("totem", "corruption", "fishing");
        b.toggle("    Only Own Totem",
                () -> config.deployableTotemOwnOnly, v -> config.deployableTotemOwnOnly = v);
        b.dropdown("    Range Display", new String[] {"Off", "Wireframe", "Ring At Feet"},
                () -> config.deployableTotemRange, v -> config.deployableTotemRange = v);
        b.color("    Range Color",
                () -> config.colorTotemRange, v -> config.colorTotemRange = v);
        b.slider("    Expiry Warning (s, 0 = off)", 0, 60,
                () -> (float) config.deployableTotemWarn, v -> config.deployableTotemWarn = Math.round(v), 0);
        b.toggle("    Hide Witch Particles",
                () -> config.deployableTotemHideParticles, v -> config.deployableTotemHideParticles = v);
        b.toggle("  Soulflow Display",
                () -> config.soulflowDisplay, v -> config.soulflowDisplay = v)
                .desc("Internalized soulflow from your Soulflow Pile / Battery / Supercell. Position in /tmap gui");
        b.toggle("  Low Soulflow Warning",
                () -> config.lowSoulflowWarning, v -> config.lowSoulflowWarning = v)
                .desc("\"Low Soulflow\" title once it drops to the threshold");
        b.slider("    Threshold", 0, 1000, () -> (float) config.lowSoulflowThreshold,
                v -> config.lowSoulflowThreshold = Math.round(v), 0);
        b.info("Position and scale in /tmap gui");
        b.toggle("Hide Deployable Particles",
                () -> config.deployableHideParticles, v -> config.deployableHideParticles = v);
        b.label("Fixes");
        b.toggle("Fix Item Double Clicks",
                () -> config.fixDoubleClicks, v -> config.fixDoubleClicks = v);
        b.info("Hypixel fires blaze daggers and autopet rods twice on blocks");
    }
}
