package com.teslamaps.adm.map;

import com.teslamaps.adm.Adm;
import com.teslamaps.adm.map.clear.ClearBreakdown;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.config.ConfigBuilder;

public final class AdmMap {
    private AdmMap() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AdmMapScan").section(Adm.SECTION).when(() -> cfg().admRoomPrediction || cfg().admClearBreakdown)
                .tick(AdmMapScan::tick);
        Feature.of("AdmRoomPrediction").section(Adm.SECTION).when(() -> cfg().admRoomPrediction)
                .tick(MapPrediction::tick)
                .config("ADM Map", AdmMap::config);
        ClearBreakdown.register();
    }

    private static void config(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
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
