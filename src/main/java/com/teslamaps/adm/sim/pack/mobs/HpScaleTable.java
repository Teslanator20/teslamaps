// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

/** The per-run multiplier on every base HP: draw {@code values[i]} with probability {@code counts[i] / n}. */
public class HpScaleTable {
    public final int n;
    public final double[] values;
    public final int[] counts;

    public HpScaleTable(int n, double[] values, int[] counts) { this.n = n; this.values = values; this.counts = counts; }
}
