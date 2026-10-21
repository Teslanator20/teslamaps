package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/MobDamageLedger.kt

import com.teslamaps.adm.sim.run.DamageSource;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MobDamageLedger {
    public static class Entry {
        public int hits = 0;
        public double total = 0.0;
        public final Map<DamageSource, Double> bySource = new EnumMap<>(DamageSource.class);
        public UUID lastBy = null;
    }

    private final HashMap<UUID, Entry> entries = new HashMap<>();

    public void record(UUID mob, double amount, DamageSource source, UUID by) {
        Entry e = entries.computeIfAbsent(mob, k -> new Entry());
        e.hits++;
        e.total += amount;
        e.bySource.merge(source, amount, Double::sum);
        if (by != null) e.lastBy = by;
    }

    public Entry of(UUID mob) { return entries.get(mob); }

    public void forget(UUID mob) { entries.remove(mob); }

    public void clear() { entries.clear(); }

    public int size() { return entries.size(); }
}
