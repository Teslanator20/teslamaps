// Ported from ADM's p3sim/clear/state/TickSchedule.kt
package com.teslamaps.adm.sim.clear.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class TickSchedule<T> {
    private static final class Entry<T> {
        final int at;
        final int seq;
        final T item;

        Entry(int at, int seq, T item) { this.at = at; this.seq = seq; this.item = item; }
    }

    private final ArrayList<Entry<T>> entries = new ArrayList<>();
    private int seq = 0;

    public void add(int at, T item) { entries.add(new Entry<>(at, seq++, item)); }

    public int size() { return entries.size(); }

    public List<T> due(int previous, int now) {
        List<Entry<T>> hit = new ArrayList<>();
        for (Entry<T> e : entries) if (e.at > previous && e.at <= now) hit.add(e);
        hit.sort(Comparator.<Entry<T>>comparingInt(e -> e.at).thenComparingInt(e -> e.seq));
        List<T> out = new ArrayList<>(hit.size());
        for (Entry<T> e : hit) out.add(e.item);
        return out;
    }

    public Integer timeOf(Predicate<T> match) {
        for (Entry<T> e : entries) if (match.test(e.item)) return e.at;
        return null;
    }

    public void clear() { entries.clear(); seq = 0; }
}
