package com.teslamaps.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DungeonData {
    private double catacombsXp = 0;
    private int catacombsLevel = 0;
    private double catacombsProgress = 0;

    private final Map<String, ClassData> classes = new LinkedHashMap<>();

    private final Map<String, FloorData> normalFloors = new LinkedHashMap<>();
    private final Map<String, FloorData> masterFloors = new LinkedHashMap<>();

    private int secretsFound = 0;

    private static final double[] CATACOMBS_XP_TABLE = {
            0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045,
            4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 97640,
            135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1684640, 2284640,
            3084640, 4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640,
            51559640, 66559640, 85559640, 109559640, 139559640, 177559640, 225559640, 285559640, 360559640, 453559640,
            569809640
    };

    private static final String[] CLASS_NAMES = {"healer", "mage", "berserk", "archer", "tank"};
    private static final String[] CLASS_DISPLAY = {"Healer", "Mage", "Berserk", "Archer", "Tank"};

    public DungeonData(JsonObject memberData) {
        for (int i = 0; i < CLASS_NAMES.length; i++) {
            classes.put(CLASS_NAMES[i], new ClassData(CLASS_DISPLAY[i], 0));
        }

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

            if (dungeons.has("dungeon_types")) {
                JsonObject dungeonTypes = dungeons.getAsJsonObject("dungeon_types");

                if (dungeonTypes.has("catacombs")) {
                    JsonObject catacombs = dungeonTypes.getAsJsonObject("catacombs");

                    if (catacombs.has("experience")) {
                        this.catacombsXp = catacombs.get("experience").getAsDouble();
                        calculateCatacombsLevel();
                    }

                    parseFloorCompletions(catacombs, normalFloors);
                    parseFloorTimes(catacombs, normalFloors);
                }

                if (dungeonTypes.has("master_catacombs")) {
                    JsonObject master = dungeonTypes.getAsJsonObject("master_catacombs");
                    parseFloorCompletions(master, masterFloors);
                    parseFloorTimes(master, masterFloors);
                }
            }

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

            if (dungeons.has("secrets")) {
                this.secretsFound = dungeons.get("secrets").getAsInt();
            }

        } catch (Exception e) {
        }
    }

    // past level 50 every catacombs level costs a flat 200m, the table stops there
    private static final double OVERFLOW_XP = 200_000_000;

    private void calculateCatacombsLevel() {
        double[] result = levelFor(catacombsXp);
        catacombsLevel = (int) result[0];
        catacombsProgress = result[1];
    }

    /** Returns {level, progress}, counting on past 50 instead of stopping there. */
    private static double[] levelFor(double xp) {
        int level = 0;
        for (int i = 0; i < CATACOMBS_XP_TABLE.length; i++) {
            if (xp >= CATACOMBS_XP_TABLE[i]) level = i;
            else break;
        }

        int last = CATACOMBS_XP_TABLE.length - 1;
        if (level < last) {
            double current = CATACOMBS_XP_TABLE[level];
            double next = CATACOMBS_XP_TABLE[level + 1];
            double span = next - current;
            return new double[]{level, span > 0 ? (xp - current) / span : 1.0};
        }

        double over = (xp - CATACOMBS_XP_TABLE[last]) / OVERFLOW_XP;
        return new double[]{last + (int) over, over - (int) over};
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

    public double getCatacombsXp() { return catacombsXp; }
    public int getCatacombsLevel() { return catacombsLevel; }
    public double getCatacombsProgress() { return catacombsProgress; }
    public Map<String, ClassData> getClasses() { return classes; }
    public Map<String, FloorData> getNormalFloors() { return normalFloors; }
    public Map<String, FloorData> getMasterFloors() { return masterFloors; }
    public int getSecretsFound() { return secretsFound; }

    /** Entrance included, master modes included - what "secrets per run" divides by. */
    public int getTotalRuns() {
        int total = 0;
        for (FloorData floor : normalFloors.values()) total += floor.getCompletions();
        for (FloorData floor : masterFloors.values()) total += floor.getCompletions();
        return total;
    }

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
            double[] result = levelFor(xp);
            level = (int) result[0];
            progress = result[1];
        }

        public String getDisplayName() { return displayName; }
        public double getXp() { return xp; }
        public int getLevel() { return level; }
        public double getProgress() { return progress; }
    }

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
