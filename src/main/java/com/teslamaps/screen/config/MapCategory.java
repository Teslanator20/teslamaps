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
        b.toggle("Show Mimic Status", () -> config.showMimicStatus, v -> config.showMimicStatus = v);
        b.toggle("Show Prince Icon", () -> config.showPrinceIcon, v -> config.showPrinceIcon = v);
        b.toggle("Show Prince Status", () -> config.showPrinceStatus, v -> config.showPrinceStatus = v);
        b.toggle("Prince ESP (structure)", () -> config.princeESP, v -> config.princeESP = v);
        b.color("  Prince ESP Color", () -> config.colorPrinceESP, v -> config.colorPrinceESP = v);
        b.toggle("Show Dungeon Score", () -> config.showDungeonScore, v -> config.showDungeonScore = v);
        b.label("Rooms");
        b.toggle("Show Room Names", () -> config.showRoomNames, v -> config.showRoomNames = v);
        b.toggle("Hide Entrance/Blood/Fairy Names", () -> config.hideEntranceBloodFairyNames, v -> config.hideEntranceBloodFairyNames = v);
        b.toggle("Show Names Only for Puzzles", () -> config.showNamesOnlyForPuzzles, v -> config.showNamesOnlyForPuzzles = v);
        b.toggle("Room Names Only When Leap", () -> config.roomNamesOnlyWithLeap, v -> config.roomNamesOnlyWithLeap = v);
        b.toggle("Player Names Only When Leap", () -> config.playerNamesOnlyWithLeap, v -> config.playerNamesOnlyWithLeap = v);
        b.toggle("Head Border (Class Color)", () -> config.mapHeadBorder, v -> config.mapHeadBorder = v);
        b.slider("Room Name Scale", 0.5f, 2.0f,
                () -> config.roomNameScale, v -> config.roomNameScale = v);
        b.slider("Info Text Scale", 0.5f, 2.0f,
                () -> config.infoTextScale, v -> config.infoTextScale = v);
        b.toggle("Show Secrets", () -> config.showSecretCount, v -> config.showSecretCount = v);
        b.toggle("Hide 1 Secret Count", () -> config.hideOneSecretCount, v -> config.hideOneSecretCount = v);
        b.toggle("Show Crypts", () -> config.showCrypts, v -> config.showCrypts = v);
        b.toggle("Show Total Crypts", () -> config.showTotalCrypts, v -> config.showTotalCrypts = v);
        b.toggle("Hide When Done", () -> config.hideSecretsWhenDone, v -> config.hideSecretsWhenDone = v);
        b.toggle("Assume Paul Mayor", () -> config.assumePaulMayor, v -> config.assumePaulMayor = v);
        b.label("Checkmarks");
        b.toggle("Show Checkmarks", () -> config.showCheckmarks, v -> config.showCheckmarks = v);
        b.toggle("White Checks", () -> config.showWhiteCheckmarks, v -> config.showWhiteCheckmarks = v);
        b.toggle("Green Checks", () -> config.showGreenCheckmarks, v -> config.showGreenCheckmarks = v);
        b.toggle("Failed Checks", () -> config.showFailedCheckmarks, v -> config.showFailedCheckmarks = v);
        b.toggle("Checkmark Icons", () -> config.mapCheckmarkIcons, v -> config.mapCheckmarkIcons = v);
        b.dropdown("  Icon Style", new String[]{"Symbol", "IllegalMap", "NEU", "Vanilla New", "Vanilla Old"},
                () -> config.mapCheckmarkStyle, v -> config.mapCheckmarkStyle = v);
        b.slider("  Icon Size", 0.2f, 1.0f, () -> config.mapCheckmarkSize, v -> config.mapCheckmarkSize = v);
        b.toggle("  Center In Room", () -> config.mapCheckmarkCenter, v -> config.mapCheckmarkCenter = v);
        b.toggle("  Question Mark On Unentered", () -> config.mapCheckmarkUnopened, v -> config.mapCheckmarkUnopened = v);
        b.toggle("  Hide When Name Shown", () -> config.mapCheckmarkHideWithName, v -> config.mapCheckmarkHideWithName = v);
        b.label("Players");
        b.toggle("Player Markers", () -> config.showPlayerMarker, v -> config.showPlayerMarker = v);
        b.toggle("Show Self", () -> config.showSelfMarker, v -> config.showSelfMarker = v);
        b.toggle("Show Others", () -> config.showOtherPlayers, v -> config.showOtherPlayers = v);
        b.toggle("Show Names", () -> config.showPlayerNames, v -> config.showPlayerNames = v);
        b.toggle("Use Heads (vs Markers)", () -> config.useHeadsInsteadOfMarkers, v -> config.useHeadsInsteadOfMarkers = v);
        b.toggle("Rotate Heads", () -> config.rotatePlayerHeads, v -> config.rotatePlayerHeads = v);
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
        b.dropdown("Mimic Room", new String[]{"Bright Red", "Tint", "Custom"},
                () -> config.mapMimicRoomStyle, v -> config.mapMimicRoomStyle = v);
        b.color("  Mimic Room Color", () -> config.colorMimicRoom, v -> config.colorMimicRoom = v);
        b.label("Door Colors");
        b.color("Normal Door", () -> config.colorDoorNormal, v -> config.colorDoorNormal = v);
        b.color("Wither Door", () -> config.colorDoorWither, v -> config.colorDoorWither = v);
        b.color("Blood Door", () -> config.colorDoorBlood, v -> config.colorDoorBlood = v);
        b.color("Entrance Door", () -> config.colorDoorEntrance, v -> config.colorDoorEntrance = v);
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
