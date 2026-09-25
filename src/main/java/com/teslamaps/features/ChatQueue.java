package com.teslamaps.features;

// Ported from Skyblocker's utils/scheduler/MessageScheduler.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Every automatic message of the mod, with a minimum gap once the queue is on. */
public final class ChatQueue {

    private static final int MAX_LEN = 256;

    private record Msg(String text, boolean command) {}

    private static final ConcurrentLinkedQueue<Msg> QUEUE = new ConcurrentLinkedQueue<>();
    private static long lastSent;

    private ChatQueue() {}

    public static void register() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> QUEUE.clear());
        Feature.of("ChatQueue")
                .tickAlways(ChatQueue::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Send Queue");
                    b.toggle("Queue Outgoing Messages", () -> c.chatSendQueue, v -> c.chatSendQueue = v)
                            .desc("Party callouts, chat command replies and coords go through one queue with a minimum gap, so Hypixel does not drop them as \"too fast\"");
                    b.slider("  Minimum Gap (ms)", 100f, 1500f, () -> (float) c.chatSendQueueGap,
                            v -> c.chatSendQueueGap = Math.round(v), 0);
                });
    }

    /** Command without the leading slash. Main thread when the queue is off. */
    public static void command(String command) {
        send(command, true);
    }

    public static void chat(String message) {
        send(message, false);
    }

    private static void send(String text, boolean command) {
        if (text == null || text.isEmpty()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatSendQueue || !c.section("Chat")) {
            direct(text, command);
            return;
        }
        QUEUE.add(new Msg(text.length() > MAX_LEN ? text.substring(0, MAX_LEN) : text, command));
    }

    private static void direct(String text, boolean command) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        if (command) conn.sendCommand(text);
        else conn.sendChat(text);
    }

    public static void tick() {
        if (QUEUE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            QUEUE.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSent < Math.max(50, TeslaMapsConfig.get().chatSendQueueGap)) return;
        Msg m = QUEUE.poll();
        if (m == null) return;
        lastSent = now;
        direct(m.text(), m.command());
    }
}
