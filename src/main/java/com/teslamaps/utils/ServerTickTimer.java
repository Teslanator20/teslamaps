package com.teslamaps.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Countdowns in server ticks. Hypixel sends one ping packet per server tick, so a lagging
 * server stretches the countdown the same way it stretches the event being timed. Without
 * pings (singleplayer, other servers) the client tick stands in.
 */
public final class ServerTickTimer {

    private static final long NO_PING_MS = 250L;
    private static final List<ServerTickTimer> PENDING = new ArrayList<>();

    private static volatile long ticks;
    private static volatile long lastPingMs;
    private static volatile int lastPingId = Integer.MIN_VALUE;

    private long end = -1;
    private Runnable onDone;

    /** Mixin hook, any thread. The same id arrives twice (netty + main), counted once. */
    public static void onPing(int id) {
        if (id == 0 || id == lastPingId) return;
        lastPingId = id;
        lastPingMs = System.currentTimeMillis();
        ticks++;
    }

    /** Client tick: fallback clock and due callbacks. */
    public static void clientTick() {
        if (System.currentTimeMillis() - lastPingMs > NO_PING_MS) ticks++;
        if (PENDING.isEmpty()) return;
        long now = ticks;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            ServerTickTimer t = PENDING.get(i);
            if (t.end < 0) { PENDING.remove(i); continue; }
            if (now < t.end) continue;
            PENDING.remove(i);
            Runnable r = t.onDone;
            t.onDone = null;
            if (r != null) r.run();
        }
    }

    public static long now() {
        return ticks;
    }

    /** True while the server actually sends tick pings. */
    public static boolean serverDriven() {
        return System.currentTimeMillis() - lastPingMs <= NO_PING_MS;
    }

    /** 57 -> "2.85s". */
    public static String seconds(long ticks) {
        return String.format(java.util.Locale.ROOT, "%.2fs", ticks / 20.0);
    }

    public void start(int durationTicks) {
        start(durationTicks, null);
    }

    /** Main thread only. {@code onDone} runs on the client tick the countdown reaches 0. */
    public void start(int durationTicks, Runnable onDone) {
        end = ticks + durationTicks;
        this.onDone = onDone;
        if (onDone != null && !PENDING.contains(this)) PENDING.add(this);
    }

    public void stop() {
        end = -1;
        onDone = null;
    }

    public boolean active() {
        return end >= 0 && ticks < end;
    }

    /** Ticks left, 0 once done or stopped. */
    public long remaining() {
        return end < 0 ? 0 : Math.max(0, end - ticks);
    }
}
