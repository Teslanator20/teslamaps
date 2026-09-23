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
        b.label("Zoom");
        b.toggle("Zoom (hold key)", () -> config.zoomEnabled, v -> config.zoomEnabled = v);
        b.keybind("  Zoom Key", () -> config.zoomKey, v -> config.zoomKey = v);
        b.slider("  Zoom FOV", 1f, 110f,
                () -> (float) config.zoomFov, v -> config.zoomFov = v.intValue());
        b.toggle("  Smooth Zoom", () -> config.zoomSmooth, v -> config.zoomSmooth = v);
        b.slider("  Zoom Speed", 1f, 30f,
                () -> config.zoomSmoothSpeed, v -> config.zoomSmoothSpeed = v, 1);
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
        b.toggle("  Show Sender, Area & Distance", () -> config.chatWaypointDetails, v -> config.chatWaypointDetails = v);
        b.toggle("  Remove On Arrival (5 blocks)", () -> config.chatWaypointRemoveOnArrival, v -> config.chatWaypointRemoveOnArrival = v);
        b.color("  Chat Waypoint Color", () -> config.chatWaypointColor, v -> config.chatWaypointColor = v);
        b.toggle("  /tmap sendcoords Adds Area", () -> config.chatWaypointShareArea, v -> config.chatWaypointShareArea = v);
        b.label("NPC Finder");
        b.toggle("NPC Finder (/tmap npc)", () -> config.npcFinder, v -> config.npcFinder = v);
        b.label("Rare Drop Effects");
        b.toggle("Rare Drops", () -> config.rareDropEffects, v -> config.rareDropEffects = v);
        b.toggle("Dungeon Chest Rewards", () -> config.rareDungeonDropEffects, v -> config.rareDungeonDropEffects = v);
        b.toggle("Dye Drops", () -> config.rareDyeDropEffects, v -> config.rareDyeDropEffects = v);
        b.toggle("Show Item Name", () -> config.rareDropText, v -> config.rareDropText = v);
        b.toggle("Show Item Value", () -> config.rareDropPrice, v -> config.rareDropPrice = v);
        b.slider("Any Drop Worth Over (Mio)", 0f, 50f,
                () -> config.rareDropMinValue, v -> config.rareDropMinValue = v, 1);
        b.info("0 = off. Plays the effect for any RARE DROP over this value");
        b.label("Etherwarp");
        b.toggle("Etherwarp Guess Box", () -> config.etherwarp, v -> config.etherwarp = v);
        b.toggle("  Show When Failed", () -> config.etherwarpShowFail, v -> config.etherwarpShowFail = v);
        b.toggle("  Fail On Skull", () -> config.etherwarpSkullFail, v -> config.etherwarpSkullFail = v);
        b.toggle("  Filled Box", () -> config.etherwarpFilled, v -> config.etherwarpFilled = v);
        b.slider("  Eye Height Offset", -0.5f, 0.5f,
                () -> config.etherwarpEyeOffset, v -> config.etherwarpEyeOffset = v, 3);
        b.color("  Etherwarp Box Color", () -> config.colorEtherwarp, v -> config.colorEtherwarp = v);
        b.color("  Etherwarp Fail Color", () -> config.colorEtherwarpFail, v -> config.colorEtherwarpFail = v);
        b.toggle("  Outline On Filled Box", () -> config.etherwarpOutline, v -> config.etherwarpOutline = v);
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
