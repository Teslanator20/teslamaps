package com.teslamaps.adm.sim.util;

import java.util.Objects;

public final class Pair<A, B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) { this.first = first; this.second = second; }

    public static <A, B> Pair<A, B> of(A a, B b) { return new Pair<>(a, b); }

    @Override public boolean equals(Object o) {
        return o instanceof Pair<?, ?> p && Objects.equals(first, p.first) && Objects.equals(second, p.second);
    }
    @Override public int hashCode() { return Objects.hash(first, second); }
    @Override public String toString() { return "(" + first + ", " + second + ")"; }
}
