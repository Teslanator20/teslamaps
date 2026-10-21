package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/Crusher.kt

import com.teslamaps.adm.sim.clear.puzzles.hard.BlockEdit;
import java.util.ArrayList;
import java.util.List;

/**
 * One crusher: a slab of material blocks (box x0, y0, z0, x1, y1, z1, canonical, absolute y) that grows sweep layers along axis in
 * direction sign and shrinks back; the home slab never changes. startAt is the room-clock tick of its first step out.
 */
public class Crusher {
    public final String id;
    public final int[] slab;
    public final char axis;
    public final int sign;
    public final int sweep;
    public final String material;
    public final CrusherTiming timing;
    public final int startAt;

    public Crusher(String id, int[] slab, char axis, int sign, int sweep, String material, CrusherTiming timing, int startAt) {
        this.id = id;
        this.slab = slab;
        this.axis = axis;
        this.sign = sign;
        this.sweep = sweep;
        this.material = material;
        this.timing = timing;
        this.startAt = startAt;
    }

    public Crusher(String id, int[] slab, char axis, int sign, int sweep, String material, CrusherTiming timing) {
        this(id, slab, axis, sign, sweep, material, timing, START_LAG);
    }

    /** How many layers out the crusher is t ticks into the room's hazard clock. */
    public int extensionAt(int t) {
        if (t < startAt) return 0;
        int period = timing.period(sweep);
        int u = Math.floorMod(t - startAt + timing.stillTicks, period);
        if (u < timing.stillTicks) return 0;
        int moves = Math.min(2 * sweep, (u - timing.stillTicks) / timing.ticksPerBlock + 1);
        return moves <= sweep ? moves : 2 * sweep - moves;
    }

    /** The cells of layer j (1 is next to the slab). */
    public List<int[]> layer(int j) {
        int dx = axis == 'x' ? sign * j : 0;
        int dz = axis == 'z' ? sign * j : 0;
        List<int[]> out = new ArrayList<>();
        for (int y = slab[1]; y <= slab[4]; y++) for (int z = slab[2]; z <= slab[5]; z++) for (int x = slab[0]; x <= slab[3]; x++) out.add(new int[]{x + dx, y, z + dz});
        return out;
    }

    /** The cells the arm fills when it is k layers out: layers 1..k. */
    public List<int[]> cells(int k) {
        List<int[]> out = new ArrayList<>();
        for (int j = 1; j <= k; j++) out.addAll(layer(j));
        return out;
    }

    /** The box (inclusive corners x0, y0, z0, x1, y1, z1) the arm fills when it is k layers out (k at least 1). */
    public int[] armBox(int k) {
        int a = sign; int b = sign * k;
        int lo = Math.min(a, b); int hi = Math.max(a, b);
        int x0 = slab[0] + (axis == 'x' ? lo : 0); int x1 = slab[3] + (axis == 'x' ? hi : 0);
        int z0 = slab[2] + (axis == 'z' ? lo : 0); int z1 = slab[5] + (axis == 'z' ? hi : 0);
        return new int[]{x0, slab[1], z0, x1, slab[4], z1};
    }

    /** The block edits that take the arm from 'from' layers out to 'to'. */
    public List<BlockEdit> edits(int from, int to) {
        if (from == to) return List.of();
        List<BlockEdit> out = new ArrayList<>();
        if (to > from) { for (int j = from + 1; j <= to; j++) for (int[] c : layer(j)) out.add(new BlockEdit(c[0], c[1], c[2], material)); }
        else for (int j = from; j >= to + 1; j--) for (int[] c : layer(j)) out.add(new BlockEdit(c[0], c[1], c[2], AIR));
        return out;
    }

    /** The middle of the tip layer's face when the arm is k layers out (canonical x, y, z). */
    public double[] tipFace(int k) {
        List<int[]> tip = layer(Math.max(k, 1));
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        if (tip.isEmpty()) throw new java.util.NoSuchElementException();
        for (int[] c : tip) {
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
        }
        double cx = (minX + maxX + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        double fx = axis == 'x' ? (sign > 0 ? maxX + 1.0 : (double) minX) : cx;
        double fz = axis == 'z' ? (sign > 0 ? maxZ + 1.0 : (double) minZ) : cz;
        return new double[]{fx, slab[1], fz};
    }

    public static final String AIR = "minecraft:air";

    /** Ticks between a room waking up and its crushers' first step (recorded 0..7, median 4). */
    public static final int START_LAG = 4;
}
