// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.List;
import java.util.Objects;
import java.util.SortedMap;

/** A melee-hit table. {@code quantiles} are percentiles 0..100 of the hit size (percent of max HP), {@code atoms} exact values with their probability. */
public class MeleeTable {
    public final int n;
    public final double perMinute;
    public final SortedMap<Double, Double> quantiles;
    public final List<Atom> atoms;
    public final boolean provisional;

    public MeleeTable(int n, double perMinute, SortedMap<Double, Double> quantiles, List<Atom> atoms, boolean provisional) {
        this.n = n; this.perMinute = perMinute; this.quantiles = quantiles; this.atoms = atoms; this.provisional = provisional;
    }

    public static final class Atom {
        public final double value;
        public final double p;

        public Atom(double value, double p) { this.value = value; this.p = p; }

        @Override public boolean equals(Object o) { return o instanceof Atom a && Double.compare(value, a.value) == 0 && Double.compare(p, a.p) == 0; }
        @Override public int hashCode() { return Objects.hash(value, p); }
        @Override public String toString() { return "Atom(value=" + value + ", p=" + p + ")"; }
    }
}
