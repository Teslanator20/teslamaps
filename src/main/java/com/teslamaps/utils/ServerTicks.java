package com.teslamaps.utils;

/**
 * Server-side tick estimate, fed by the time packet (one every 20 server ticks).
 * The client clock keeps running at 20/s while the server lags, so a countdown
 * anchored to a server event has to be measured against this instead.
 * Ported from GanKura's GameState.Server.
 */
public final class ServerTicks {

    private static final double PACKET_INTERVAL = 20.0;
    private static final double SMOOTHING = 0.3;

    private static volatile long lastPacketTicks = 0;
    private static volatile long lastPacketMillis = 0;
    private static volatile double tps = 20.0;
    private static double lastEstimate = 0;

    private ServerTicks() {}

    /** netty thread - timestamps only */
    public static void onTimePacket(long gameTime) {
        long now = System.currentTimeMillis();
        long dTicks = gameTime - lastPacketTicks;
        long dMillis = now - lastPacketMillis;
        if (lastPacketMillis > 0 && dTicks > 0 && dMillis > 0) {
            double sample = Math.min(20.0, dTicks * 1000.0 / dMillis);
            tps = tps + (sample - tps) * SMOOTHING;
        }
        lastPacketTicks = gameTime;
        lastPacketMillis = now;
    }

    public static void reset() {
        lastPacketTicks = 0;
        lastPacketMillis = 0;
        tps = 20.0;
        lastEstimate = 0;
    }

    public static double tps() {
        return tps;
    }

    public static boolean ready() {
        return lastPacketMillis != 0;
    }

    /** Current server tick, interpolated between packets at the measured TPS. */
    public static double now() {
        // before the first packet the only clock we have is the client's
        if (lastPacketMillis == 0) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? 0 : level.getGameTime();
        }
        long since = System.currentTimeMillis() - lastPacketMillis;
        double advanced = Math.min(since * tps / 1000.0, PACKET_INTERVAL);
        double estimate = lastPacketTicks + Math.max(0.0, advanced);
        // never walk backwards inside one packet gap - a countdown that grows reads as broken
        if (estimate < lastEstimate && lastEstimate - estimate <= PACKET_INTERVAL) return lastEstimate;
        lastEstimate = estimate;
        return estimate;
    }

    /** For anchoring a target time; falls back while no packet was seen yet. */
    public static long now(long fallback) {
        if (lastPacketMillis == 0) return fallback;
        return Math.round(now());
    }
}
