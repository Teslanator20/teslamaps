// Ported from ADM's p3sim/clear/puzzles/BoulderLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Boulder as a state machine: the 6 by 7 grid at y = 66 becomes Odin's 42-character key, the clicks come from Odin's table
 * (see OdinTables); done when every click of the pattern has been made. UNMEASURED: what a wrong click does; here it only counts.
 */
public class BoulderLogic {
    public final List<int[]> clicks;
    private final List<int[]> remaining;
    public int misclicks = 0;

    public BoulderLogic(List<int[]> clicks) {
        this.clicks = clicks;
        this.remaining = new ArrayList<>(clicks);
    }

    public boolean solved() { return !clicks.isEmpty() && remaining.isEmpty(); }

    public int left() { return remaining.size(); }

    /** A click at (x, z) of y = 65 in the room's frame; true when it was one of the pattern's. */
    public boolean click(int x, int z) {
        int[] hit = null;
        for (int[] it : remaining) if (it[2] == x && it[3] == z) { hit = it; break; }
        if (hit == null) { misclicks++; return false; }
        remaining.remove(hit);
        return true;
    }

    public void reset() { remaining.clear(); remaining.addAll(clicks); misclicks = 0; }

    /** The grid cells Odin samples, in its order: z from 24 down to 9, x from 24 down to 6, step 3. */
    public static final List<Pair<Integer, Integer>> GRID;

    static {
        List<Pair<Integer, Integer>> g = new ArrayList<>();
        for (int z = 24; z >= 9; z -= 3) for (int x = 24; x >= 6; x -= 3) g.add(Pair.of(x, z));
        GRID = List.copyOf(g);
    }

    /** The key for a room: isAir says whether the cell (x, 66, z) is air. */
    public static String key(BiPredicate<Integer, Integer> isAir) {
        StringBuilder sb = new StringBuilder();
        for (Pair<Integer, Integer> p : GRID) sb.append(isAir.test(p.first, p.second) ? "0" : "1");
        return sb.toString();
    }

    /** The clicks for a key, or null when Odin's table does not know the pattern. */
    public static List<int[]> solutionFor(String key, Map<String, List<int[]>> table) { return table.get(key); }

    public static List<int[]> solutionFor(String key) { return solutionFor(key, OdinTables.boulder()); }
}
