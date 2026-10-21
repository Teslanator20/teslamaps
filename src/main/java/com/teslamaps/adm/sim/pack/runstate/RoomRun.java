// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import java.util.Map;

/** Per-room secrets, crypts and durations. Key = the room's in-game name. */
public class RoomRun {
    public final String name;
    public final String type;
    public final String shape;
    /** Tile count (a 1x4 is 4, a 2x2 is 4, an L is 3): the counter weight. */
    public final int tiles;
    /** The number the action bar prints as M and the tab/Odin total use. Authoritative; never {@code secretsDb}. */
    public final int secretsMax;
    public final String secretsMaxSource;
    /** How many secrets the rooms pack can place for this design. */
    public final Integer secretsDb;
    public final Integer cryptsPotential;
    /** {@code mob}, {@code miniboss}, {@code none} or null. */
    public final String clear;
    /** First entry -> GREEN, all classes pooled; null = use {@link RunStatePack#roomTypeDur}. */
    public final Dist dur;
    public final Map<String, Dist> byClass;
    public final Dist toWhite;
    public final Dist toGreen;

    public RoomRun(String name, String type, String shape, int tiles, int secretsMax, String secretsMaxSource, Integer secretsDb, Integer cryptsPotential,
                   String clear, Dist dur, Map<String, Dist> byClass, Dist toWhite, Dist toGreen) {
        this.name = name;
        this.type = type;
        this.shape = shape;
        this.tiles = tiles;
        this.secretsMax = secretsMax;
        this.secretsMaxSource = secretsMaxSource;
        this.secretsDb = secretsDb;
        this.cryptsPotential = cryptsPotential;
        this.clear = clear;
        this.dur = dur;
        this.byClass = byClass;
        this.toWhite = toWhite;
        this.toGreen = toGreen;
    }
}
