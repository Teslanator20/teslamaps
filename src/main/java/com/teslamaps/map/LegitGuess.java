/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
package com.teslamaps.map;

import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.LegitMode;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.scanner.DoorScanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static void reset() { doors.clear(); }

    public static void tick() {
        if (!LegitMode.isActive() || !DungeonManager.isInDungeon()) {
            if (!doors.isEmpty()) doors.clear();
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

    private static int doorKey(int x1, int z1, int x2, int z2) {
        int a = x1 * N + z1, b = x2 * N + z2;
        return Math.min(a, b) * (N * N) + Math.max(a, b);
    }
}
