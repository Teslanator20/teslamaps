package com.teslamaps.adm.map;

import com.teslamaps.adm.Adm;
import com.teslamaps.adm.map.clear.ClearBreakdown;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.screen.config.ConfigBuilder;

public final class AdmMap {
    static HudEditElement hud;

    private static final String[] ROOM_TEXT = {"Both", "Room Name", "Room Secrets"};

    private AdmMap() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("admMap", "ADM Dungeon Map").ctx(HudEditElement.HudCtx.DUNGEON).section(Adm.SECTION)
                .enabled(() -> cfg().admMap)
                .size(() -> AdmMapRender.lastW, () -> AdmMapRender.lastH)
                .preview(AdmMapRender::preview);
        Feature.of("AdmMapScan").section(Adm.SECTION).when(() -> cfg().admMap || cfg().admClearBreakdown)
                .tick(AdmMapScan::tick);
        Feature.of("AdmMap").section(Adm.SECTION).when(() -> cfg().admMap)
                .tick(MapPrediction::tick)
                .hud("admmap", AdmMapRender::hud)
                .config("ADM Map", AdmMap::config);
        ClearBreakdown.register();
    }

    private static void config(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("ADM Dungeon Map", () -> c.admMap, v -> c.admMap = v)
                .desc("Odin's Dungeon Map with ADM's additions (LCM Style, Room Prediction). Replaces the TeslaMaps map while on. Position in /tmap gui")
                .tags("adm", "map", "odin");
        b.toggle("  Disable in Boss", () -> c.admMapDisableInBoss, v -> c.admMapDisableInBoss = v)
                .desc("Disables the map during boss fights.");
        b.color("  Background Outline", () -> c.admMapBgOutline, v -> c.admMapBgOutline = v);
        b.color("  Background Color", () -> c.admMapBgColor, v -> c.admMapBgColor = v);
        b.dropdown("  Room Text", ROOM_TEXT, () -> c.admMapRoomText, v -> c.admMapRoomText = v)
                .desc("What to display on the rooms.");
        b.slider("  Text Scaling", 0.1f, 1f, () -> c.admMapTextScaling, v -> c.admMapTextScaling = v, 2)
                .desc("Scale of room name text.");
        b.label("  Player Settings");
        b.slider("    Player Names Scaling", 0.1f, 2f, () -> c.admMapPlayerNamesScaling, v -> c.admMapPlayerNamesScaling = v, 2);
        b.color("    Player Name Color", () -> c.admMapPlayerNameColor, v -> c.admMapPlayerNameColor = v);
        b.toggle("    Own Player Head", () -> c.admMapOwnHead, v -> c.admMapOwnHead = v)
                .desc("Shows the player head on the map.");
        b.label("  Room Settings");
        b.color("    Normal Room", () -> c.admMapNormalRoom, v -> c.admMapNormalRoom = v);
        b.color("    Puzzle Room", () -> c.admMapPuzzleRoom, v -> c.admMapPuzzleRoom = v);
        b.color("    Trap Room", () -> c.admMapTrapRoom, v -> c.admMapTrapRoom = v);
        b.color("    Blood Room", () -> c.admMapBloodRoom, v -> c.admMapBloodRoom = v);
        b.color("    Entrance Room", () -> c.admMapEntranceRoom, v -> c.admMapEntranceRoom = v);
        b.color("    Fairy Room", () -> c.admMapFairyRoom, v -> c.admMapFairyRoom = v);
        b.color("    Champion Room", () -> c.admMapChampionRoom, v -> c.admMapChampionRoom = v);
        b.color("    Unknown Room", () -> c.admMapUnknownRoom, v -> c.admMapUnknownRoom = v);
        b.label("  Door Settings");
        b.color("    Normal Door", () -> c.admMapNormalDoor, v -> c.admMapNormalDoor = v);
        b.color("    Wither Door", () -> c.admMapWitherDoor, v -> c.admMapWitherDoor = v);
        b.color("    Blood Door", () -> c.admMapBloodDoor, v -> c.admMapBloodDoor = v);
        b.color("    Fairy Door", () -> c.admMapFairyDoor, v -> c.admMapFairyDoor = v);
        b.color("    Unknown Door", () -> c.admMapUnknownDoor, v -> c.admMapUnknownDoor = v);
        b.toggle("  Disable Prediction", () -> c.admMapDisablePred, v -> c.admMapDisablePred = v)
                .desc("Disables special-column room type prediction.");
        b.toggle("  LCM Style", () -> c.admLcmStyle, v -> c.admLcmStyle = v)
                .desc("Draws the map like LCM's map (dtMap) in its Legit Mode: its colours, background, doors, room names, check marks, Prince crowns and player heads (yours as its white marker). Room Prediction draws its guesses the same way, with its chances instead of \"?\".");

        b.toggle("Room Prediction", () -> c.admRoomPrediction, v -> c.admRoomPrediction = v).cheat()
                .desc("On: unknown rooms coloured by their chances, from the rules every recorded F7/M7 map follows, updated as rooms open. Off: Odin's own guess.");
        b.toggle("  Split Contents", () -> c.admPredSplitContents, v -> c.admPredSplitContents = v)
                .desc("On each \"?\" behind a door: what that side of the map holds (puzzles P, trap T, yellow Y, blood B; a letter shows when it's more likely there than not) and about how many rooms.");
        b.toggle("  Hidden Tile Hints", () -> c.admPredHiddenHints, v -> c.admPredHiddenHints = v)
                .desc("A small mark on unknown tiles further in where a special room is likely.");
        b.toggle("  Show Chances", () -> c.admPredShowChances, v -> c.admPredShowChances = v)
                .desc("The most likely special room's chance on each \"?\" tile.");
        b.slider("  Min Chance", 0.1f, 0.9f, () -> c.admPredMinChance, v -> c.admPredMinChance = Math.round(v * 20f) / 20f, 2)
                .desc("A room type shows on a tile only from this chance up.");
        b.label("  Dead End Prediction");
        b.slider("    Dead End Chance %", 0f, 100f, () -> c.admPredDeadEndChance, v -> c.admPredDeadEndChance = Math.round(v), 0)
                .desc("How sure the prediction has to be that a tile is a puzzle, trap, yellow or rare 1x1 before it gets Odin's guess tile. From 95% it always does, whatever this is set to.");
        b.label("  Blood & Fairy Prediction");
        b.slider("    Blood & Fairy Chance %", 0f, 100f, () -> c.admPredBloodFairyChance, v -> c.admPredBloodFairyChance = Math.round(v), 0)
                .desc("How sure the prediction has to be that a tile is blood or the fairy room before it's marked.");
        b.label("  Big Room Prediction");
        b.slider("    Big Room Chance %", 0f, 100f, () -> c.admPredBigChance, v -> c.admPredBigChance = Math.round(v), 0)
                .desc("How sure the prediction has to be of a big room's size and place before it's drawn.");
        b.label("  1x1 Room Prediction");
        b.slider("    1x1 Room Chance %", 0f, 100f, () -> c.admPredOneChance, v -> c.admPredOneChance = Math.round(v), 0)
                .desc("How sure the prediction has to be that a tile is a normal 1x1 room before it's drawn.");
        b.label("  Door Prediction");
        b.slider("    Door Chance %", 0f, 100f, () -> c.admPredDoorChance, v -> c.admPredDoorChance = Math.round(v), 0)
                .desc("How sure the prediction has to be of a door before it's drawn.");
        b.toggle("  Exact Last Rooms", () -> c.admPredExactRooms, v -> c.admPredExactRooms = v)
                .desc("Once the secrets the tab list counts leave only one way for the last rooms to be (their size, place and name), they're drawn in full with their name and secrets.");
    }
}
