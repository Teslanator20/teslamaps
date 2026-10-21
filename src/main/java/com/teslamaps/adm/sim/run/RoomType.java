// Ported from ADM's p3sim/run/DungeonPlan.kt
package com.teslamaps.adm.sim.run;

public enum RoomType {
    NORMAL, PUZZLE, RARE, YELLOW, TRAP, FAIRY, ENTRANCE, BLOOD;

    public static RoomType fromWord(String w) {
        for (RoomType t : values()) if (t.name().equalsIgnoreCase(w)) return t;
        return NORMAL;
    }
}
