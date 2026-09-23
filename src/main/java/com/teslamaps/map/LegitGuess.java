package com.teslamaps.map;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.LegitMode;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.DoorScanner;
import com.teslamaps.utils.TabListUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Legit Mode helper: finds doors on the edges of explored rooms that lead to
 * undiscovered cells and guesses a 1x1 room behind each one. Discovered doors
 * are accumulated for the run (only added) so the map stays stable when rooms
 * leave render distance.
 */
public class LegitGuess {
    public record Door(int gx, int gz, int nx, int nz, DoorType type) {}

    private static final int N = ComponentGrid.GRID_SIZE;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final Map<Integer, Door> doors = new HashMap<>();
    private static int tickCounter = 0;

    // Odin SpecialColumn state
    private static final Set<Integer> known1x1 = new HashSet<>();
    private static final Map<Integer, int[]> guessColors = new HashMap<>();
    private static int discovered1x1s = 0;
    private static int columnRoomCount = 0;

    public static void reset() {
        doors.clear();
        known1x1.clear();
        guessColors.clear();
        discovered1x1s = 0;
        columnRoomCount = 0;
    }

    public static void tick() {
        if (!LegitMode.isActive() || !DungeonManager.isInDungeon()) {
            if (!doors.isEmpty() || !known1x1.isEmpty()) reset();
            return;
        }
        if (tickCounter++ % 10 != 0) return;

        ComponentGrid grid = DungeonManager.getGrid();
        for (DungeonRoom room : grid.getAllRooms()) {
            if (!room.isExplored()) continue;
            for (int[] comp : room.getComponents()) {
                int gx = comp[0], gz = comp[1];
                for (int[] d : DIRS) {
                    int nx = gx + d[0], nz = gz + d[1];
                    if (nx < 0 || nx >= N || nz < 0 || nz >= N) continue;
                    DungeonRoom neighbor = grid.getRoom(nx, nz);
                    if (neighbor != null && neighbor.isExplored()) continue;  // real door, drawn normally
                    int key = doorKey(gx, gz, nx, nz);
                    if (doors.containsKey(key)) continue;                     // already discovered
                    DoorType dt = DoorScanner.scanDoorBetweenCells(gx, gz, nx, nz);
                    if (dt == DoorType.NONE) continue;
                    doors.put(key, new Door(gx, gz, nx, nz, dt));
                }
            }
        }

        if (TeslaMapsConfig.get().legitRoomGuess) updateGuesses(grid);
        else guessColors.clear();
    }

    /** Doors from an explored room to a still-undiscovered cell. */
    public static List<Door> getGuessDoors() {
        List<Door> out = new ArrayList<>();
        if (!LegitMode.isActive()) return out;
        ComponentGrid grid = DungeonManager.getGrid();
        for (Door dr : doors.values()) {
            DungeonRoom nb = grid.getRoom(dr.nx(), dr.nz());
            if (nb != null && nb.isExplored()) continue;  // got explored since
            out.add(dr);
        }
        return out;
    }

    /** Guessed type colors for an undiscovered cell (1-3 colors), or null for unknown. */
    public static int[] colorsAt(int x, int z) {
        return guessColors.get(x + z * N);
    }

    private record Special(RoomType type, int topLeftX) {}

    // Ported from Odin's SpecialColumn.kt (update, colorGuessForUnknown)
    private static void updateGuesses(ComponentGrid grid) {
        int w = 6, h = 6, column = -1;
        switch (DungeonManager.getCurrentFloor()) {
            case F1, M1 -> { w = 4; h = 5; }
            case F2, F3, M2, M3 -> { w = 5; h = 5; }
            case F4, F5, F6, M4, M5, M6 -> column = 5;
            default -> {}
        }

        Set<Integer> hints = new HashSet<>();
        Set<Integer> bloodHints = new HashSet<>();
        for (Door d : getGuessDoors()) {
            hints.add(d.nx() + d.nz() * N);
            if (d.type() == DoorType.BLOOD) bloodHints.add(d.nx() + d.nz() * N);
        }

        for (int cell : hints) {
            if (known1x1.contains(cell)) continue;
            int x = cell % N, z = cell / N;
            if (x == column) {
                known1x1.add(cell);
                discovered1x1s++;
                columnRoomCount++;
            } else if (checkSide(grid, hints, x + 1, z, w, h) && checkSide(grid, hints, x - 1, z, w, h)
                    && checkSide(grid, hints, x, z + 1, w, h) && checkSide(grid, hints, x, z - 1, w, h)) {
                known1x1.add(cell);
                discovered1x1s++;
            }
        }

        List<Special> opened = new ArrayList<>();
        for (DungeonRoom room : grid.getAllRooms()) {
            RoomType t = room.getType();
            if (!room.isExplored() || (t != RoomType.YELLOW && t != RoomType.TRAP && t != RoomType.PUZZLE)) continue;
            int minX = Integer.MAX_VALUE;
            for (int[] c : room.getComponents()) minX = Math.min(minX, c[0]);
            opened.add(new Special(t, minX));
        }

        int puzzleCount = TabListUtils.getPuzzleCount();
        int discoveredFull;
        if (discovered1x1s == puzzleCount + 2) discoveredFull = w;
        else if (column != -1) {
            discoveredFull = h;
            for (int z = 0; z < h; z++) {
                int cell = column + z * N;
                DungeonRoom r = grid.getRoom(column, z);
                boolean seen = (r != null && r.isExplored()) || hints.contains(cell);
                if (!seen || (r != null && r.isExplored())) discoveredFull--;
            }
        } else discoveredFull = 0;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        int puzzle = TeslaMapsConfig.parseColor(c.colorPuzzle);
        int trap = TeslaMapsConfig.parseColor(c.colorTrap);
        int champion = TeslaMapsConfig.parseColor(c.colorMiniboss);
        int specialCount = (column != -1 && columnRoomCount == 0) ? 1 : columnRoomCount;

        guessColors.clear();
        for (int cell : hints) {
            if (bloodHints.contains(cell)) {
                guessColors.put(cell, new int[]{darker(TeslaMapsConfig.parseColor(c.colorBlood))});
            } else if (known1x1.contains(cell)) {
                int x = cell % N;
                guessColors.put(cell, x == column
                        ? guessSpecial(specialCount, h, discoveredFull, puzzleCount, column, opened, puzzle, trap, champion)
                        : guessNonSpecial(specialCount, puzzleCount, column, opened, puzzle, trap, champion));
            }
        }
    }

    private static boolean checkSide(ComponentGrid grid, Set<Integer> hints, int x, int z, int w, int h) {
        if (x < 0 || x >= w || z < 0 || z >= h) return true;
        DungeonRoom r = grid.getRoom(x, z);
        return (r != null && r.isExplored()) || hints.contains(x + z * N);
    }

    private static int[] guessSpecial(int specialCount, int mapSizeZ, int discoveredFull, int puzzleCount,
                                      int column, List<Special> opened, int puzzle, int trap, int champion) {
        int specialSize = mapSizeZ - discoveredFull + specialCount;
        if (specialSize <= puzzleCount || discovered1x1s - specialCount >= 2) return new int[]{puzzle};
        for (Special s : opened) if (s.type() == RoomType.TRAP && s.topLeftX() != column) return new int[]{puzzle};
        if (specialSize == puzzleCount + 1) return new int[]{trap, puzzle};
        if (discovered1x1s - specialCount == 1) return new int[]{trap, puzzle};
        return new int[]{champion, trap, puzzle};
    }

    private static int[] guessNonSpecial(int specialCount, int puzzleCount, int column, List<Special> opened,
                                         int puzzle, int trap, int champion) {
        int nonSpecialPuzzles = 0;
        boolean anyTrap = false, anyChampion = false;
        for (Special s : opened) {
            if (s.type() == RoomType.PUZZLE && s.topLeftX() != column) nonSpecialPuzzles++;
            if (s.type() == RoomType.TRAP) anyTrap = true;
            if (s.type() == RoomType.YELLOW) anyChampion = true;
        }
        if (specialCount + nonSpecialPuzzles == puzzleCount) {
            if (anyTrap) return new int[]{champion};
            if (anyChampion) return new int[]{trap};
            return new int[]{champion, trap};
        }
        if (specialCount == puzzleCount + 1) return new int[]{champion};
        int trapOrChampion = 0;
        for (Special s : opened) if (s.type() == RoomType.TRAP || s.type() == RoomType.YELLOW) trapOrChampion++;
        if (trapOrChampion == 2) return new int[]{puzzle};
        if (opened.size() == puzzleCount + 1) {
            if (!anyTrap) return new int[]{trap};
            if (!anyChampion) return new int[]{champion};
            return new int[]{puzzle};
        }
        if (!anyTrap && !anyChampion) return new int[]{puzzle, trap, champion};
        if (!anyTrap) return new int[]{puzzle, trap};
        if (!anyChampion) return new int[]{puzzle, champion};
        return new int[]{puzzle};
    }

    private static int darker(int color) {
        int r = ((color >> 16) & 0xFF) / 2, g = ((color >> 8) & 0xFF) / 2, b = (color & 0xFF) / 2;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int doorKey(int x1, int z1, int x2, int z2) {
        int a = x1 * N + z1, b = x2 * N + z2;
        return Math.min(a, b) * (N * N) + Math.max(a, b);
    }
}
