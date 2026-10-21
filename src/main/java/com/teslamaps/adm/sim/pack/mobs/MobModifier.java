// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Map;
import java.util.Objects;

/** A name prefix word ("" = none) with its weight, HP factor and colours. {@code weight} null for a floor = reuse F7. */
public final class MobModifier {
    public final String id;
    public final Map<String, Double> weight;
    public final double hpFactor;
    public final String nameColor;
    public final String hpColor;
    /** False for Flaming: the head loses its trailing {@code §r}, so the word inherits the star's gold. */
    public final boolean headReset;
    /** Plain row only: the HP colour once hurt ({@code e}) and the share of max HP at or below which it applies (0.5). */
    public final String hpColorHurt;
    public final Double hpThreshold;

    public MobModifier(String id, Map<String, Double> weight, double hpFactor, String nameColor, String hpColor, boolean headReset,
                       String hpColorHurt, Double hpThreshold) {
        this.id = id;
        this.weight = weight;
        this.hpFactor = hpFactor;
        this.nameColor = nameColor;
        this.hpColor = hpColor;
        this.headReset = headReset;
        this.hpColorHurt = hpColorHurt;
        this.hpThreshold = hpThreshold;
    }

    public MobModifier(String id, Map<String, Double> weight, double hpFactor, String nameColor, String hpColor, boolean headReset, String hpColorHurt) {
        this(id, weight, hpFactor, nameColor, hpColor, headReset, hpColorHurt, null);
    }

    public MobModifier(String id, Map<String, Double> weight, double hpFactor, String nameColor, String hpColor, boolean headReset) {
        this(id, weight, hpFactor, nameColor, hpColor, headReset, null);
    }

    public MobModifier(String id, Map<String, Double> weight, double hpFactor, String nameColor, String hpColor) {
        this(id, weight, hpFactor, nameColor, hpColor, true);
    }

    @Override public boolean equals(Object o) {
        return o instanceof MobModifier m && Objects.equals(id, m.id) && Objects.equals(weight, m.weight) && Double.compare(hpFactor, m.hpFactor) == 0
                && Objects.equals(nameColor, m.nameColor) && Objects.equals(hpColor, m.hpColor) && headReset == m.headReset
                && Objects.equals(hpColorHurt, m.hpColorHurt) && Objects.equals(hpThreshold, m.hpThreshold);
    }
    @Override public int hashCode() { return Objects.hash(id, weight, hpFactor, nameColor, hpColor, headReset, hpColorHurt, hpThreshold); }
    @Override public String toString() {
        return "MobModifier(id=" + id + ", weight=" + weight + ", hpFactor=" + hpFactor + ", nameColor=" + nameColor + ", hpColor=" + hpColor
                + ", headReset=" + headReset + ", hpColorHurt=" + hpColorHurt + ", hpThreshold=" + hpThreshold + ")";
    }
}
