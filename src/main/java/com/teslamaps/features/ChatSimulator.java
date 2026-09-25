package com.teslamaps.features;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

// Ported from SkyHanni's TestChatCommand
/** /tmap debug chat: fake lines through the full chat pipeline. */
public final class ChatSimulator {

    private static boolean silent, running;

    private ChatSimulator() {}

    /** Checked by ChatMixin after every handler ran. */
    public static boolean silent() { return silent; }

    public static boolean running() { return running; }

    /** Lines split on "\n" or real newlines, &-codes become §, a line starting with { or [ is JSON. */
    public static int send(String input, boolean quiet) {
        Minecraft mc = Minecraft.getInstance();
        if (input == null || input.isBlank()) return 0;
        int n = 0;
        for (String line : input.replace("\\n", "\n").split("\r?\n")) {
            if (line.isEmpty()) continue;
            Component c = parse(line);
            silent = quiet;
            running = true;
            try {
                mc.gui.getChat().addClientSystemMessage(c);
            } finally {
                silent = false;
                running = false;
            }
            n++;
        }
        if (quiet && mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Simulated §f" + n + " §7line(s) silently"));
        return n;
    }

    private static Component parse(String line) {
        String t = line.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                var r = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(t)).result();
                if (r.isPresent()) return r.get();
            } catch (Exception ignored) {
            }
        }
        return Component.literal(ampersand(line));
    }

    private static String ampersand(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&' && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) b.append('§');
            else b.append(ch);
        }
        return b.toString();
    }
}
