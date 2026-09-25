package com.teslamaps.map;

import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.MapScanner;

// Odin MapScan.mapTiles: room type from the tile's corner pixel, doors from the gap pixels
public final class MapTileTypes {
    private static final int N = ComponentGrid.GRID_SIZE;
    private static final RoomType[] types = new RoomType[N * N];
    private static final boolean[] unopened = new boolean[N * N];
    private static final boolean[] joinRight = new boolean[N * N];
    private static final boolean[] joinDown = new boolean[N * N];
    private static final DoorType[] doorRight = new DoorType[N * N];
    private static final DoorType[] doorDown = new DoorType[N * N];
    private static boolean has;

    private MapTileTypes() {}

    public static void update(byte[] colors) {
        int rs = MapScanner.mapRoomSize, gap = MapScanner.mapGapSize;
        int cx = MapScanner.mapCornerX, cy = MapScanner.mapCornerY;
        if (cx < 0 || cy < 0 || colors == null || colors.length < 128 * 128) return;
        int half = rs / 2;
        for (int gz = 0; gz < N; gz++) {
            for (int gx = 0; gx < N; gx++) {
                int i = gx + gz * N;
                int ox = cx + gx * (rs + gap), oz = cy + gz * (rs + gap);
                int corner = px(colors, ox, oz);
                types[i] = typeOf(corner);
                unopened[i] = corner == 85;
                joinRight[i] = joinDown[i] = false;
                doorRight[i] = doorDown[i] = null;
                if (types[i] == null) continue;
                if (gx < N - 1) {
                    int door = px(colors, ox + rs + gap / 2, oz + half);
                    int side = px(colors, ox + rs + gap / 2, oz + half - 4);
                    if (side == 0 && door != 0) doorRight[i] = doorOf(door);
                    else if (side != 0 && side == corner) joinRight[i] = true;
                }
                if (gz < N - 1) {
                    int door = px(colors, ox + half, oz + rs + gap / 2);
                    int side = px(colors, ox + half - 4, oz + rs + gap / 2);
                    if (side == 0 && door != 0) doorDown[i] = doorOf(door);
                    else if (side != 0 && side == corner) joinDown[i] = true;
                }
            }
        }
        has = true;
    }

    public static void reset() {
        has = false;
        java.util.Arrays.fill(types, null);
    }

    private static int px(byte[] c, int x, int y) {
        if (x < 0 || y < 0 || x >= 128 || y >= 128) return 0;
        return c[x + y * 128] & 0xFF;
    }

    private static RoomType typeOf(int color) {
        return switch (color) {
            case 30 -> RoomType.ENTRANCE;
            case 18 -> RoomType.BLOOD;
            case 62 -> RoomType.TRAP;
            case 66 -> RoomType.PUZZLE;
            case 74 -> RoomType.YELLOW;
            case 82 -> RoomType.FAIRY;
            case 63 -> RoomType.NORMAL;
            case 85 -> RoomType.UNKNOWN;
            default -> null;
        };
    }

    private static DoorType doorOf(int color) {
        return switch (color) {
            case 119 -> DoorType.WITHER;
            case 18 -> DoorType.BLOOD;
            case 30 -> DoorType.ENTRANCE;
            default -> DoorType.NORMAL;
        };
    }

    public static boolean ready() { return has; }

    private static boolean in(int gx, int gz) { return gx >= 0 && gz >= 0 && gx < N && gz < N; }

    public static RoomType type(int gx, int gz) { return has && in(gx, gz) ? types[gx + gz * N] : null; }

    public static boolean unopened(int gx, int gz) { return has && in(gx, gz) && unopened[gx + gz * N]; }

    public static boolean joinRight(int gx, int gz) { return has && in(gx, gz) && joinRight[gx + gz * N]; }

    public static boolean joinDown(int gx, int gz) { return has && in(gx, gz) && joinDown[gx + gz * N]; }

    public static DoorType doorRight(int gx, int gz) { return has && in(gx, gz) ? doorRight[gx + gz * N] : null; }

    public static DoorType doorDown(int gx, int gz) { return has && in(gx, gz) ? doorDown[gx + gz * N] : null; }
}
