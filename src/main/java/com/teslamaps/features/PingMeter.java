package com.teslamaps.features;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

/**
 * Real round-trip ping. Hypixel fakes the tab-list latency (PlayerInfo.getLatency() returns 1 for
 * your own entry), so instead we send a play-phase ping request and time the echoed pong — the same
 * mechanism vanilla's F3 PingDebugMonitor uses, so the server supports it.
 */
public class PingMeter {

    // The send time of our outstanding request (matched against the echoed pong). MIN_VALUE = none.
    private static volatile long pending = Long.MIN_VALUE;
    private static volatile boolean toParty = false; // where the matching result goes

    /** Measure ping and print the result locally in chat. */
    public static void request() { send(false); }

    /** Measure ping and post the result to party chat (/pc) — used by the !ping party command. */
    public static void requestToParty() { send(true); }

    private static void send(boolean party) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            if (!party && mc.player != null) mc.player.sendSystemMessage(Component.literal("§cPing unavailable (not connected)."));
            return;
        }
        long now = System.currentTimeMillis();
        pending = now;
        toParty = party;
        mc.getConnection().send(new ServerboundPingRequestPacket(now));
    }

    /** Called from the pong handler mixin with the echoed time. */
    public static void onPong(long time) {
        if (time != pending) return; // not our request (e.g. vanilla's F3 ping monitor)
        pending = Long.MIN_VALUE;
        boolean party = toParty;
        long rtt = System.currentTimeMillis() - time;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.getConnection() == null) return;
            if (party) {
                mc.getConnection().sendCommand("pc Ping: " + rtt + "ms");
            } else if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§aPing: §f" + rtt + "ms"));
            }
        });
    }
}
