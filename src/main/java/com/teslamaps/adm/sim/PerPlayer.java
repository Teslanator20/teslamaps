package com.teslamaps.adm.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;

/** One value per player (keyed by Sim.player()'s UUID). */
public final class PerPlayer<T> {
    private static final class Box<T> { T v; Box(T v) { this.v = v; } }

    private static final UUID NOBODY = new UUID(0, 0);
    private static final Set<PerPlayer<?>> ALL = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private final Supplier<T> init;
    private final ConcurrentHashMap<UUID, Box<T>> map = new ConcurrentHashMap<>();

    public PerPlayer(Supplier<T> init) {
        this.init = init;
        ALL.add(this);
    }

    private Box<T> box() {
        ServerPlayer p = Sim.player();
        UUID id = p != null ? p.getUUID() : NOBODY;
        return map.computeIfAbsent(id, k -> new Box<>(init.get()));
    }

    public T get() { return box().v; }

    public void set(T v) { box().v = v; }

    public List<T> values() {
        List<T> out = new ArrayList<>();
        for (Box<T> b : map.values()) out.add(b.v);
        return out;
    }

    public static void clearAll() {
        List<PerPlayer<?>> copy;
        synchronized (ALL) { copy = new ArrayList<>(ALL); }
        for (PerPlayer<?> p : copy) p.map.clear();
    }
}
