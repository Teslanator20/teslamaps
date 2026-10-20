package com.teslamaps.adm.map;

// Ported from Odin's dungeon/map DungeonScan.kt, MapScan.kt, SpecialColumn.kt and tile/*.kt:
// the map as Odin reads it, rooms identified by our world scan (DungeonManager grid)
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class AdmMapScan {
    public enum RType {
        ENTRANCE(30), FAIRY(82), NORMAL(63), RARE(63), BLOOD(18), CHAMPION(74), UNKNOWN(85), PUZZLE(66), TRAP(62), UNDISCOVERED(-1);
        public final int mapColor;
        RType(int c) { mapColor = c; }

        static RType fromMapColor(int c) {
            return switch (c) {
                case 30 -> ENTRANCE;
                case 82 -> FAIRY;
                case 63 -> NORMAL;
                case 18 -> BLOOD;
                case 74 -> CHAMPION;
                case 85 -> UNKNOWN;
                case 66 -> PUZZLE;
                case 62 -> TRAP;
                default -> null;
            };
        }
    }

    public enum Check {
        NONE, WHITE, GREEN, RED, QUESTION_MARK, UNDISCOVERED;

        static Check fromMapColor(int c) {
            return switch (c) {
                case 34 -> WHITE;
                case 30 -> GREEN;
                case 18 -> RED;
                case 119 -> QUESTION_MARK;
                default -> null;
            };
        }
    }

    public enum DType { NORMAL, WITHER, BLOOD, FAIRY }

    public static final class Room {
        public RType type;
        public final List<int[]> tiles = new ArrayList<>(4);
        public int tlx, tlz;
        public Check checkmark = Check.UNDISCOVERED;
        public boolean walkedInto, known1x1;
        public String name;
        public int maxSecrets;
        public int foundSecrets = -1;

        Room(RType type, int x, int z) { this.type = type; tlx = x; tlz = z; }

        public boolean viewable() { return walkedInto || checkmark != Check.UNDISCOVERED; }

        public boolean hasTile(int x, int z) {
            for (int[] t : tiles) if (t[0] == x && t[1] == z) return true;
            return false;
        }

        void addTile(int x, int z) {
            if (hasTile(x, z)) return;
            tiles.add(new int[]{x, z});
            tlx = Integer.MAX_VALUE; tlz = Integer.MAX_VALUE;
            for (int[] t : tiles) { tlx = Math.min(tlx, t[0]); tlz = Math.min(tlz, t[1]); }
        }

        /** Odin's room center in map space (20 per tile): the middle, an L's full row. */
        public int[] center() {
            if (tiles.isEmpty()) return null;
            int minX = 9, maxX = -1, minZ = 9, maxZ = -1;
            for (int[] t : tiles) { minX = Math.min(minX, t[0]); maxX = Math.max(maxX, t[0]); minZ = Math.min(minZ, t[1]); maxZ = Math.max(maxZ, t[1]); }
            if (tiles.size() == 3 && maxX - minX == 1 && maxZ - minZ == 1) {
                int top = 0;
                for (int[] t : tiles) if (t[1] == minZ) top++;
                int row = top == 2 ? minZ : maxZ;
                return new int[]{minX * 20 + 8 + 10, row * 20 + 8};
            }
            return new int[]{(minX + maxX) * 10 + 8, (minZ + maxZ) * 10 + 8};
        }

        /** Sorted tile indices, a stable key per room. */
        public String key() {
            int[] k = new int[tiles.size()];
            for (int i = 0; i < k.length; i++) k[i] = tiles.get(i)[0] + tiles.get(i)[1] * 6;
            Arrays.sort(k);
            return Arrays.toString(k);
        }

        public String displayName() { return name != null ? name : type.name(); }
    }

    public static final class Door {
        public final int x, z;
        public final boolean horizontal;
        public DType type;
        int exampleColor;

        Door(int x, int z, boolean horizontal, DType type) { this.x = x; this.z = z; this.horizontal = horizontal; this.type = type; }

        public int origin() { return x + z * 6; }
        public int dest() { return (x + (horizontal ? 1 : 0)) + (z + (horizontal ? 0 : 1)) * 6; }
    }

    public static final int ROOM_SPACING = 4;
    public static int roomSize = 16, startX = 5, startY = 5;
    public static final Room[] tiles = new Room[36];
    public static final List<Room> rooms = new ArrayList<>();
    public static final Map<Integer, Door> doors = new LinkedHashMap<>();
    public static final List<Integer> pathHints = new ArrayList<>();
    /** Teammates' map icons as [mapX, mapZ, rot16] per tab slot, null when none. */
    public static final List<int[]> mapPlayers = new ArrayList<>();
    private static boolean init;
    private static int lastFloor = -1;
    private static Object lastLevel;
    private static final byte[] last = new byte[128 * 128];
    private static boolean hasLast;
    private static int version;

    // SpecialColumn
    private static final Set<Room> openedSpecialRooms = new HashSet<>();
    private static int discoveredFullSpecialColumn, columnRoomCount, discovered1x1s;
    public static int column = -1;

    private AdmMapScan() {}

    public static int roomGap() { return roomSize + ROOM_SPACING; }
    public static int connectionGap() { return roomSize + ROOM_SPACING / 2; }
    public static int version() { return version; }

    public static void reset() {
        Arrays.fill(tiles, null);
        rooms.clear();
        doors.clear();
        pathHints.clear();
        mapPlayers.clear();
        roomSize = 16; startX = 5; startY = 5;
        init = false;
        hasLast = false;
        lastFloor = -1;
        column = -1;
        discoveredFullSpecialColumn = 0; discovered1x1s = 0; columnRoomCount = 0;
        openedSpecialRooms.clear();
        version++;
    }

    public static int floorNumber() {
        return DungeonManager.getCurrentFloor().getLevel();
    }

    public static boolean inClear() {
        return DungeonManager.isInDungeon() && !DungeonManager.isInBossArea();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!DungeonManager.isInDungeon()) {
            if (!rooms.isEmpty() || init) reset();
            return;
        }
        int floor = floorNumber();
        if (floor != lastFloor && floor >= 0) {
            lastFloor = floor;
            if (!init) initClient(floor);
        }
        boolean changed = syncGrid();
        if (walk(mc)) changed = true;
        if (inClear()) {
            MapItemSavedData data = findMap(mc);
            byte[] colors = data == null ? null : data.colors;
            if (colors != null && colors.length >= 128 * 128 && colors[0] == 0
                    && !(hasLast && Arrays.equals(colors, 0, 128 * 128, last, 0, 128 * 128))) {
                System.arraycopy(colors, 0, last, 0, 128 * 128);
                hasLast = true;
                if (init || initLayout(colors)) {
                    updateAll(colors);
                    changed = true;
                }
            }
            updatePlayers();
        }
        if (changed) {
            updateViewableDoors();
            updateSpecial();
            version++;
        }
    }

    private static void initClient(int floor) {
        roomSize = floor <= 3 ? 18 : 16;
        startX = floor <= 1 ? 22 : floor <= 3 ? 11 : 5;
        startY = switch (floor) {
            case 0 -> 22;
            case 4 -> 16;
            case 1, 2, 3 -> 11;
            default -> 5;
        };
    }

    private static MapItemSavedData findMap(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        for (int i = 0; i < 9; i++) {
            MapItemSavedData s = stateOf(mc, mc.player.getInventory().getItem(i));
            if (s != null) return s;
        }
        return stateOf(mc, mc.player.getOffhandItem());
    }

    private static MapItemSavedData stateOf(Minecraft mc, ItemStack stack) {
        if (!(stack.getItem() instanceof MapItem)) return null;
        MapId id = stack.get(DataComponents.MAP_ID);
        return id == null ? null : MapItem.getSavedData(id, mc.level);
    }

    private static RType fromGrid(com.teslamaps.map.RoomType t) {
        if (t == null) return null;
        return switch (t) {
            case NORMAL -> RType.NORMAL;
            case PUZZLE -> RType.PUZZLE;
            case TRAP -> RType.TRAP;
            case YELLOW -> RType.CHAMPION;
            case BLOOD -> RType.BLOOD;
            case FAIRY -> RType.FAIRY;
            case RARE -> RType.RARE;
            case ENTRANCE -> RType.ENTRANCE;
            default -> null;
        };
    }

    /** Odin's WorldScan role: the rooms our world scan identified, by tile. */
    private static boolean syncGrid() {
        boolean changed = false;
        for (DungeonRoom gr : DungeonManager.getGrid().getAllRooms()) {
            if (gr.getName() == null || "Unknown".equals(gr.getName())) continue;
            RType type = fromGrid(gr.getType());
            if (type == null || gr.getComponents().isEmpty()) continue;
            Room room = null;
            for (int[] c : gr.getComponents()) {
                if (c[0] < 0 || c[0] > 5 || c[1] < 0 || c[1] > 5) continue;
                Room r = tiles[c[0] + c[1] * 6];
                if (r != null) { room = r; break; }
            }
            if (room == null) {
                int[] p = gr.getPrimaryComponent();
                room = new Room(type, p[0], p[1]);
                rooms.add(room);
                changed = true;
            }
            for (int[] c : gr.getComponents()) {
                if (c[0] < 0 || c[0] > 5 || c[1] < 0 || c[1] > 5) continue;
                int i = c[0] + c[1] * 6;
                if (tiles[i] == null) {
                    tiles[i] = room;
                    room.addTile(c[0], c[1]);
                    changed = true;
                }
            }
            if (room.name == null) changed = true;
            if (room.type != type && type != RType.NORMAL) room.type = type;
            room.name = gr.getName();
            room.maxSecrets = gr.getSecrets();
            int found = gr.getFoundSecrets();
            if (found > room.foundSecrets) room.foundSecrets = found;
            if (room.checkmark == Check.GREEN && room.maxSecrets > 0) room.foundSecrets = room.maxSecrets;
        }
        return changed;
    }

    /** Odin sets walkedInto on entering a room (its own and its sync's players): here every teammate we can see. */
    private static boolean walk(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        boolean changed = false;
        Set<String> party = new HashSet<>();
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) party.add(p.getName());
        for (Player pl : mc.level.players()) {
            if (pl != mc.player && !party.contains(pl.getGameProfile().name())) continue;
            int tx = (int) Math.floor((pl.getX() + 200) / 32.0), tz = (int) Math.floor((pl.getZ() + 200) / 32.0);
            if (tx < 0 || tx > 5 || tz < 0 || tz > 5) continue;
            Room r = tiles[tx + tz * 6];
            if (r != null && !r.walkedInto) { r.walkedInto = true; changed = true; }
        }
        return changed;
    }

    private static boolean initLayout(byte[] colors) {
        for (int index = 0; index < colors.length; index++) {
            if ((colors[index] & 0xFF) != RType.ENTRANCE.mapColor) continue;
            int end = index;
            while (end < colors.length && colors[end] == colors[index]) end++;
            int length = end - index;
            if (length == 16 || length == 18) {
                roomSize = length;
                startX = (index % 128) % roomGap();
                startY = (index / 128) % roomGap();
                if (startX == 0) startX = 22;
                if (startY == 0) startY = 22;
                init = true;
                int f = floorNumber();
                if (((f == 6 || f == 5) && startX == 5) || (f == 4 && startX == 5)) column = 5;
                return true;
            }
            index = end - 1;
        }
        return false;
    }

    private static void updatePlayers() {
        mapPlayers.clear();
        for (int[] p : com.teslamaps.scanner.MapScanner.getMapPlayerPositions()) {
            if (p[3] == 1) continue;
            mapPlayers.add(new int[]{p[0] * 2 - 128, p[1] * 2 - 128, p[2]});
        }
    }

    private static int px(byte[] c, int x, int z) {
        return x >= 0 && x < 128 && z >= 0 && z < 128 ? c[z * 128 + x] & 0xFF : 0;
    }

    private static void updateAll(byte[] colors) {
        RType[] types = new RType[36];
        int[] roomColors = new int[36], centerColors = new int[36];
        int half = roomSize / 2, gap = connectionGap();
        for (int i = 0; i < 36; i++) {
            int tx = i % 6, tz = i / 6;
            int ox = startX + tx * roomGap(), oz = startY + tz * roomGap();
            int corner = px(colors, ox, oz);
            if (corner != 0) {
                RType t = RType.fromMapColor(corner);
                if (t != null && t != RType.UNKNOWN && t != RType.UNDISCOVERED) {
                    types[i] = t;
                    roomColors[i] = corner;
                    centerColors[i] = px(colors, ox + half, oz + half);
                }
            }
            if (tx < 5) {
                int door = px(colors, ox + gap, oz + half), side = px(colors, ox + gap, oz + half - 4);
                if (side == 0 && door != 0) addDoor(tx, tz, true, doorType(door));
            }
            if (tz < 5) {
                int door = px(colors, ox + half, oz + gap), side = px(colors, ox + half - 4, oz + gap);
                if (side == 0 && door != 0) addDoor(tx, tz, false, doorType(door));
            }
        }
        boolean[] visited = new boolean[36];
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int s = 0; s < 36; s++) {
            RType type = types[s];
            if (type == null || visited[s]) continue;
            List<Integer> comp = new ArrayList<>();
            ArrayList<Integer> queue = new ArrayList<>();
            queue.add(s);
            visited[s] = true;
            while (!queue.isEmpty()) {
                int cur = queue.remove(0);
                comp.add(cur);
                int cx = cur % 6, cz = cur / 6;
                for (int[] d : dirs) {
                    int nx = cx + d[0], nz = cz + d[1];
                    if (nx < 0 || nx > 5 || nz < 0 || nz > 5) continue;
                    int ni = nx + nz * 6;
                    if (visited[ni] || types[ni] != type) continue;
                    boolean connected;
                    if (d[0] == 1) connected = px(colors, startX + cx * roomGap() + gap, startY + cz * roomGap()) != 0;
                    else if (d[0] == -1) connected = px(colors, startX + nx * roomGap() + gap, startY + nz * roomGap()) != 0;
                    else if (d[1] == 1) connected = px(colors, startX + cx * roomGap(), startY + cz * roomGap() + gap) != 0;
                    else connected = px(colors, startX + nx * roomGap(), startY + nz * roomGap() + gap) != 0;
                    if (!connected) continue;
                    visited[ni] = true;
                    queue.add(ni);
                }
            }
            buildRoom(comp, type, roomColors, centerColors);
        }
    }

    private static DType doorType(int color) {
        if (color == 119) return DType.WITHER;
        if (color == RType.BLOOD.mapColor) return DType.BLOOD;
        if (color == RType.FAIRY.mapColor) return DType.FAIRY;
        return DType.NORMAL;
    }

    private static void addDoor(int x, int z, boolean horizontal, DType type) {
        int key = (x + z * 6) * 2 + (horizontal ? 0 : 1);
        Door d = doors.get(key);
        if (d == null) doors.put(key, new Door(x, z, horizontal, type));
        else d.type = type;
    }

    private static void buildRoom(List<Integer> segments, RType type, int[] roomColors, int[] centerColors) {
        Room existing = null;
        for (int i : segments) if (tiles[i] != null) { existing = tiles[i]; break; }
        Room room;
        if (existing != null) {
            room = existing;
            for (int i : segments) {
                if (tiles[i] != room) {
                    tiles[i] = room;
                    room.addTile(i % 6, i / 6);
                }
            }
        } else {
            int minX = 9, minZ = 9;
            for (int i : segments) { minX = Math.min(minX, i % 6); minZ = Math.min(minZ, i / 6); }
            room = new Room(type, minX, minZ);
            rooms.add(room);
            for (int i : segments) {
                tiles[i] = room;
                room.addTile(i % 6, i / 6);
            }
        }
        for (int i : segments) {
            int rc = roomColors[i], cc = centerColors[i];
            Check next = cc == rc && (room.checkmark == Check.UNDISCOVERED || room.checkmark == Check.QUESTION_MARK)
                    ? Check.NONE : Check.fromMapColor(cc);
            if (next != null && next != room.checkmark) {
                room.checkmark = next;
                if (next == Check.GREEN && room.maxSecrets > 0) room.foundSecrets = room.maxSecrets;
            }
        }
    }

    private static void updateViewableDoors() {
        pathHints.clear();
        for (Door door : doors.values()) {
            Room o = door.origin() < 36 ? tiles[door.origin()] : null;
            Room d = door.dest() < 36 ? tiles[door.dest()] : null;
            boolean ov = o != null && o.viewable(), dv = d != null && d.viewable();
            if (!ov && !dv) continue;
            int hint = !ov ? door.origin() : !dv ? door.dest() : -1;
            if (hint >= 0 && !pathHints.contains(hint)) pathHints.add(hint);
        }
    }

    // ---- SpecialColumn ----

    private static void updateSpecial() {
        int size = startX == 5 ? 6 : 5;
        for (int t : pathHints) {
            Room r = tiles[t];
            int x = t % 6, z = t / 6;
            if (x == column) {
                if (r != null && !r.known1x1) { r.known1x1 = true; discovered1x1s++; columnRoomCount++; }
            } else if (visibleFromAllSides(x, z, size)) {
                if (r != null && !r.known1x1) { r.known1x1 = true; discovered1x1s++; }
            }
        }
        for (Room r : rooms) {
            if ((r.type == RType.CHAMPION || r.type == RType.TRAP || r.type == RType.PUZZLE) && r.viewable()) openedSpecialRooms.add(r);
        }
        discoveredFullSpecialColumn = discovered1x1s == puzzleCount() + 2 ? size : column != -1 ? countDiscoveredInColumn(size) : 0;
    }

    public static int puzzleCount() {
        return TabListUtils.getPuzzleCount();
    }

    private static boolean checkSide(int nx, int nz, int gridSize) {
        if (nx < 0 || nx >= gridSize || nz < 0 || nz >= 6) return true;
        Room r = tiles[nx + nz * 6];
        if (r == null) return false;
        return r.viewable() || pathHints.contains(nx + nz * 6);
    }

    private static boolean visibleFromAllSides(int x, int z, int size) {
        return checkSide(x + 1, z, size) && checkSide(x - 1, z, size) && checkSide(x, z + 1, size) && checkSide(x, z - 1, size);
    }

    private static int countDiscoveredInColumn(int zSize) {
        int discovered = zSize;
        for (int z = 0; z < zSize; z++) {
            int i = (column - 1) + z * 6;
            Room r = i >= 0 && i < 36 ? tiles[i] : null;
            if (r == null || r.viewable()) discovered--;
        }
        return discovered;
    }

    /** Odin's special column guess for an unknown tile in this column, as room types (CHAMPION, TRAP, PUZZLE). */
    public static RType[] guessForUnknown(int tileX) {
        int count = column != -1 && columnRoomCount == 0 ? 1 : columnRoomCount;
        int z = startX == 5 ? 6 : 5;
        return tileX == column ? guessSpecial(count, z) : guessNonSpecial(count);
    }

    private static boolean anyOpened(RType t) {
        for (Room r : openedSpecialRooms) if (r.type == t) return true;
        return false;
    }

    private static RType[] guessSpecial(int count, int mapSizeZ) {
        int specialSize = mapSizeZ - discoveredFullSpecialColumn + count;
        int puzzles = puzzleCount();
        if (specialSize <= puzzles || discovered1x1s - count >= 2) return new RType[]{RType.PUZZLE};
        for (Room r : openedSpecialRooms) if (r.type == RType.TRAP && r.tlx != column) return new RType[]{RType.PUZZLE};
        if (specialSize == puzzles + 1) return new RType[]{RType.TRAP, RType.PUZZLE};
        if (discovered1x1s - count == 1) return new RType[]{RType.TRAP, RType.PUZZLE};
        return new RType[]{RType.CHAMPION, RType.TRAP, RType.PUZZLE};
    }

    private static RType[] guessNonSpecial(int count) {
        int nonSpecialPuzzles = 0;
        for (Room r : openedSpecialRooms) if (r.type == RType.PUZZLE && r.tlx != column) nonSpecialPuzzles++;
        int puzzles = puzzleCount();
        if (count + nonSpecialPuzzles == puzzles) {
            if (anyOpened(RType.TRAP)) return new RType[]{RType.CHAMPION};
            if (anyOpened(RType.CHAMPION)) return new RType[]{RType.TRAP};
            return new RType[]{RType.CHAMPION, RType.TRAP};
        }
        if (count == puzzles + 1) return new RType[]{RType.CHAMPION};
        int ct = 0;
        for (Room r : openedSpecialRooms) if (r.type == RType.CHAMPION || r.type == RType.TRAP) ct++;
        if (ct == 2) return new RType[]{RType.PUZZLE};
        if (openedSpecialRooms.size() == puzzles + 1) {
            if (!anyOpened(RType.TRAP)) return new RType[]{RType.TRAP};
            if (!anyOpened(RType.CHAMPION)) return new RType[]{RType.CHAMPION};
            return new RType[]{RType.PUZZLE};
        }
        List<RType> out = new ArrayList<>();
        out.add(RType.PUZZLE);
        if (!anyOpened(RType.TRAP)) out.add(RType.TRAP);
        if (!anyOpened(RType.CHAMPION)) out.add(RType.CHAMPION);
        return out.toArray(new RType[0]);
    }

    /** The room at a world position. */
    public static Room roomAt(double x, double z) {
        int tx = ((int) Math.floor(x) + 201) >> 5, tz = ((int) Math.floor(z) + 201) >> 5;
        if (tx < 0 || tx > 5 || tz < 0 || tz > 5) return null;
        return tiles[tx + tz * 6];
    }
}
