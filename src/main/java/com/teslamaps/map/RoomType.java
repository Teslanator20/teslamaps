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
