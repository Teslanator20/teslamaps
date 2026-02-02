package com.teslamaps.dungeon;

public enum DungeonFloor {
    UNKNOWN("Unknown", 0),
    F1("Floor 1", 1),
    F2("Floor 2", 2),
    F3("Floor 3", 3),
    F4("Floor 4", 4),
    F5("Floor 5", 5),
    F6("Floor 6", 6),
    F7("Floor 7", 7),
    M1("Master 1", 1),
    M2("Master 2", 2),
    M3("Master 3", 3),
    M4("Master 4", 4),
    M5("Master 5", 5),
    M6("Master 6", 6),
    M7("Master 7", 7);

    private final String displayName;
    private final int level;

    DungeonFloor(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    public boolean isMaster() {
        return this.name().startsWith("M");
    }

    public static DungeonFloor fromString(String text) {
        if (text == null) return UNKNOWN;

        text = text.toUpperCase().trim();

        // Try direct match
        for (DungeonFloor floor : values()) {
            if (floor.name().equals(text)) {
                return floor;
            }
        }

        // Try parsing "Floor X" or "Master X" or just number
        if (text.contains("MASTER") || text.contains("M")) {
            String num = text.replaceAll("[^0-9]", "");
            if (!num.isEmpty()) {
                int n = Integer.parseInt(num);
                if (n >= 1 && n <= 7) {
                    return valueOf("M" + n);
                }
            }
        } else if (text.contains("FLOOR") || text.contains("F")) {
            String num = text.replaceAll("[^0-9]", "");
            if (!num.isEmpty()) {
                int n = Integer.parseInt(num);
                if (n >= 1 && n <= 7) {
                    return valueOf("F" + n);
                }
            }
        }

        return UNKNOWN;
    }
}
