/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

        for (DungeonFloor floor : values()) {
            if (floor.name().equals(text)) {
                return floor;
            }
        }

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
