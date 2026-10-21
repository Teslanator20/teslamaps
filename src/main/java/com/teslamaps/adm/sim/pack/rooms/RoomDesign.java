// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** One room design. All coordinates canonical; {@code y} absolute. */
public class RoomDesign {
    public final String id;
    public final String name;
    public final String family;
    public final String buildKey;
    /** {@code normal puzzle rare yellow trap fairy entrance blood}. */
    public final String type;
    /** {@code 1x1 1x2 1x3 1x4 2x2 L}. */
    public final String shape;
    public final List<Tile> tiles;
    public final int w, d, h, y0;
    public final int roofY;
    public final List<Rot> rotations;
    /** Door set, 4 chars in the order N, E, S, W (1x1 designs); null for other shapes. */
    public final String doors;
    public final RoomSource source;
    public final BlobRef vol;
    public final List<DoorSlot> slots;
    public final List<RoomBlockEntity> blockEntities;
    public final List<RoomSecret> secrets;
    public final List<RoomLever> levers;
    public final List<List<Pos>> crypts;
    public final List<List<Pos>> superboomWalls;
    public final List<Pos> fallingBlocks;
    public final List<RoomCrusher> crushers;
    public final List<RoomTripwire> tripwires;
    public final RoomPuzzle puzzle;
    /** A copy of the Odin {@code rooms.json} entry; null if the design has none. */
    public final JsonObject odin;
    public final List<RoomVariant> variants;
    public final List<String> gaps;
    /** The Odin cores the BASE volume produces; empty when the index lacks them. */
    public final List<Integer> baseCores;
    /** {@code mob}, {@code miniboss} or {@code none}, or null. */
    public final String clear;

    public RoomDesign(String id, String name, String family, String buildKey, String type, String shape, List<Tile> tiles,
                      int w, int d, int h, int y0, int roofY, List<Rot> rotations, String doors, RoomSource source, BlobRef vol,
                      List<DoorSlot> slots, List<RoomBlockEntity> blockEntities, List<RoomSecret> secrets, List<RoomLever> levers,
                      List<List<Pos>> crypts, List<List<Pos>> superboomWalls, List<Pos> fallingBlocks, List<RoomCrusher> crushers,
                      List<RoomTripwire> tripwires, RoomPuzzle puzzle, JsonObject odin, List<RoomVariant> variants, List<String> gaps,
                      List<Integer> baseCores, String clear) {
        this.id = id;
        this.name = name;
        this.family = family;
        this.buildKey = buildKey;
        this.type = type;
        this.shape = shape;
        this.tiles = tiles;
        this.w = w;
        this.d = d;
        this.h = h;
        this.y0 = y0;
        this.roofY = roofY;
        this.rotations = rotations;
        this.doors = doors;
        this.source = source;
        this.vol = vol;
        this.slots = slots;
        this.blockEntities = blockEntities;
        this.secrets = secrets;
        this.levers = levers;
        this.crypts = crypts;
        this.superboomWalls = superboomWalls;
        this.fallingBlocks = fallingBlocks;
        this.crushers = crushers;
        this.tripwires = tripwires;
        this.puzzle = puzzle;
        this.odin = odin;
        this.variants = variants;
        this.gaps = gaps;
        this.baseCores = baseCores;
        this.clear = clear;
    }

    public RoomDesign(String id, String name, String family, String buildKey, String type, String shape, List<Tile> tiles,
                      int w, int d, int h, int y0, int roofY, List<Rot> rotations, String doors, RoomSource source, BlobRef vol,
                      List<DoorSlot> slots, List<RoomBlockEntity> blockEntities, List<RoomSecret> secrets, List<RoomLever> levers,
                      List<List<Pos>> crypts, List<List<Pos>> superboomWalls, List<Pos> fallingBlocks, List<RoomCrusher> crushers,
                      List<RoomTripwire> tripwires, RoomPuzzle puzzle, JsonObject odin, List<RoomVariant> variants, List<String> gaps,
                      List<Integer> baseCores) {
        this(id, name, family, buildKey, type, shape, tiles, w, d, h, y0, roofY, rotations, doors, source, vol, slots, blockEntities, secrets,
                levers, crypts, superboomWalls, fallingBlocks, crushers, tripwires, puzzle, odin, variants, gaps, baseCores, null);
    }

    public RoomDesign(String id, String name, String family, String buildKey, String type, String shape, List<Tile> tiles,
                      int w, int d, int h, int y0, int roofY, List<Rot> rotations, String doors, RoomSource source, BlobRef vol,
                      List<DoorSlot> slots, List<RoomBlockEntity> blockEntities, List<RoomSecret> secrets, List<RoomLever> levers,
                      List<List<Pos>> crypts, List<List<Pos>> superboomWalls, List<Pos> fallingBlocks, List<RoomCrusher> crushers,
                      List<RoomTripwire> tripwires, RoomPuzzle puzzle, JsonObject odin, List<RoomVariant> variants, List<String> gaps) {
        this(id, name, family, buildKey, type, shape, tiles, w, d, h, y0, roofY, rotations, doors, source, vol, slots, blockEntities, secrets,
                levers, crypts, superboomWalls, fallingBlocks, crushers, tripwires, puzzle, odin, variants, gaps, new ArrayList<>());
    }

    /** How many secrets Odin says the room has ({@code odin.secrets}, or {@code maxSecrets}); null when unknown. */
    public Integer odinSecrets() {
        if (odin == null) return null;
        Integer s = JsonKt.int_(odin, "secrets");
        if (s != null) return s;
        return JsonKt.int_(odin, "maxSecrets");
    }

    /** The Fairy room is never rotated by the world. */
    public boolean isFairy() { return type.equals("fairy") || family.equals("Fairy"); }

    /** Where this design stands for the layout's rotation word and world tiles. Throws {@link PlacementException}. */
    public Placement place(Rot rot, List<Tile> worldTiles) {
        Rot used = isFairy() ? Rot.SOUTH : rot;
        if (!rotations.isEmpty() && !rotations.contains(used)) {
            throw new PlacementException(id + " is never placed " + used + " (it occurs as "
                    + rotations.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")");
        }
        return Placement.of(shape, w, d, used, worldTiles, isFairy());
    }
}
