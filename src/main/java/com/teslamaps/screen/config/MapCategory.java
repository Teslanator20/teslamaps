package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class MapCategory {

    private MapCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("General");
        b.toggle("Show Map", () -> config.mapEnabled, v -> config.mapEnabled = v);
        b.toggle("Only In Dungeon", () -> config.onlyShowInDungeon, v -> config.onlyShowInDungeon = v);
        b.toggle("Only In Boss", () -> config.mapOnlyInBoss, v -> config.mapOnlyInBoss = v);
        b.toggle("Hide In Boss", () -> config.mapHideInBoss, v -> config.mapHideInBoss = v);
        b.toggle("Show Background", () -> config.showMapBackground, v -> config.showMapBackground = v);
        b.toggle("Background Image", () -> config.mapBgImage, v -> config.mapBgImage = v)
                .desc("Your own picture behind the map, from config/teslamaps/map_backgrounds");
        b.dropdown("  Image", com.teslamaps.render.gui.MapImage.names(),
                () -> config.mapBgImageName, v -> config.mapBgImageName = v);
        b.slider("  Image Opacity", 0.0f, 1.0f, () -> config.mapBgImageAlpha, v -> config.mapBgImageAlpha = v, 2);
        b.slider("  Image Inset", 0.0f, 10.0f, () -> config.mapBgImageInset, v -> config.mapBgImageInset = v, 0);
        b.button("  Open Image Folder", com.teslamaps.render.gui.MapImage::openFolder);
        b.toggle("Center Small Floors", () -> config.mapCenterSmallFloors, v -> config.mapCenterSmallFloors = v);
        b.toggle("Hypixel Map Fallback", () -> config.mapHypixelFallback, v -> config.mapHypixelFallback = v);
        b.toggle("  Use Room Colors (Typed)", () -> config.mapFallbackTyped, v -> config.mapFallbackTyped = v);
        b.toggle("Star Mob Radar", () -> config.starMobRadar, v -> config.starMobRadar = v);
        b.slider("  Radar Size", 0.5f, 2.5f, () -> config.starMobRadarSize, v -> config.starMobRadarSize = v);
        b.toggle("  Mob Heads", () -> config.starMobRadarHeads, v -> config.starMobRadarHeads = v);
        b.toggle("  Radar Border", () -> config.starMobRadarBorder, v -> config.starMobRadarBorder = v);
        b.toggle("  Miniboss Colors", () -> config.starMobRadarMinibossColors, v -> config.starMobRadarMinibossColors = v);
        b.color("  Radar Dot Color", () -> config.colorStarMobRadar, v -> config.colorStarMobRadar = v);
        b.color("  Radar Border Color", () -> config.colorStarMobRadarBorder, v -> config.colorStarMobRadarBorder = v);
        b.toggle("Show Mimic Status", () -> config.showMimicStatus, v -> config.showMimicStatus = v);
        b.toggle("Show Prince Icon", () -> config.showPrinceIcon, v -> config.showPrinceIcon = v);
        b.toggle("Show Prince Status", () -> config.showPrinceStatus, v -> config.showPrinceStatus = v);
        b.toggle("Prince ESP (structure)", () -> config.princeESP, v -> config.princeESP = v);
        b.color("  Prince ESP Color", () -> config.colorPrinceESP, v -> config.colorPrinceESP = v);
        b.toggle("Show Dungeon Score", () -> config.showDungeonScore, v -> config.showDungeonScore = v);
        b.toggle("  Blaze Done From Party Chat", () -> config.mapBlazeDoneFromChat, v -> config.mapBlazeDoneFromChat = v)
                .desc("A party member's \"Blaze Done\" marks the Blaze puzzle solved on map and score");
        b.toggle("Chat Info After Scan", () -> config.mapScanChatInfo, v -> config.mapScanChatInfo = v)
                .desc("Puzzles, trap, wither doors, crypts, secrets and minimum secrets once the dungeon is scanned");
        b.label("Style");
        b.buttonRow(new String[]{"Doc Style", "Leg Style", "Default Style"},
                new Runnable[]{com.teslamaps.map.MapStyle::applyDoc, com.teslamaps.map.MapStyle::applyLeg,
                        com.teslamaps.map.MapStyle::applyDefault});
        b.buttonRow(new String[]{"Export Style", "Import Style"},
                new Runnable[]{com.teslamaps.map.MapStyle::exportToClipboard, com.teslamaps.map.MapStyle::importFromClipboard});
        b.slider("Room Size", 12f, 27f, () -> config.mapRoomSize, v -> config.mapRoomSize = (float) Math.round(v), 0);
        b.slider("Door Width", 2f, 20f, () -> config.mapDoorWidth, v -> config.mapDoorWidth = (float) Math.round(v), 0);
        b.toggle("Normal Doors In Room Color", () -> config.mapDoorsRoomColor, v -> config.mapDoorsRoomColor = v);
        b.dropdown("Text Shadow", new String[]{"Drop", "None", "Outline"},
                () -> config.mapTextShadow, v -> config.mapTextShadow = v);
        b.dropdown("Room Name Position", new String[]{"Center L", "Center", "Top Left", "Top Right", "Bottom Left", "Bottom Right"},
                () -> config.mapNameAlign, v -> config.mapNameAlign = v);
        b.dropdown("Map Border", new String[]{"Default", "Solid", "Chroma", "None"},
                () -> config.mapBorderStyle, v -> config.mapBorderStyle = v);
        b.color("  Border Color", () -> config.colorMapBorder, v -> config.colorMapBorder = v);
        b.slider("  Border Width", 0.5f, 10f, () -> config.mapBorderWidth, v -> config.mapBorderWidth = v);
        b.slider("  Chroma Speed", 0.1f, 5f, () -> config.mapBorderChromaSpeed, v -> config.mapBorderChromaSpeed = v);
        b.label("Rooms");
        b.toggle("Show Room Names", () -> config.showRoomNames, v -> config.showRoomNames = v);
        b.toggle("  Hide Entrance/Blood/Fairy Names", () -> config.hideEntranceBloodFairyNames, v -> config.hideEntranceBloodFairyNames = v);
        b.toggle("  Show Names Only for Puzzles", () -> config.showNamesOnlyForPuzzles, v -> config.showNamesOnlyForPuzzles = v);
        b.toggle("  Room Names Only When Leap", () -> config.roomNamesOnlyWithLeap, v -> config.roomNamesOnlyWithLeap = v);
        b.toggle("Player Names Only When Leap", () -> config.playerNamesOnlyWithLeap, v -> config.playerNamesOnlyWithLeap = v);
        b.toggle("Head Border (Class Color)", () -> config.mapHeadBorder, v -> config.mapHeadBorder = v);
        b.slider("Room Name Scale", 0.5f, 2.0f,
                () -> config.roomNameScale, v -> config.roomNameScale = v);
        b.slider("Info Text Scale", 0.5f, 2.0f,
                () -> config.infoTextScale, v -> config.infoTextScale = v);
        b.toggle("Show Secrets", () -> config.showSecretCount, v -> config.showSecretCount = v);
        b.toggle("  Hide 1 Secret Count", () -> config.hideOneSecretCount, v -> config.hideOneSecretCount = v);
        b.toggle("  Hide When Done", () -> config.hideSecretsWhenDone, v -> config.hideSecretsWhenDone = v);
        b.toggle("Show Crypts", () -> config.showCrypts, v -> config.showCrypts = v);
        b.toggle("  Show Total Crypts", () -> config.showTotalCrypts, v -> config.showTotalCrypts = v);
        b.toggle("Assume Paul Mayor", () -> config.assumePaulMayor, v -> config.assumePaulMayor = v);
        b.toggle("  Auto-detect Paul (Election API)", () -> config.paulAutoDetect, v -> config.paulAutoDetect = v);
        b.toggle("Check Spirit Pet on First Death", () -> config.spiritPetCheck, v -> config.spiritPetCheck = v);
        b.keybind("Peek Key (all names + secrets)", () -> config.mapPeekKey, v -> config.mapPeekKey = v);
        b.toggle("  Peek Key Toggles (instead of hold)", () -> config.mapPeekToggle, v -> config.mapPeekToggle = v);
        b.label("Checkmarks");
        b.toggle("Show Checkmarks", () -> config.showCheckmarks, v -> config.showCheckmarks = v);
        b.toggle("  White Checks", () -> config.showWhiteCheckmarks, v -> config.showWhiteCheckmarks = v);
        b.toggle("  Green Checks", () -> config.showGreenCheckmarks, v -> config.showGreenCheckmarks = v);
        b.toggle("  Failed Checks", () -> config.showFailedCheckmarks, v -> config.showFailedCheckmarks = v);
        b.toggle("  White Blood Check When Watcher Done", () -> config.mapWhiteCheckBlood, v -> config.mapWhiteCheckBlood = v);
        b.toggle("  Checkmark Icons", () -> config.mapCheckmarkIcons, v -> config.mapCheckmarkIcons = v);
        b.dropdown("    Icon Style", new String[]{"Symbol", "IllegalMap", "NEU", "Vanilla New", "Vanilla Old"},
                () -> config.mapCheckmarkStyle, v -> config.mapCheckmarkStyle = v);
        b.slider("    Icon Size", 0.2f, 1.0f, () -> config.mapCheckmarkSize, v -> config.mapCheckmarkSize = v);
        b.toggle("    Center In Room", () -> config.mapCheckmarkCenter, v -> config.mapCheckmarkCenter = v);
        b.toggle("    Question Mark On Unentered", () -> config.mapCheckmarkUnopened, v -> config.mapCheckmarkUnopened = v);
        b.toggle("    Hide When Name Shown", () -> config.mapCheckmarkHideWithName, v -> config.mapCheckmarkHideWithName = v);
        b.dropdown("    Icon Position", new String[]{"Default", "Top Left", "Top Right", "Bottom Left", "Bottom Right"},
                () -> config.mapCheckmarkAlign, v -> config.mapCheckmarkAlign = v);
        b.label("Players");
        b.toggle("Player Markers", () -> config.showPlayerMarker, v -> config.showPlayerMarker = v);
        b.toggle("Show Self", () -> config.showSelfMarker, v -> config.showSelfMarker = v);
        b.toggle("Show Others", () -> config.showOtherPlayers, v -> config.showOtherPlayers = v);
        b.toggle("  Interpolate Far Players", () -> config.mapInterpolatePlayers, v -> config.mapInterpolatePlayers = v)
                .desc("Markers of out-of-range players glide instead of jumping");
        b.toggle("Show Names", () -> config.showPlayerNames, v -> config.showPlayerNames = v);
        b.slider("  Name Scale", 0.5f, 3.0f, () -> config.mapPlayerNameScale, v -> config.mapPlayerNameScale = v);
        b.toggle("  Class Name Instead Of Player", () -> config.mapPlayerClassName, v -> config.mapPlayerClassName = v);
        b.dropdown("  Name Color", new String[]{"Class", "White"},
                () -> config.mapPlayerNameColor, v -> config.mapPlayerNameColor = v);
        b.toggle("Use Heads (vs Markers)", () -> config.useHeadsInsteadOfMarkers, v -> config.useHeadsInsteadOfMarkers = v);
        b.toggle("  Self As Vanilla Arrow", () -> config.mapSelfVanillaArrow, v -> config.mapSelfVanillaArrow = v);
        b.toggle("Rotate Heads", () -> config.rotatePlayerHeads, v -> config.rotatePlayerHeads = v);
        b.toggle("Glow Arrow Marker", () -> config.mapGlowArrow, v -> config.mapGlowArrow = v);
        b.slider("Head Scale", 0.5f, 2.0f,
                () -> config.playerHeadScale, v -> config.playerHeadScale = v);
        b.label("Room Colors");
        b.color("Background", () -> config.colorBackground, v -> config.colorBackground = v);
        b.color("Unexplored", () -> config.colorUnexplored, v -> config.colorUnexplored = v);
        b.toggle("Darken Unentered (Type Color)", () -> config.mapDarkenUndiscovered, v -> config.mapDarkenUndiscovered = v);
        b.slider("  Darken Amount", 0.0f, 1.0f, () -> config.mapDarkenPercent, v -> config.mapDarkenPercent = v);
        b.toggle("Gray Unentered (Type Color)", () -> config.mapGrayUndiscovered, v -> config.mapGrayUndiscovered = v);
        b.color("Normal", () -> config.colorNormal, v -> config.colorNormal = v);
        b.color("Entrance", () -> config.colorEntrance, v -> config.colorEntrance = v);
        b.color("Blood", () -> config.colorBlood, v -> config.colorBlood = v);
        b.color("Trap", () -> config.colorTrap, v -> config.colorTrap = v);
        b.color("Puzzle", () -> config.colorPuzzle, v -> config.colorPuzzle = v);
        b.color("Fairy", () -> config.colorFairy, v -> config.colorFairy = v);
        b.color("Miniboss", () -> config.colorMiniboss, v -> config.colorMiniboss = v);
        b.toggle("Own Color For Rare Rooms", () -> config.mapRareRoomColor, v -> config.mapRareRoomColor = v);
        b.color("  Rare Room", () -> config.colorRare, v -> config.colorRare = v);
        b.dropdown("Mimic Room", new String[]{"Bright Red", "Tint", "Custom"},
                () -> config.mapMimicRoomStyle, v -> config.mapMimicRoomStyle = v);
        b.color("  Mimic Room Color", () -> config.colorMimicRoom, v -> config.colorMimicRoom = v);
        b.label("Door Colors");
        b.color("Normal Door", () -> config.colorDoorNormal, v -> config.colorDoorNormal = v);
        b.color("Wither Door", () -> config.colorDoorWither, v -> config.colorDoorWither = v);
        b.color("Blood Door", () -> config.colorDoorBlood, v -> config.colorDoorBlood = v);
        b.color("Entrance Door", () -> config.colorDoorEntrance, v -> config.colorDoorEntrance = v);
        b.toggle("Recolor Opened Doors", () -> config.mapColorOpenDoors, v -> config.mapColorOpenDoors = v)
                .desc("Wither and blood doors change color once opened");
        b.color("  Opened Door", () -> config.colorDoorOpenWither, v -> config.colorDoorOpenWither = v);
        b.label("Text Colors");
        b.color("Unexplored Text", () -> config.colorTextUnexplored, v -> config.colorTextUnexplored = v);
        b.color("Cleared Text", () -> config.colorTextCleared, v -> config.colorTextCleared = v);
        b.color("Green Text", () -> config.colorTextGreen, v -> config.colorTextGreen = v);
        b.color("Secret Count", () -> config.colorSecretCount, v -> config.colorSecretCount = v);
        b.label("Checkmark Colors");
        b.color("White Check", () -> config.colorCheckWhite, v -> config.colorCheckWhite = v);
        b.color("Green Check", () -> config.colorCheckGreen, v -> config.colorCheckGreen = v);
        b.color("Failed Check", () -> config.colorCheckFailed, v -> config.colorCheckFailed = v);
    }
}
