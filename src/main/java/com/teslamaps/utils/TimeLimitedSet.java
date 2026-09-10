package com.teslamaps.utils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Set whose entries expire after a fixed duration. Expiry is lazy — contains()
 * drops a stale entry itself, cleanup() is only needed to bound memory.
 * Ported from bax's utils/TimeLimitedSet.
 */
public class TimeLimitedSet<T> {
    private final ConcurrentHashMap<T, Long> entries = new ConcurrentHashMap<>();
    private final long durationMs;

    public TimeLimitedSet(long durationMs) {
        this.durationMs = durationMs;
    }

    public void add(T item) {
        entries.put(item, System.currentTimeMillis());
    }

    public boolean contains(T item) {
        Long added = entries.get(item);
        if (added == null) return false;
        if (System.currentTimeMillis() - added > durationMs) {
            entries.remove(item);
            return false;
        }
        return true;
    }

    /** true if the item was not present (and is now added) — one call for the "seen already?" pattern. */
    public boolean addIfAbsent(T item) {
        if (contains(item)) return false;
        add(item);
        return true;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e -> now - e.getValue() > durationMs);
    }

    public void remove(T item) {
        entries.remove(item);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
