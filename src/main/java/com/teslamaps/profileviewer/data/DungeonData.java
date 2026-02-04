package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dungeon/Catacombs data for a profile.
 */
public class DungeonData {
    // Catacombs level
    private double catacombsXp = 0;
    private int catacombsLevel = 0;
    private double catacombsProgress = 0;

    // Class levels (healer, mage, berserk, archer, tank)
    private final Map<String, ClassData> classes = new LinkedHashMap<>();

    // Floor completions (normal and master)
    private final Map<String, FloorData> normalFloors = new LinkedHashMap<>();
    private final Map<String, FloorData> masterFloors = new LinkedHashMap<>();

    // Total secrets
    private int secretsFound = 0;

    // Dungeon XP table (cumulative)
    private static final double[] CATACOMBS_XP_TABLE = {
            0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045,
            4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640,
            135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1684640, 2284640,
            3084640, 4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640,
            51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640, 453559640,
            569809640
    };

    // Class display names
    private static final String[] CLASS_NAMES = {"healer", "mage", "berserk", "archer", "tank"};
    private static final String[] CLASS_DISPLAY = {"Healer", "Mage", "Berserk", "Archer", "Tank"};

    public DungeonData(JsonObject memberData) {
        // Initialize classes
        for (int i = 0; i < CLASS_NAMES.length; i++) {
            classes.put(CLASS_NAMES[i], new ClassData(CLASS_DISPLAY[i], 0));
        }

        // Initialize floors
        for (int i = 0; i <= 7; i++) {
            normalFloors.put(String.valueOf(i), new FloorData(i == 0 ? "Entrance" : "F" + i));
            if (i >= 1) {
                masterFloors.put(String.valueOf(i), new FloorData("M" + i));
            }
        }

        parse(memberData);
    }

    private void parse(JsonObject memberData) {
        try {
            if (!memberData.has("dungeons")) return;
            JsonObject dungeons = memberData.getAsJsonObject("dungeons");

            // Parse dungeon types (catacombs)
            if (dungeons.has("dungeon_types")) {
                JsonObject dungeonTypes = dungeons.getAsJsonObject("dungeon_types");

                if (dungeonTypes.has("catacombs")) {
                    JsonObject catacombs = dungeonTypes.getAsJsonObject("catacombs");

                    // Catacombs XP and level
                    if (catacombs.has("experience")) {
                        this.catacombsXp = catacombs.get("experience").getAsDouble();
                        calculateCatacombsLevel();
                    }

                    // Floor completions
                    parseFloorCompletions(catacombs, normalFloors);
                    parseFloorTimes(catacombs, normalFloors);
                }

                // Master mode
                if (dungeonTypes.has("master_catacombs")) {
                    JsonObject master = dungeonTypes.getAsJsonObject("master_catacombs");
                    parseFloorCompletions(master, masterFloors);
                    parseFloorTimes(master, masterFloors);
                }
            }

            // Parse class levels
            if (dungeons.has("player_classes")) {
                JsonObject playerClasses = dungeons.getAsJsonObject("player_classes");
                for (String className : CLASS_NAMES) {
                    if (playerClasses.has(className)) {
                        JsonObject classData = playerClasses.getAsJsonObject(className);
                        if (classData.has("experience")) {
                            double xp = classData.get("experience").getAsDouble();
                            classes.get(className).setXp(xp);
                        }
                    }
                }
            }

            // Secrets found
            if (dungeons.has("secrets")) {
                this.secretsFound = dungeons.get("secrets").getAsInt();
            }

        } catch (Exception e) {
            // Ignore parse errors
        }
    }

    private void calculateCatacombsLevel() {
        for (int i = 0; i < CATACOMBS_XP_TABLE.length; i++) {
            if (catacombsXp >= CATACOMBS_XP_TABLE[i]) {
                catacombsLevel = i;
            } else {
                break;
            }
        }

        // Calculate progress
        if (catacombsLevel < CATACOMBS_XP_TABLE.length - 1) {
            double currentLevelXp = CATACOMBS_XP_TABLE[catacombsLevel];
            double nextLevelXp = CATACOMBS_XP_TABLE[catacombsLevel + 1];
            double progressXp = catacombsXp - currentLevelXp;
            double xpToNext = nextLevelXp - currentLevelXp;
            catacombsProgress = xpToNext > 0 ? progressXp / xpToNext : 1.0;
        } else {
            catacombsProgress = 1.0;
        }
    }

    private void parseFloorCompletions(JsonObject dungeonType, Map<String, FloorData> floors) {
        if (!dungeonType.has("tier_completions")) return;
        JsonObject completions = dungeonType.getAsJsonObject("tier_completions");
        for (Map.Entry<String, JsonElement> entry : completions.entrySet()) {
            FloorData floor = floors.get(entry.getKey());
            if (floor != null) {
                floor.setCompletions(entry.getValue().getAsInt());
            }
        }
    }

    private void parseFloorTimes(JsonObject dungeonType, Map<String, FloorData> floors) {
        if (!dungeonType.has("fastest_time")) return;
        JsonObject times = dungeonType.getAsJsonObject("fastest_time");
        for (Map.Entry<String, JsonElement> entry : times.entrySet()) {
            FloorData floor = floors.get(entry.getKey());
            if (floor != null) {
                floor.setFastestTime(entry.getValue().getAsLong());
            }
        }

        // Also check fastest_time_s and fastest_time_s_plus
        if (dungeonType.has("fastest_time_s")) {
            JsonObject sTimes = dungeonType.getAsJsonObject("fastest_time_s");
            for (Map.Entry<String, JsonElement> entry : sTimes.entrySet()) {
                FloorData floor = floors.get(entry.getKey());
                if (floor != null) {
                    floor.setFastestSTime(entry.getValue().getAsLong());
                }
            }
        }

        if (dungeonType.has("fastest_time_s_plus")) {
            JsonObject sPlusTimes = dungeonType.getAsJsonObject("fastest_time_s_plus");
            for (Map.Entry<String, JsonElement> entry : sPlusTimes.entrySet()) {
                FloorData floor = floors.get(entry.getKey());
                if (floor != null) {
                    floor.setFastestSPlusTime(entry.getValue().getAsLong());
                }
            }
        }
    }

    // Getters
    public double getCatacombsXp() { return catacombsXp; }
    public int getCatacombsLevel() { return catacombsLevel; }
    public double getCatacombsProgress() { return catacombsProgress; }
    public Map<String, ClassData> getClasses() { return classes; }
    public Map<String, FloorData> getNormalFloors() { return normalFloors; }
    public Map<String, FloorData> getMasterFloors() { return masterFloors; }
    public int getSecretsFound() { return secretsFound; }

    /**
     * Inner class for class data.
     */
    public static class ClassData {
        private final String displayName;
        private double xp;
        private int level;
        private double progress;

        public ClassData(String displayName, double xp) {
            this.displayName = displayName;
            setXp(xp);
        }

        public void setXp(double xp) {
            this.xp = xp;
            // Calculate level (uses same table as catacombs)
            for (int i = 0; i < CATACOMBS_XP_TABLE.length; i++) {
                if (xp >= CATACOMBS_XP_TABLE[i]) {
                    level = i;
                } else {
                    break;
                }
            }
            // Calculate progress
            if (level < CATACOMBS_XP_TABLE.length - 1) {
                double currentLevelXp = CATACOMBS_XP_TABLE[level];
                double nextLevelXp = CATACOMBS_XP_TABLE[level + 1];
                double progressXp = xp - currentLevelXp;
                double xpToNext = nextLevelXp - currentLevelXp;
                progress = xpToNext > 0 ? progressXp / xpToNext : 1.0;
            } else {
                progress = 1.0;
            }
        }

        public String getDisplayName() { return displayName; }
        public double getXp() { return xp; }
        public int getLevel() { return level; }
        public double getProgress() { return progress; }
    }

    /**
     * Inner class for floor data.
     */
    public static class FloorData {
        private final String displayName;
        private int completions = 0;
        private long fastestTime = 0;      // milliseconds
        private long fastestSTime = 0;
        private long fastestSPlusTime = 0;

        public FloorData(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
        public int getCompletions() { return completions; }
        public void setCompletions(int completions) { this.completions = completions; }

        public long getFastestTime() { return fastestTime; }
        public void setFastestTime(long fastestTime) { this.fastestTime = fastestTime; }

        public long getFastestSTime() { return fastestSTime; }
        public void setFastestSTime(long fastestSTime) { this.fastestSTime = fastestSTime; }

        public long getFastestSPlusTime() { return fastestSPlusTime; }
        public void setFastestSPlusTime(long fastestSPlusTime) { this.fastestSPlusTime = fastestSPlusTime; }

        /**
         * Format time as MM:SS.
         */
        public String formatTime(long ms) {
            if (ms == 0) return "-";
            long seconds = ms / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        public String getFormattedFastestTime() { return formatTime(fastestTime); }
        public String getFormattedSTime() { return formatTime(fastestSTime); }
        public String getFormattedSPlusTime() { return formatTime(fastestSPlusTime); }
    }
}
