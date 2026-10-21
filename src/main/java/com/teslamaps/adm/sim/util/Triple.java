package com.teslamaps.adm.sim.util;

import java.util.Objects;

public final class Triple<A, B, C> {
    public final A first;
    public final B second;
    public final C third;

    public Triple(A first, B second, C third) { this.first = first; this.second = second; this.third = third; }

    public static <A, B, C> Triple<A, B, C> of(A a, B b, C c) { return new Triple<>(a, b, c); }

    @Override public boolean equals(Object o) {
        return o instanceof Triple<?, ?, ?> p && Objects.equals(first, p.first) && Objects.equals(second, p.second) && Objects.equals(third, p.third);
    }
    @Override public int hashCode() { return Objects.hash(first, second, third); }
    @Override public String toString() { return "(" + first + ", " + second + ", " + third + ")"; }
}
