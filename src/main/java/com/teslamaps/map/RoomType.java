/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
package com.teslamaps.map;

public enum RoomType {
    NORMAL(0x6B3A11FF),      // Brown
    PUZZLE(0x7500ADFF),      // Purple
    TRAP(0xD87F33FF),        // Orange
    YELLOW(0xFEDF00FF),      // Yellow (miniboss)
    BLOOD(0xFF0000FF),       // Red
    FAIRY(0xE000FFFF),       // Pink
    RARE(0xFFCB59FF),        // Gold
    ENTRANCE(0x148500FF),    // Green
    UNKNOWN(0x808080FF);     // Gray

    private final int color;

    RoomType(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public static RoomType fromString(String type) {
        if (type == null) return UNKNOWN;
        return switch (type.toLowerCase()) {
            case "normal" -> NORMAL;
            case "puzzle" -> PUZZLE;
            case "trap" -> TRAP;
            case "yellow" -> YELLOW;
            case "blood" -> BLOOD;
            case "fairy" -> FAIRY;
            case "rare" -> RARE;
            case "entrance" -> ENTRANCE;
            default -> UNKNOWN;
        };
    }
}
