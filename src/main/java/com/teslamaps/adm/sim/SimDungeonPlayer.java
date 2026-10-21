package com.teslamaps.adm.sim;

/** Odin's DungeonPlayer, as the sim uses it. */
public final class SimDungeonPlayer {
    public final String name;
    public final DungeonClass clazz;
    public final int clazzLvl;
    public final Object skin;

    public SimDungeonPlayer(String name, DungeonClass clazz, int clazzLvl, Object skin) {
        this.name = name;
        this.clazz = clazz;
        this.clazzLvl = clazzLvl;
        this.skin = skin;
    }

    public SimDungeonPlayer(String name, DungeonClass clazz) { this(name, clazz, 0, null); }
}
