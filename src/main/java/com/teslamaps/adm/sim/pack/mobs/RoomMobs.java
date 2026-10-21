// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.ArrayList;
import java.util.List;

/** Everything the pack knows about one room, keyed by the room's in-game name. */
public class RoomMobs {
    public final String name;
    public final List<String> designs;
    /** {@code mob}, {@code miniboss} or {@code none}: how the room is cleared. */
    public final String clear;
    public final int visitsAdr;
    public final int visitsCd;
    public final CountStats starred;
    public final CountStats unstarred;
    public final List<MobCell> cells;
    public final List<MobLayout> layouts;
    /** Other spellings of the room's name. */
    public final List<String> aliases;
    /** The design type word ({@code normal}, {@code puzzle}, {@code trap}, ...) or null. */
    public final String type;

    public RoomMobs(String name, List<String> designs, String clear, int visitsAdr, int visitsCd, CountStats starred, CountStats unstarred,
                    List<MobCell> cells, List<MobLayout> layouts, List<String> aliases, String type) {
        this.name = name;
        this.designs = designs;
        this.clear = clear;
        this.visitsAdr = visitsAdr;
        this.visitsCd = visitsCd;
        this.starred = starred;
        this.unstarred = unstarred;
        this.cells = cells;
        this.layouts = layouts;
        this.aliases = aliases;
        this.type = type;
    }

    public RoomMobs(String name, List<String> designs, String clear, int visitsAdr, int visitsCd, CountStats starred, CountStats unstarred,
                    List<MobCell> cells, List<MobLayout> layouts, List<String> aliases) {
        this(name, designs, clear, visitsAdr, visitsCd, starred, unstarred, cells, layouts, aliases, null);
    }

    public RoomMobs(String name, List<String> designs, String clear, int visitsAdr, int visitsCd, CountStats starred, CountStats unstarred,
                    List<MobCell> cells, List<MobLayout> layouts) {
        this(name, designs, clear, visitsAdr, visitsCd, starred, unstarred, cells, layouts, new ArrayList<>());
    }

    /** Visits behind the numbers. */
    public int visits() { return visitsAdr + visitsCd; }
}
