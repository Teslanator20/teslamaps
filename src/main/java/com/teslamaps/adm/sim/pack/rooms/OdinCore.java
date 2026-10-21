// Ported from ADM's p3sim/pack/rooms/OdinCore.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Odin's room core ({@code WorldScan.getRoomCore}): the hash of a tile's CENTRE column that Odin looks up in its
 * {@code rooms.json} to name a room. Pure.
 */
public final class OdinCore {
    private OdinCore() {}

    private static final String AIR = "minecraft:air";

    /** The core of the column whose block name at height y is {@code name(y)} (properties stripped; null or the air name for air). */
    public static int hash(Function<Integer, String> name) {
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        int bedrock = 0;
        int y = 140;
        while (y >= 12) {
            String b = name.apply(y);
            if (b != null && b.equals(AIR)) b = null;
            if (!found) {
                if (b != null && !b.equals("minecraft:gold_block")) found = true; else sb.append('0');
            }
            if (found) {
                if (b == null && bedrock >= 2 && y < 69) {
                    for (int i = 0; i < y - 11; i++) sb.append('0');
                    break;
                }
                if ("minecraft:bedrock".equals(b)) bedrock++;
                else {
                    bedrock = 0;
                    if ("minecraft:oak_planks".equals(b) || "minecraft:trapped_chest".equals(b) || "minecraft:chest".equals(b)) { y--; continue; }
                }
                sb.append("Block{").append(b != null ? b : AIR).append('}');
            }
            y--;
        }
        return sb.toString().hashCode();
    }

    /** Like {@link #hash} for a column given as full state strings: the properties are stripped first. */
    public static int ofStates(Function<Integer, String> state) {
        return hash(y -> {
            String s = state.apply(y);
            if (s == null) return null;
            int i = s.indexOf('[');
            return i < 0 ? s : s.substring(0, i);
        });
    }

    /** The core of canonical column (x, z) of [volume]. */
    public static int ofVolume(RoomVolume volume, int x, int z) {
        return ofStates(y -> volume.contains(x, y, z) ? volume.stateAt(x, y, z) : null);
    }

    /** The cores of every tile centre of a canonical room of the given tile cells, in the order of [tiles]. */
    public static List<Integer> ofRoom(RoomVolume volume, List<Tile> tiles) {
        List<Integer> out = new ArrayList<>();
        for (Tile t : tiles) out.add(ofVolume(volume, 32 * t.x + 15, 32 * t.z + 15));
        return out;
    }
}
