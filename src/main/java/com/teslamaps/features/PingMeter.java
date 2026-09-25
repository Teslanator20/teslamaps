package com.teslamaps.features;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

public class PingMeter {

    private static volatile long pending = Long.MIN_VALUE;
    private static volatile int mode = 0; // 0 = print locally, 1 = party chat, 2 = silent (just sample)

    private static volatile float lastRtt = 50f;

    public static float getLastPingMs() { return lastRtt; }

    private static final long[] samples = new long[15];
    private static int sampleIdx, sampleCount;

    public static float medianPingMs() {
        long[] a;
        synchronized (samples) {
            if (sampleCount == 0) return lastRtt;
            a = java.util.Arrays.copyOf(samples, sampleCount);
        }
        java.util.Arrays.sort(a);
        int n = a.length;
        return n % 2 == 1 ? a[n / 2] : (a[n / 2 - 1] + a[n / 2]) / 2f;
    }

    public static float meanPingMs() {
        synchronized (samples) {
            if (sampleCount == 0) return lastRtt;
            long sum = 0;
            for (int i = 0; i < sampleCount; i++) sum += samples[i];
            return (float) sum / sampleCount;
        }
    }

    public static void clearSamples() {
        synchronized (samples) { sampleCount = 0; sampleIdx = 0; }
    }

    public static void request() { send(0); }

    public static void requestToParty() { send(1); }

    public static void requestSilent() { send(2); }

    private static void send(int requestMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            if (requestMode == 0 && mc.player != null) mc.player.sendSystemMessage(Component.literal("§cPing unavailable (not connected)."));
            return;
        }
        long now = System.currentTimeMillis();
        pending = now;
        mode = requestMode;
        mc.getConnection().send(new ServerboundPingRequestPacket(now));
    }

    public static void onPong(long time) {
        if (time != pending) return; // not our request (e.g. vanilla's F3 ping monitor)
        pending = Long.MIN_VALUE;
        int m = mode;
        long rtt = System.currentTimeMillis() - time;
        lastRtt = rtt;
        synchronized (samples) {
            samples[sampleIdx] = rtt;
            sampleIdx = (sampleIdx + 1) % samples.length;
            if (sampleCount < samples.length) sampleCount++;
        }
        if (m == 2) return; // silent sample
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() == null) return;
            if (m == 1) {
                mc.getConnection().sendCommand("pc Ping: " + rtt + "ms");
            } else if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§aPing: §f" + rtt + "ms"));
            }
        });
    }
}
