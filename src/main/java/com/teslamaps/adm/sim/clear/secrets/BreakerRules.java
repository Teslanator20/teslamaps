// Ported from ADM's p3sim/clear/secrets/BreakerRules.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;
import java.util.Set;

public final class BreakerRules {
    private BreakerRules() {}

    public static final String REFUSAL = "§cA mystical force prevents you from digging that block!";
    public static final String THERE = "§cA mystical force prevents you digging there!";
    public static final String ROOM_LOCKED = "§cA mystical force prevents you digging in this room!";

    public static final Set<String> LOCKED_ROOMS = Set.of("Blood", "Fairy", "Tic Tac Toe", "Boulder", "Water Board", "Creeper Beams", "Teleport Maze");

    @FunctionalInterface
    public interface CellTest { boolean test(int x, int y, int z); }

    public static Integer cellRoom(DungeonPlan plan, int x, int z) {
        Pair<Integer, Boolean> tx = tileIndexOrGap(x);
        Pair<Integer, Boolean> tz = tileIndexOrGap(z);
        if (tx == null || tz == null) return null;
        List<Integer> xs = tx.second ? List.of(tx.first, tx.first + 1) : List.of(tx.first);
        List<Integer> zs = tz.second ? List.of(tz.first, tz.first + 1) : List.of(tz.first);
        Integer room = null;
        for (int a : xs) for (int b : zs) {
            PlacedRoom pr = plan.roomAt(a, b);
            if (pr == null) return null;
            int r = pr.index;
            if (room == null) room = r; else if (room != r) return null;
        }
        return room;
    }

    private static Pair<Integer, Boolean> tileIndexOrGap(int v) {
        int t = Math.floorDiv(v - DungeonFrame.ORIGIN, DungeonFrame.PITCH);
        if (t < 0 || t >= DungeonFrame.TILES) return null;
        int local = v - DungeonFrame.tileMin(t);
        if (local <= DungeonFrame.TILE_SIZE - 1) return Pair.of(t, false);
        if (t < DungeonFrame.TILES - 1) return Pair.of(t, true);
        return null;
    }

    public static boolean inGrid(int x, int z) {
        return x >= DungeonFrame.ORIGIN && x <= DungeonFrame.DUNGEON_MAX && z >= DungeonFrame.ORIGIN && z <= DungeonFrame.DUNGEON_MAX;
    }

    public static boolean isBorderOrOutside(DungeonPlan plan, int x, int z) {
        Integer room = cellRoom(plan, x, z);
        if (room == null) return true;
        for (int[] d : NEIGHBOURS) {
            Integer o = cellRoom(plan, x + d[0], z + d[1]);
            if (o == null || o.intValue() != room.intValue()) return true;
        }
        return false;
    }

    private static final List<int[]> NEIGHBOURS = List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1});

    public static String refusal(DungeonPlan plan, CellGroups groups, CellTest secretCell, int x, int y, int z) {
        if (groups.standingAt(x, y, z) != null) return REFUSAL;
        if (secretCell.test(x, y, z)) return REFUSAL;
        if (isBorderOrOutside(plan, x, z)) return THERE;
        PlacedRoom r = plan.roomAtBlock(x + 0.5, z + 0.5);
        if (r != null && r.name != null && LOCKED_ROOMS.contains(r.name)) return ROOM_LOCKED;
        return null;
    }
}
