// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TimedQueue<T> {
    private final ArrayList<Timed<T>> items = new ArrayList<>();

    public int size() { return items.size(); }

    public boolean isEmpty() { return items.isEmpty(); }

    public void add(int due, T what, String owner) { items.add(new Timed<>(due, what, owner)); }

    public void add(int due, T what) { add(due, what, null); }

    public List<T> takeDue(int now) {
        if (items.isEmpty()) return List.of();
        List<Timed<T>> due = new ArrayList<>();
        for (Timed<T> it : items) if (it.due <= now) due.add(it);
        if (due.isEmpty()) return List.of();
        items.removeIf(it -> it.due <= now);
        due.sort(Comparator.comparingInt(it -> it.due));
        List<T> out = new ArrayList<>(due.size());
        for (Timed<T> it : due) out.add(it.what);
        return out;
    }

    public void dropOwner(String owner) { items.removeIf(it -> Objects.equals(it.owner, owner)); }

    public void clear() { items.clear(); }

    public Set<String> owners() {
        Set<String> out = new LinkedHashSet<>();
        for (Timed<T> it : items) if (it.owner != null) out.add(it.owner);
        return out;
    }
}
