// Ported from ADM's p3sim/pack/layouts/LayoutPack.kt
package com.teslamaps.adm.sim.pack.layouts;

import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;

import java.util.List;
import java.util.Objects;

/**
 * One room of a recorded layout. {@code design} null: the builder picks one from the seed. {@code rot} null: unresolved.
 * The {@code t*} fields are the FIRST tick (relative to {@code t0}) the room showed that check mark; null = not observed.
 */
public final class LayoutRoom {
    public final int i;
    public final String name;
    public final String family;
    public final String design;
    public final String designBy;
    public final Integer core;
    /** {@code normal puzzle rare yellow trap fairy entrance blood}. */
    public final String type;
    /** {@code 1x1 1x2 1x3 1x4 2x2 L}. */
    public final String shape;
    public final List<Tile> tiles;
    public final Rot rot;
    public final String rotSource;
    public final String rotOdin;
    public final Integer secretsMax;
    public final Integer secretsFound;
    public final Integer tSeen;
    public final Integer tNone;
    public final Integer tWhite;
    public final Integer tGreen;
    public final Integer tRed;

    public LayoutRoom(int i, String name, String family, String design, String designBy, Integer core, String type, String shape,
                      List<Tile> tiles, Rot rot, String rotSource, String rotOdin, Integer secretsMax, Integer secretsFound,
                      Integer tSeen, Integer tNone, Integer tWhite, Integer tGreen, Integer tRed) {
        this.i = i;
        this.name = name;
        this.family = family;
        this.design = design;
        this.designBy = designBy;
        this.core = core;
        this.type = type;
        this.shape = shape;
        this.tiles = tiles;
        this.rot = rot;
        this.rotSource = rotSource;
        this.rotOdin = rotOdin;
        this.secretsMax = secretsMax;
        this.secretsFound = secretsFound;
        this.tSeen = tSeen;
        this.tNone = tNone;
        this.tWhite = tWhite;
        this.tGreen = tGreen;
        this.tRed = tRed;
    }

    @Override public boolean equals(Object o) {
        return o instanceof LayoutRoom r && i == r.i && Objects.equals(name, r.name) && Objects.equals(family, r.family)
                && Objects.equals(design, r.design) && Objects.equals(designBy, r.designBy) && Objects.equals(core, r.core)
                && Objects.equals(type, r.type) && Objects.equals(shape, r.shape) && Objects.equals(tiles, r.tiles) && rot == r.rot
                && Objects.equals(rotSource, r.rotSource) && Objects.equals(rotOdin, r.rotOdin) && Objects.equals(secretsMax, r.secretsMax)
                && Objects.equals(secretsFound, r.secretsFound) && Objects.equals(tSeen, r.tSeen) && Objects.equals(tNone, r.tNone)
                && Objects.equals(tWhite, r.tWhite) && Objects.equals(tGreen, r.tGreen) && Objects.equals(tRed, r.tRed);
    }

    @Override public int hashCode() {
        return Objects.hash(i, name, family, design, designBy, core, type, shape, tiles, rot, rotSource, rotOdin, secretsMax, secretsFound,
                tSeen, tNone, tWhite, tGreen, tRed);
    }

    @Override public String toString() {
        return "LayoutRoom(i=" + i + ", name=" + name + ", family=" + family + ", design=" + design + ", designBy=" + designBy + ", core=" + core
                + ", type=" + type + ", shape=" + shape + ", tiles=" + tiles + ", rot=" + rot + ", rotSource=" + rotSource + ", rotOdin=" + rotOdin
                + ", secretsMax=" + secretsMax + ", secretsFound=" + secretsFound + ", tSeen=" + tSeen + ", tNone=" + tNone + ", tWhite=" + tWhite
                + ", tGreen=" + tGreen + ", tRed=" + tRed + ")";
    }
}
