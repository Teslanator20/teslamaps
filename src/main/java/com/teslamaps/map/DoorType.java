package com.teslamaps.map;

public enum DoorType {
    NONE(0x00000000),
    NORMAL(0x808080FF),
    WITHER(0x000000FF),
    BLOOD(0xFF0000FF),
    ENTRANCE(0x00FF00FF);

    private final int color;

    DoorType(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public static DoorType fromInt(int type) {
        return switch (type) {
            case 0 -> NORMAL;
            case 1 -> WITHER;
            case 2 -> BLOOD;
            case 3 -> ENTRANCE;
            default -> NONE;
        };
    }
}
