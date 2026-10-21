package com.teslamaps.adm.sim;

/** Odin's DungeonClass, as the sim uses it. */
public enum DungeonClass {
    ARCHER(0xFFFFAA00, '6', 0, 2),
    BERSERK(0xFFAA0000, '4', 1, 0),
    HEALER(0xFFFF55FF, 'd', 2, 2),
    MAGE(0xFF55FFFF, 'b', 3, 2),
    TANK(0xFF00AA00, '2', 3, 1),
    EMPTY(0xFFFFFFFF, 'f', 0, 0);

    public final int color;
    public final char colorCode;
    public final int defaultQuadrant;
    public int priority;

    DungeonClass(int color, char colorCode, int defaultQuadrant, int priority) {
        this.color = color;
        this.colorCode = colorCode;
        this.defaultQuadrant = defaultQuadrant;
        this.priority = priority;
    }
}
