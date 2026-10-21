// Ported from ADM's p3sim/run/DungeonPlan.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.LayoutRoom;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import java.util.List;

/** One room of the dungeon, resolved to a design and a placement. Immutable. */
public class PlacedRoom {
    public final int index;
    /** The in-game name, with spaces. */
    public final String name;
    public final String family;
    /** The rooms-pack design id actually placed. */
    public final String designId;
    public final RoomDesign design;
    public final RoomType type;
    /** `1x1 1x2 1x3 1x4 2x2 L`. */
    public final String shape;
    /** World tiles, sorted row-major. */
    public final List<Tile> tiles;
    public final Rot rotation;
    public final Placement placement;
    public final int secretsMax;
    public final Integer recordedNone;
    public final Integer recordedWhite;
    public final Integer recordedGreen;
    public final Integer recordedRed;
    public final LayoutRoom layoutRoom;
    /** The variant to build (RoomPack.volumeFor), or null for the base volume. */
    public final String variantId;

    public PlacedRoom(int index, String name, String family, String designId, RoomDesign design, RoomType type, String shape, List<Tile> tiles,
                      Rot rotation, Placement placement, int secretsMax, Integer recordedNone, Integer recordedWhite, Integer recordedGreen,
                      Integer recordedRed, LayoutRoom layoutRoom, String variantId) {
        this.index = index;
        this.name = name;
        this.family = family;
        this.designId = designId;
        this.design = design;
        this.type = type;
        this.shape = shape;
        this.tiles = tiles;
        this.rotation = rotation;
        this.placement = placement;
        this.secretsMax = secretsMax;
        this.recordedNone = recordedNone;
        this.recordedWhite = recordedWhite;
        this.recordedGreen = recordedGreen;
        this.recordedRed = recordedRed;
        this.layoutRoom = layoutRoom;
        this.variantId = variantId;
    }

    public PlacedRoom(int index, String name, String family, String designId, RoomDesign design, RoomType type, String shape, List<Tile> tiles,
                      Rot rotation, Placement placement, int secretsMax, Integer recordedNone, Integer recordedWhite, Integer recordedGreen,
                      Integer recordedRed, LayoutRoom layoutRoom) {
        this(index, name, family, designId, design, type, shape, tiles, rotation, placement, secretsMax, recordedNone, recordedWhite, recordedGreen,
                recordedRed, layoutRoom, null);
    }

    /** How many of the 36 tile units this room counts for. */
    public int tileCount() { return tiles.size(); }

    public boolean containsTile(Tile t) { return tiles.contains(t); }
}
