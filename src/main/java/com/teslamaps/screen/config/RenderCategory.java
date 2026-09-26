package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class RenderCategory {

    private RenderCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("World");
        b.toggle("Fullbright", () -> config.fullbright, v -> config.fullbright = v);
        b.toggle("Night Vision Control", () -> config.nightVisionControl, v -> config.nightVisionControl = v)
                .desc("Dims Hypixel's permanent night vision. Only matters while Fullbright is off.");
        b.slider("  Night Vision Strength", 0f, 100f,
                () -> config.nightVisionStrength, v -> config.nightVisionStrength = v);
        b.toggle("  No Night Vision Blinking", () -> config.nightVisionNoBlink, v -> config.nightVisionNoBlink = v);
        b.label("Zoom");
        b.toggle("Zoom (hold key)", () -> config.zoomEnabled, v -> config.zoomEnabled = v);
        b.keybind("  Zoom Key", () -> config.zoomKey, v -> config.zoomKey = v);
        b.slider("  Zoom FOV", 1f, 110f,
                () -> (float) config.zoomFov, v -> config.zoomFov = v.intValue());
        b.toggle("  Smooth Zoom", () -> config.zoomSmooth, v -> config.zoomSmooth = v);
        b.slider("    Zoom Speed", 1f, 30f,
                () -> config.zoomSmoothSpeed, v -> config.zoomSmoothSpeed = v, 1);
        b.toggle("  Toggle Instead Of Hold", () -> config.zoomToggle, v -> config.zoomToggle = v);
        b.toggle("  Remember Scroll Adjustment", () -> config.zoomRememberScroll, v -> config.zoomRememberScroll = v);
        b.toggle("  Relative Sensitivity", () -> config.zoomRelativeSensitivity, v -> config.zoomRelativeSensitivity = v);
        b.toggle("  Hide Hand While Zoomed", () -> config.zoomHideHand, v -> config.zoomHideHand = v);
        b.label("Motion Blur");
        b.toggle("Motion Blur", () -> config.motionBlur, v -> config.motionBlur = v);
        b.slider("  Blur Strength", 0f, 0.9f,
                () -> config.motionBlurStrength, v -> config.motionBlurStrength = v, 3);
        b.toggle("  Skip While A Menu Is Open", () -> config.motionBlurWorldOnly, v -> config.motionBlurWorldOnly = v);
        b.label("Smoothness");
        b.toggle("Smooth Entity Movement", () -> config.smoothEntities, v -> config.smoothEntities = v);
        b.slider("  Interpolation Ticks", 3f, 12f,
                () -> (float) config.smoothEntitySteps, v -> config.smoothEntitySteps = v.intValue());
        b.label("HUD");
        b.dropdown("  HUD Style",
                com.teslamaps.render.gui.HudStyle.NAMES,
                () -> com.teslamaps.render.gui.HudStyle.current(), v -> config.hudStyle = v);
        b.toggle("Sprint Indicator (HUD)", () -> config.sprintingOverlay, v -> config.sprintingOverlay = v);
        b.label("Dark Mode");
        b.toggle("Dark Mode", () -> config.darkMode, v -> config.darkMode = v);
        b.slider("  Opacity", 1f, 100f,
                () -> (float) config.darkModeOpacity, v -> config.darkModeOpacity = v.intValue());
        b.toggle("  Tint HUD", () -> config.darkModeTintHud, v -> config.darkModeTintHud = v);
        b.label("Loading Screens");
        b.toggle("Disable World Loading Screen", () -> config.disableWorldLoadingScreen, v -> config.disableWorldLoadingScreen = v);
        b.toggle("  Seamless Server Switch", () -> config.seamlessServerSwitch, v -> config.seamlessServerSwitch = v)
                .desc("Hides Hypixel's Reconfiguring screen and keeps the cursor captured, so a /warp looks like a teleport");
        b.toggle("No Texture Reload Screen", () -> config.noReloadScreen, v -> config.noReloadScreen = v);
        b.label("Beacons & Lines");
        b.toggle("Real Beacon Beams", () -> config.realBeaconBeam, v -> config.realBeaconBeam = v);
        b.toggle("Glow Ribbons (Tracers & Lines)", () -> config.glowRibbons, v -> config.glowRibbons = v);
        b.slider("  Ribbon Width", 0.5f, 4f, () -> config.glowRibbonWidth, v -> config.glowRibbonWidth = v, 1);
        b.toggle("Waypoint Label Background", () -> config.waypointLabelBackground, v -> config.waypointLabelBackground = v);
        b.label("Block Overlay");
        b.toggle("Block Overlay", () -> config.blockOverlay, v -> config.blockOverlay = v);
        b.color("  Block Overlay Color", () -> config.blockOverlayColor, v -> config.blockOverlayColor = v);
        b.dropdown("  Block Overlay Style", com.teslamaps.features.BlockOverlay.STYLES,
                () -> config.blockOverlayStyle, v -> config.blockOverlayStyle = v);
        b.color("  Block Overlay Fill Color", () -> config.blockOverlayFillColor, v -> config.blockOverlayFillColor = v);
        b.slider("  Block Overlay Line Width", 0.5f, 10f, () -> config.blockOverlayLineWidth, v -> config.blockOverlayLineWidth = v, 1);
        b.toggle("  Block Overlay Through Walls", () -> config.blockOverlayThroughWalls, v -> config.blockOverlayThroughWalls = v);
        b.toggle("  Exact Block Shape", () -> config.blockOverlayExactShape, v -> config.blockOverlayExactShape = v);
        b.toggle("  Hide Vanilla Outline", () -> config.blockOverlayHideVanilla, v -> config.blockOverlayHideVanilla = v);
        b.toggle("  Hide While Etherwarping", () -> config.blockOverlayHideEtherwarp, v -> config.blockOverlayHideEtherwarp = v);
        b.label("Chat Waypoint");
        b.toggle("Chat Waypoint (coords)", () -> config.chatWaypoint, v -> config.chatWaypoint = v);
        b.toggle("  More Formats (x y z, Inquisitor)", () -> config.chatWaypointMoreFormats, v -> config.chatWaypointMoreFormats = v);
        b.toggle("  Ignore All-Chat", () -> config.chatWaypointIgnoreAllChat, v -> config.chatWaypointIgnoreAllChat = v);
        b.toggle("  Ignore Own Messages", () -> config.chatWaypointIgnoreOwn, v -> config.chatWaypointIgnoreOwn = v);
        b.toggle("  Show Sender, Area, Note & Distance", () -> config.chatWaypointDetails, v -> config.chatWaypointDetails = v);
        b.toggle("  Remove On Arrival (5 blocks)", () -> config.chatWaypointRemoveOnArrival, v -> config.chatWaypointRemoveOnArrival = v);
        b.color("  Chat Waypoint Color", () -> config.chatWaypointColor, v -> config.chatWaypointColor = v);
        b.toggle("  /tmap sendcoords Adds Area", () -> config.chatWaypointShareArea, v -> config.chatWaypointShareArea = v);
        b.dropdown("  /tmap sendcoords Channel", new String[] {"Party", "Current", "All", "Guild", "Coop"},
                () -> config.sendCoordsChannel, v -> config.sendCoordsChannel = v)
                .desc("Where /tmap sendcoords [text] goes. Current = the chat you are in");
        b.toggle("  /tmap wp Commands", () -> config.chatWaypointCommands, v -> config.chatWaypointCommands = v)
                .desc("/tmap wp addtemp [name] [x y z] sets a 60s beam here or at the coords, /tmap wp share [x y z] posts coords in chat, /tmap wp clear removes all");
        b.label("NPC Finder");
        b.toggle("NPC Finder (/tmap npc)", () -> config.npcFinder, v -> config.npcFinder = v);
        b.toggle("  Use Nearest Unlocked Warp", () -> config.npcNearestWarp, v -> config.npcNearestWarp = v);
        b.info("Learns locked warps from the 'not unlocked' chat message");
        b.toggle("  Warp Twice", () -> config.npcWarpTwice, v -> config.npcWarpTwice = v);
        b.keybind("  NPC Waypoint Key", () -> config.npcWaypointKey, v -> config.npcWaypointKey = v);
        b.info("Over an NPC item (Abiphone): track, Shift: also warp");
        b.info("In the world: open the finder, Shift: warp to the target");
        b.toggle("  Visitor's Logbook Navigation", () -> config.visitorLogbookNav, v -> config.visitorLogbookNav = v)
                .desc("In the Visitor's Logbook a key over a visitor tracks it, or warps to its island first")
                .tags("visitor", "logbook", "garden", "navigate");
        b.keybind("    Logbook Key", () -> config.visitorLogbookKey, v -> config.visitorLogbookKey = v);
        b.label("Rare Drop Effects");
        b.toggle("Rare Drops", () -> config.rareDropEffects, v -> config.rareDropEffects = v);
        b.dropdown("  Post Drops To", com.teslamaps.features.RareDropEffects.ANNOUNCE,
                () -> config.rareDropAnnounce, v -> config.rareDropAnnounce = v)
                .desc("Sends the RARE DROP line with a per-item counter to party and/or guild chat");
        b.slider("    Only Over (Mio, 0 = all)", 0f, 100f,
                () -> config.rareDropAnnounceMin, v -> config.rareDropAnnounceMin = v, 1);
        b.toggle("  Copy Drop Line To Clipboard", () -> config.rareDropCopy, v -> config.rareDropCopy = v);
        b.dropdown("  Burrowing Spores Alarm", com.teslamaps.features.RareDropEffects.SPORES_ALARM,
                () -> config.burrowingSporesAlarm, v -> config.burrowingSporesAlarm = v)
                .desc("VERY RARE CROP! Burrowing Spores: title and/or the big item pop. Works on its own, Rare Drops can stay off")
                .tags("garden", "rare crop", "spores");
        b.toggle("  Snow Minion Gift Alert", () -> config.snowMinionGiftAlert, v -> config.snowMinionGiftAlert = v)
                .desc("Big aqua \"Snow Minion!\" title and a beep when a gift gives a Snow Minion (SWEET! line)")
                .tags("gift", "jerry", "winter", "snow minion");
        b.toggle("Dungeon Chest Rewards", () -> config.rareDungeonDropEffects, v -> config.rareDungeonDropEffects = v);
        b.toggle("Dye Drops", () -> config.rareDyeDropEffects, v -> config.rareDyeDropEffects = v);
        b.toggle("Dungeon Ground Drops", () -> config.rareGroundDrops, v -> config.rareGroundDrops = v)
                .desc("Title + sound when an Ice Spray Wand or Skeleton Master Chestplate drops on the ground");
        b.toggle("  SM Chestplate On All Floors", () -> config.rareGroundDropsSmAllFloors, v -> config.rareGroundDropsSmAllFloors = v)
                .desc("Off = only in M7");
        b.slider("  Title Time (s)", 0.5f, 5f, () -> config.rareGroundDropsTime, v -> config.rareGroundDropsTime = v, 1);
        b.toggle("Show Item Name", () -> config.rareDropText, v -> config.rareDropText = v);
        b.toggle("Show Item Value", () -> config.rareDropPrice, v -> config.rareDropPrice = v);
        b.slider("Any Drop Worth Over (Mio)", 0f, 50f,
                () -> config.rareDropMinValue, v -> config.rareDropMinValue = v, 1);
        b.info("0 = off. Plays the effect for any RARE DROP over this value");
        b.toggle("Valuable Drop Warning (Trackers)", () -> config.valuableDropWarn, v -> config.valuableDropWarn = v)
                .desc("Any tracker gain or [Sacks] gain over the value, no RARE DROP line needed")
                .tags("expensive", "item warning", "tracker");
        b.slider("  Chat Line Over (Mio)", 0.1f, 50f,
                () -> config.valuableDropChatValue, v -> config.valuableDropChatValue = v, 1);
        b.slider("  Title Over (Mio, 0 = off)", 0f, 200f,
                () -> config.valuableDropTitleValue, v -> config.valuableDropTitleValue = v, 1);
        b.label("Etherwarp");
        b.toggle("Etherwarp Guess Box", () -> config.etherwarp, v -> config.etherwarp = v);
        b.toggle("  Show When Failed", () -> config.etherwarpShowFail, v -> config.etherwarpShowFail = v);
        b.toggle("  Fail On Skull", () -> config.etherwarpSkullFail, v -> config.etherwarpSkullFail = v);
        b.toggle("  Filled Box", () -> config.etherwarpFilled, v -> config.etherwarpFilled = v);
        b.toggle("    Outline On Filled Box", () -> config.etherwarpOutline, v -> config.etherwarpOutline = v);
        b.slider("  Eye Height Offset", -0.5f, 0.5f,
                () -> config.etherwarpEyeOffset, v -> config.etherwarpEyeOffset = v, 3);
        b.color("  Etherwarp Box Color", () -> config.colorEtherwarp, v -> config.colorEtherwarp = v);
        b.color("  Etherwarp Fail Color", () -> config.colorEtherwarpFail, v -> config.colorEtherwarpFail = v);
        b.slider("  Etherwarp Line Width", 1f, 8f, () -> config.etherwarpLineWidth, v -> config.etherwarpLineWidth = v, 1);
        b.toggle("  Etherwarp Through Walls", () -> config.etherwarpThroughWalls, v -> config.etherwarpThroughWalls = v);
        b.toggle("  Use Block Shape", () -> config.etherwarpBlockShape, v -> config.etherwarpBlockShape = v);
        b.toggle("  Use Server Position", () -> config.etherwarpServerPos, v -> config.etherwarpServerPos = v);
        b.toggle("  Fail Reason HUD", () -> config.etherwarpFailReason, v -> config.etherwarpFailReason = v);
        b.label("Cape");
        b.toggle("Custom Cape", () -> config.capeEnabled, v -> config.capeEnabled = v);
        b.info("Worn capes are visible to every TeslaMaps user");
        b.slider("  Waviness", 0f, 1f,
                () -> config.capeWaviness, v -> config.capeWaviness = v, 2);
        b.button("Choose Cape",
                () -> Minecraft.getInstance().setScreen(new CapeScreen(b.screen())));
    }
}
