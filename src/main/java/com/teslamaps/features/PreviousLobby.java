package com.teslamaps.features;

// SkyHanni's LastServers.kt + Devonian's PreviousLobby.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "You were already on this server 3m 12s ago." when a lobby hop lands on a known server. */
public final class PreviousLobby {

    private static final Pattern SENDING = Pattern.compile("^Sending to server (\\w+)\\.\\.\\.$");
    private static final Map<String, Long> SEEN = new HashMap<>();
    private static String current;
    private static int timer;

    private PreviousLobby() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PreviousLobby").section("Chat").when(() -> cfg().previousLobby)
                .serverChat(PreviousLobby::onChat)
                .tick(PreviousLobby::tick)
                .config("Chat", b -> {
                    b.toggle("Previous Lobby Alert", () -> cfg().previousLobby, v -> cfg().previousLobby = v)
                            .desc("Tells you when a server switch puts you back on a lobby you were on recently");
                    b.slider("  Remember (minutes)", 1f, 60f,
                            () -> (float) cfg().previousLobbyMinutes, v -> cfg().previousLobbyMinutes = v.intValue());
                });
    }

    // keeps the current server fresh, so the time is measured from when you left it
    private static void tick() {
        if (current == null || timer-- > 0) return;
        timer = 20;
        SEEN.put(current, System.currentTimeMillis());
    }

    private static void onChat(String raw) {
        String text = StringUtil.stripTrim(raw);
        if (!text.startsWith("Sending to server ")) return;
        Matcher m = SENDING.matcher(text);
        if (!m.matches()) return;
        String id = m.group(1);
        long now = System.currentTimeMillis();
        if (current != null) SEEN.put(current, now);
        long window = cfg().previousLobbyMinutes * 60_000L;
        SEEN.values().removeIf(t -> now - t > window);
        Long last = id.equals(current) ? null : SEEN.get(id);
        current = id;
        SEEN.put(id, now);
        if (last == null || now - last < 1000) return;
        String ago = format(now - last);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§7You were already on this server §b" + ago + " §7ago."));
            }
        });
    }

    private static String format(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        return m > 0 ? m + "m " + (s % 60) + "s" : s + "s";
    }
}
