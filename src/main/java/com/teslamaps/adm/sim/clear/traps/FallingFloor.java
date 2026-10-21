package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/FallingFloor.kt

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.clear.puzzles.hard.BlockEdit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A floor that lets go: when the trigger happens every block of it becomes a falling-block entity in the same tick (air, a
 * lava-extinguish sound), once. cells are canonical [x, y, z]; extra cells are cleared too. No fall damage.
 */
public class FallingFloor implements Hazard {
    public final List<int[]> cells;
    public final List<int[]> extra;
    public final FloorTrigger trigger;

    /** Whether the floor has gone. */
    public boolean fired = false;

    private final Set<Long> cellSet = new HashSet<>();

    public FallingFloor(List<int[]> cells, List<int[]> extra, FloorTrigger trigger) {
        this.cells = cells;
        this.extra = extra;
        this.trigger = trigger;
        for (int[] c : cells) cellSet.add(cellKey(c[0], c[1], c[2]));
    }

    @Override
    public void tick(int clock, List<PlayerPos> players, HazardOut out) {
        if (fired) return;
        int[] soundAt;
        if (trigger instanceof FloorTrigger.StandOn) {
            PlayerPos p = null;
            for (PlayerPos q : players) if (standsOnFloor(q)) { p = q; break; }
            if (p == null) return;
            soundAt = new int[]{(int) Math.floor(p.x), (int) Math.floor(p.y + 1e-4) - 1, (int) Math.floor(p.z)};
        } else if (trigger instanceof FloorTrigger.StringTouched st) {
            if (!out.touched.contains(st.line)) return;
            // the recorded sound sat at the far end of the walkway
            int[] best = null;
            for (int[] c : cells) {
                if (best == null || c[0] > best[0] || (c[0] == best[0] && c[2] > best[2])) best = c;
            }
            if (best == null) return;
            soundAt = best;
        } else return;
        fired = true;
        out.debris.addAll(cells);
        out.debris.addAll(extra);
        out.edits.addAll(clearEdits());
        out.cues.add(new SoundCue(HazardSound.FLOOR_COLLAPSE, soundAt[0] + 0.5, soundAt[1] + 0.5, soundAt[2] + 0.5));
    }

    /** Whether the player's feet are on (or less than a block above) a cell of the floor. */
    public boolean standsOnFloor(PlayerPos p) {
        return cellSet.contains(cellKey((int) Math.floor(p.x), (int) Math.floor(p.y + 1e-4) - 1, (int) Math.floor(p.z)));
    }

    private List<BlockEdit> clearEdits() {
        List<BlockEdit> out = new ArrayList<>();
        for (int[] c : cells) out.add(new BlockEdit(c[0], c[1], c[2], Crusher.AIR));
        for (int[] c : extra) out.add(new BlockEdit(c[0], c[1], c[2], Crusher.AIR));
        return out;
    }

    @Override public List<BlockEdit> currentEdits() { return fired ? clearEdits() : List.of(); }

    @Override public void snapshot(JsonObject o) { o.addProperty("fired", fired); }

    @Override
    public void restore(JsonObject o) {
        JsonElement e = o.get("fired");
        fired = e != null && e.getAsBoolean();
    }

    /** One number for a cell (20 bits a coordinate). */
    public static long cellKey(int x, int y, int z) {
        return (((long) x & 0xFFFFF) << 40) | (((long) y & 0xFFFFF) << 20) | ((long) z & 0xFFFFF);
    }

    /** The connected part of all (side or corner neighbours on one level) holding the cell nearest to near; every cell when near is null. */
    public static List<int[]> cluster(List<int[]> all, int[] near) {
        if (near == null || all.isEmpty()) return all;
        Map<Long, int[]> byKey = new HashMap<>();
        for (int[] c : all) byKey.put(cellKey(c[0], c[1], c[2]), c);
        int[] start = null;
        int bestD = Integer.MAX_VALUE;
        for (int[] c : all) {
            int d = Math.abs(c[0] - near[0]) + Math.abs(c[1] - near[1]) + Math.abs(c[2] - near[2]);
            if (d < bestD) { bestD = d; start = c; }
        }
        if (start == null) return all;
        HashSet<Long> seen = new HashSet<>();
        List<int[]> out = new ArrayList<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(start); seen.add(cellKey(start[0], start[1], start[2]));
        while (!queue.isEmpty()) {
            int[] c = queue.removeFirst();
            out.add(c);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int[] n = byKey.get(cellKey(c[0] + dx, c[1], c[2] + dz));
                if (n == null) continue;
                if (seen.add(cellKey(n[0], n[1], n[2]))) queue.add(n);
            }
        }
        return out;
    }
}
