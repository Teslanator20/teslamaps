// Ported from ADM's p3sim/run/RunBus.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The run event bus: synchronous, single-threaded (sim server thread), deterministic. */
public class RunBus {
    private final BooleanSupplier serverThread;
    private final BiConsumer<RunEvent, Throwable> onError;

    public RunBus(BooleanSupplier serverThread, BiConsumer<RunEvent, Throwable> onError) {
        this.serverThread = serverThread;
        this.onError = onError;
    }

    public RunBus(BooleanSupplier serverThread) {
        this(serverThread, (e, t) -> {
            // fails on every event of a kind: reported in full once, then counted
            Integer held = RunBusKt.busFailures.admit(e.getClass().getSimpleName());
            if (held != null) Ec.LOG.error("[ads] a subscriber failed on " + e + RunBusKt.busFailures.suffix(held), t);
        });
    }

    public RunBus() { this(() -> true); }

    private static final class Sub {
        final int priority;
        final int seq;
        final Consumer<RunEvent> handler;
        boolean active = true;

        Sub(int priority, int seq, Consumer<RunEvent> handler) { this.priority = priority; this.seq = seq; this.handler = handler; }
    }

    /** Cancels a subscription. */
    public static class Subscription {
        private final Object sub;

        Subscription(Object sub) { this.sub = sub; }

        public void cancel() { ((Sub) sub).active = false; }
    }

    private List<Sub> subs = List.of();
    private int seq = 0;
    private final ArrayDeque<RunEvent> queue = new ArrayDeque<>();
    private boolean draining = false;
    private final ArrayDeque<RunEvent> recent = new ArrayDeque<>();

    /** Subscribes to every event. */
    public Subscription subscribe(int priority, Consumer<RunEvent> handler) {
        Sub s = new Sub(priority, seq++, handler);
        List<Sub> next = new ArrayList<>(subs);
        next.add(s);
        next.sort(Comparator.<Sub>comparingInt(x -> x.priority).thenComparingInt(x -> x.seq));
        subs = next;
        return new Subscription(s);
    }

    public Subscription subscribe(Consumer<RunEvent> handler) { return subscribe(0, handler); }

    /** Subscribes to events of one type. */
    public <T extends RunEvent> Subscription on(Class<T> type, int priority, Consumer<T> handler) {
        return subscribe(priority, e -> { if (type.isInstance(e)) handler.accept(type.cast(e)); });
    }

    public <T extends RunEvent> Subscription on(Class<T> type, Consumer<T> handler) { return on(type, 0, handler); }

    /** Delivered now if nothing is being delivered, else queued behind the waiting ones. */
    public void post(RunEvent event) {
        if (!serverThread.getAsBoolean()) throw new IllegalStateException("RunBus.post must be called on the sim server thread");
        if (queue.size() >= MAX_QUEUE) { Ec.LOG.error("[ads] event queue overflow, dropping " + event); return; }
        queue.addLast(event);
        if (draining) return;
        draining = true;
        try {
            while (!queue.isEmpty()) {
                RunEvent e = queue.removeFirst();
                remember(e);
                for (Sub s : subs) {
                    if (s.active) {
                        try { s.handler.accept(e); } catch (Throwable t) { onError.accept(e, t); }
                    }
                }
            }
        } finally { draining = false; }
    }

    private void remember(RunEvent e) {
        recent.addLast(e);
        while (recent.size() > KEEP) recent.removeFirst();
    }

    /** The last events delivered, oldest first. */
    public List<RunEvent> recentEvents() { return new ArrayList<>(recent); }

    /** Subscriber priority of the run's fan-out to the modules. */
    public static final int SYSTEMS_PRIORITY = 0;
    private static final int MAX_QUEUE = 100_000;
    private static final int KEEP = 200;
}
