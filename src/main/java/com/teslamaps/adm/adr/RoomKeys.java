package com.teslamaps.adm.adr;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.map.DungeonRoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Ported from ADM's RoomKeys.kt (Odin's core hash, clay rotation and geometry rotation inlined)
/**
 * Which copy in the room library a room is: "Name|ROTATION", plus "|core" for 1x1 rooms with
 * several variants (Odin's core of the room's centre column). Rotation and shape names are Odin's.
 */
public final class RoomKeys {
    private RoomKeys() {}

    /** 1x1 rooms Odin lists more than one core for (name -> cores). */
    private static final Map<String, Set<Integer>> MULTI = load();
    /** Per tile "x,z": {core, highestBlock}. */
    private static final Map<Long, int[]> cores = new HashMap<>();
    private static final Map<Long, String> clayRot = new HashMap<>();

    public static void reset() {
        cores.clear();
        clayRot.clear();
    }

    private static Map<String, Set<Integer>> load() {
        Map<String, Set<Integer>> out = new HashMap<>();
        try (var in = RoomKeys.class.getResourceAsStream("/assets/teslamaps/adm/adr_room_cores.json")) {
            if (in == null) return out;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.entrySet()) {
                Set<Integer> s = new HashSet<>();
                for (JsonElement c : e.getValue().getAsJsonArray()) s.add(c.getAsInt());
                out.put(e.getKey(), s);
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("[adr] room cores not loaded", e);
        }
        return out;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Odin's RoomShape name. */
    public static String shape(DungeonRoom room) {
        String s = room.getShape();
        if (s != null) {
            switch (s) {
                case "1x1": return "OneByOne";
                case "1x2": return "TwoByOne";
                case "1x3": return "ThreeByOne";
                case "1x4": return "FourByOne";
                case "2x2": return "TwoByTwo";
                case "L": return "L";
                default: break;
            }
        }
        List<int[]> t = room.getComponents();
        if (t.isEmpty()) return "OneByOne";
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : t) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[1]); maxZ = Math.max(maxZ, c[1]);
        }
        boolean square = maxX - minX == 1 && maxZ - minZ == 1;
        return switch (t.size()) {
            case 2 -> "TwoByOne";
            case 3 -> square ? "L" : "ThreeByOne";
            case 4 -> square ? "TwoByTwo" : "FourByOne";
            default -> "OneByOne";
        };
    }

    private static int tileAmount(String shape) {
        return switch (shape) {
            case "L" -> 3;
            case "TwoByOne" -> 2;
            case "ThreeByOne" -> 3;
            case "FourByOne", "TwoByTwo" -> 4;
            default -> 1;
        };
    }

    /** Odin's core and highest block of a 1x1 room's tile, once its chunk is here. */
    private static int[] coreOf(int tx, int tz) {
        long k = key(tx, tz);
        int[] c = cores.get(k);
        if (c != null) return c;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        int cx = (tx - 6) * 2, cz = (tz - 6) * 2;
        if (!(level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) return null;
        c = roomCore(chunk, cx * 16 + 7, cz * 16 + 7);
        return c;
    }

    /** Odin's WorldScan.getRoomCore: {hash, highestBlock}. */
    static int[] roomCore(LevelChunk chunk, int x, int z) {
        StringBuilder sb = new StringBuilder();
        boolean foundHighest = false;
        int highest = 0;
        int bedrock = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = 140; y >= 12; y--) {
            BlockState s = chunk.getBlockState(p.set(x, y, z));
            if (!foundHighest) {
                if (!s.isAir() && s.getBlock() != Blocks.GOLD_BLOCK) {
                    foundHighest = true;
                    highest = y;
                } else sb.append('0');
            }
            if (foundHighest) {
                if (s.isAir() && bedrock >= 2 && y < 69) {
                    for (int i = 0; i < y - 11; i++) sb.append('0');
                    break;
                }
                if (s.getBlock() == Blocks.BEDROCK) bedrock++;
                else {
                    bedrock = 0;
                    if (s.getBlock() == Blocks.OAK_PLANKS || s.getBlock() == Blocks.TRAPPED_CHEST || s.getBlock() == Blocks.CHEST) continue;
                }
                sb.append(s.getBlock());
            }
        }
        return new int[]{sb.toString().hashCode(), highest};
    }

    /** The variant core of a multi-variant 1x1 room, null until readable. */
    public static Integer core(DungeonRoom room) {
        Set<Integer> list = room.getName() == null ? null : MULTI.get(room.getName());
        if (list == null || !"OneByOne".equals(shape(room)) || room.getComponents().isEmpty()) return null;
        int[] t = room.getComponents().get(0);
        int[] c = coreOf(t[0], t[1]);
        if (c == null || !list.contains(c[0])) return null;
        cores.put(key(t[0], t[1]), c);
        return c[0];
    }

    /** Odin's RoomRotation name, or null while not known. */
    public static String rotation(DungeonRoom room) {
        if (room.getComponents().isEmpty()) return null;
        String shape = shape(room);
        if ("Fairy".equals(room.getName())) return "SOUTH";
        if ("OneByOne".equals(shape)) return clayRotation(room);
        if (room.getComponents().size() != tileAmount(shape)) return null;
        int[] tl = null, br = null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int[] c : room.getComponents()) {
            minX = Math.min(minX, c[0]);
            minZ = Math.min(minZ, c[1]);
            if (br == null || c[0] * 1000 + c[1] > br[0] * 1000 + br[1]) br = c;
        }
        tl = new int[]{minX, minZ};
        if ("L".equals(shape)) {
            int[] other = null;
            for (int[] c : room.getComponents()) {
                boolean isTl = c[0] == tl[0] && c[1] == tl[1];
                if (!isTl && c != br) { other = c; break; }
            }
            if (other == null) return null;
            if (tl[0] == br[0]) return "EAST";
            if (tl[1] == br[1]) return "WEST";
            if (other[0] == tl[0]) return "SOUTH";
            return "NORTH";
        }
        return tl[0] == br[0] ? "WEST" : "SOUTH";
    }

    private static final String[] ROT_NAMES = {"NORTH", "SOUTH", "WEST", "EAST"};
    private static final int[][] ROT_D = {{15, 15}, {-15, -15}, {15, -15}, {-15, 15}};

    /** 1x1: the rotation whose corner has the blue clay at the roof (Odin's get1x1Rotation). */
    private static String clayRotation(DungeonRoom room) {
        int[] t = room.getComponents().get(0);
        long k = key(t[0], t[1]);
        String known = clayRot.get(k);
        if (known != null) return known;
        int[] c = cores.get(k);
        if (c == null) c = coreOf(t[0], t[1]);
        if (c == null) return null;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        int x = t[0] * 32 - 185, z = t[1] * 32 - 185;
        for (int i = 0; i < 4; i++) {
            if (level.getBlockState(new BlockPos(x + ROT_D[i][0], c[1], z + ROT_D[i][1])).getBlock() == Blocks.BLUE_TERRACOTTA) {
                clayRot.put(k, ROT_NAMES[i]);
                return ROT_NAMES[i];
            }
        }
        return null;
    }

    /** The room's library key, or null while its rotation or variant isn't known. */
    public static String key(DungeonRoom room) {
        String name = room.getName();
        if (name == null || room.getRoomData() == null) return null;
        String rot = rotation(room);
        if (rot == null) return null;
        boolean multi = "OneByOne".equals(shape(room)) && MULTI.containsKey(name);
        if (!multi) return name + "|" + rot;
        Integer core = core(room);
        if (core == null) return null;
        return name + "|" + rot + "|" + core;
    }
}
