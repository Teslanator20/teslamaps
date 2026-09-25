package com.teslamaps.features;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Outgoing announcements. Every line goes through one queue with a minimum gap, so two
 * announcements in the same tick are not dropped by Hypixel's chat limit. Ported from
 * critterMod's ChatQueue (1.2 s gap, 250 char cap, cleared on disconnect).
 */
public final class Announce {

    public static final String NOBODY = "Nobody";
    public static final String PARTY = "Party";
    public static final String ALL = "All";
    public static final String[] CHANNELS = {NOBODY, PARTY, ALL};

    private static final long GAP_MS = 1200L;
    private static final int MAX_LEN = 250;

    private static final ConcurrentLinkedDeque<String> QUEUE = new ConcurrentLinkedDeque<>();
    private static long lastSent = 0L;

    private Announce() {}

    /** Any thread. Nobody (or an unknown channel) sends nothing. */
    public static void send(String channel, String message) {
        if (message == null || message.isBlank() || ChatSimulator.running()) return;
        String prefix = PARTY.equals(channel) ? "pc " : ALL.equals(channel) ? "ac " : null;
        if (prefix != null) command(prefix + message);
    }

    public static void party(String message) {
        send(PARTY, message);
    }

    /** A raw command without the leading slash, queued like an announcement. */
    public static void command(String command) {
        String c = command.length() > MAX_LEN ? command.substring(0, MAX_LEN) : command;
        if (!c.equals(QUEUE.peekLast())) QUEUE.addLast(c);
    }

    public static int pending() {
        return QUEUE.size();
    }

    /** Client tick, main thread. */
    public static void tick() {
        if (QUEUE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            QUEUE.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSent < GAP_MS) return;
        String next = QUEUE.pollFirst();
        if (next == null) return;
        lastSent = now;
        mc.getConnection().sendCommand(next);
    }
}
