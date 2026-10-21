// Ported from ADM's p3sim/run/StubDungeon.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class StubDungeon {
    private StubDungeon() {}

    public static final String NAME = "stub-dungeon";
    public static final int FLOOR_Y = 68;
    public static final int CEILING_Y = 86;

    public static DungeonPlan plan(long seed) {
        ArrayList<Tile> order = new ArrayList<>();
        for (int z = 0; z < DungeonFrame.TILES; z++) {
            if (z % 2 == 0) for (int x = 0; x < DungeonFrame.TILES; x++) order.add(new Tile(x, z));
            else for (int x = DungeonFrame.TILES - 1; x >= 0; x--) order.add(new Tile(x, z));
        }
        List<PlacedRoom> rooms = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            Tile t = order.get(i);
            RoomType type;
            if (i == 0) type = RoomType.ENTRANCE;
            else if (i == order.size() - 1) type = RoomType.BLOOD;
            else if (i == order.size() - 2) type = RoomType.FAIRY;
            else type = RoomType.NORMAL;
            rooms.add(new PlacedRoom(i, "Stub Room " + (i + 1), "Stub", "stub", null, type, "1x1", List.of(t), Rot.SOUTH, null, 0, null, null, null, null, null));
        }
        ArrayList<PlacedDoor> doors = new ArrayList<>();
        for (int z = 0; z < DungeonFrame.TILES; z++) for (int x = 0; x < DungeonFrame.TILES; x++) {
            if (x < DungeonFrame.TILES - 1) doors.add(door(doors.size(), 'E', new Tile(x, z), new Tile(x + 1, z), order, rooms));
            if (z < DungeonFrame.TILES - 1) doors.add(door(doors.size(), 'S', new Tile(x, z), new Tile(x, z + 1), order, rooms));
        }
        List<Integer> path = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) path.add(i);
        return new DungeonPlan("stub", "F7", seed, rooms, doors, path, new ArrayList<>(), new ArrayList<>());
    }

    public enum Kind { AIR, BRICK, LANTERN, BEDROCK }

    public static final int BEDROCK_Y = 66;

    public static Kind kindAt(int x, int y, int z) {
        if (x < DungeonFrame.ORIGIN || x > DungeonFrame.DUNGEON_MAX || z < DungeonFrame.ORIGIN || z > DungeonFrame.DUNGEON_MAX) return Kind.AIR;
        if (y < BEDROCK_Y || y > CEILING_Y) return Kind.AIR;
        if (y < FLOOR_Y) return Kind.BEDROCK;
        int tx = Math.floorDiv(x - DungeonFrame.ORIGIN, DungeonFrame.PITCH); int tz = Math.floorDiv(z - DungeonFrame.ORIGIN, DungeonFrame.PITCH);
        int lx = Math.floorMod(x - DungeonFrame.ORIGIN, DungeonFrame.PITCH);
        int lz = Math.floorMod(z - DungeonFrame.ORIGIN, DungeonFrame.PITCH);
        if (y == FLOOR_Y || y == CEILING_Y) return Kind.BRICK;
        if (y == CEILING_Y - 1 && lx % 8 == 4 && lz % 8 == 4 && lx < 30 && lz < 30) return Kind.LANTERN;
        boolean wallX = lx >= 30 || lx == 0;
        boolean wallZ = lz >= 30 || lz == 0;
        if (!wallX && !wallZ) return Kind.AIR;
        boolean openX = wallX && !wallZ && Math.abs(lz - 15) <= 1 && joined(tx, tz, lx == 0 ? tx - 1 : tx + 1, tz);
        boolean openZ = wallZ && !wallX && Math.abs(lx - 15) <= 1 && joined(tx, tz, tx, lz == 0 ? tz - 1 : tz + 1);
        return y <= DungeonFrame.DOOR_Y_MAX && (openX || openZ) ? Kind.AIR : Kind.BRICK;
    }

    private static boolean joined(int ax, int az, int bx, int bz) {
        if (bx < 0 || bx >= DungeonFrame.TILES || bz < 0 || bz >= DungeonFrame.TILES) return false;
        return Math.abs(snakeIndex(ax, az) - snakeIndex(bx, bz)) == 1;
    }

    public static int snakeIndex(int x, int z) { return z * DungeonFrame.TILES + (z % 2 == 0 ? x : DungeonFrame.TILES - 1 - x); }

    private static PlacedDoor door(int i, char axis, Tile a, Tile b, List<Tile> order, List<PlacedRoom> rooms) {
        int ra = order.indexOf(a); int rb = order.indexOf(b);
        DoorKind kind = Math.abs(ra - rb) == 1 ? (Math.min(ra, rb) == 0 ? DoorKind.ENTRANCE : DoorKind.NORMAL) : DoorKind.NONE;
        return new PlacedDoor(i, axis, a, b, ra, rb, kind, null);
    }

    public static WorldRegion region() {
        return new WorldRegion() {
            private BlockState brick;
            private BlockState lantern;
            private BlockState bedrock;

            private BlockState brick() { if (brick == null) brick = Blocks.STONE_BRICKS.defaultBlockState(); return brick; }
            private BlockState lantern() { if (lantern == null) lantern = Blocks.SEA_LANTERN.defaultBlockState(); return lantern; }
            private BlockState bedrock() { if (bedrock == null) bedrock = Blocks.BEDROCK.defaultBlockState(); return bedrock; }

            @Override public String name() { return NAME; }
            @Override public int minX() { return DungeonFrame.ORIGIN; }
            @Override public int minZ() { return DungeonFrame.ORIGIN; }
            @Override public int maxX() { return DungeonFrame.DUNGEON_MAX; }
            @Override public int maxZ() { return DungeonFrame.DUNGEON_MAX; }

            @Override
            public void fill(ChunkAccess chunk) {
                int cx = chunk.getPos().getMinBlockX(); int cz = chunk.getPos().getMinBlockZ();
                for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) {
                    int x = cx + dx; int z = cz + dz;
                    if (x < minX() || x > maxX() || z < minZ() || z > maxZ()) continue;
                    for (int y = BEDROCK_Y; y <= CEILING_Y; y++) {
                        BlockState s;
                        switch (kindAt(x, y, z)) {
                            case AIR -> { continue; }
                            case BRICK -> s = brick();
                            case LANTERN -> s = lantern();
                            case BEDROCK -> s = bedrock();
                            default -> throw new IllegalStateException();
                        }
                        put(chunk, x, y, z, s);
                    }
                }
            }

            private void put(ChunkAccess chunk, int x, int y, int z, BlockState s) {
                int si = chunk.getSectionIndex(y);
                if (si < 0 || si >= chunk.getSections().length) return;
                chunk.getSection(si).setBlockState(x & 15, y & 15, z & 15, s, false);
            }

            @Override
            public int resync(ServerLevel level) { return 0; }
        };
    }
}
