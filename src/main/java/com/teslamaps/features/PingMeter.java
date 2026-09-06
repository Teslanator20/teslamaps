package com.teslamaps.features;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

public class PingMeter {

    private static volatile long pending = Long.MIN_VALUE;
    private static volatile int mode = 0; // 0 = print locally, 1 = party chat, 2 = silent (just sample)

    private static volatile float lastRtt = 50f;

    public static float getLastPingMs() { return lastRtt; }

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
