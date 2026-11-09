package com.teslamaps.features;

// Ported from SkyHanni's CurrentChatDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Chat: Party (4 members)" while the chat is open and 2 s after. */
public final class CurrentChatDisplay {

    private static final Pattern CHANGED = Pattern.compile("^You are now in the (.+) channel$");
    private static final Pattern OPENED = Pattern.compile("^Opened a chat conversation with (?:\\[[^]]+] )?(\\w{1,16}) for the next 5 minutes\\. Use /chat a to leave$");
    private static final long CONVO_MS = 5 * 60_000L;

    private static String channel;
    private static String partner;
    private static long convoEnd;
    private static long closedAt;
    private static String line;
    private static HudEditElement el;

    private CurrentChatDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("currentChatDisplay", "Current Chat Channel").section("Chat")
                .enabled(() -> cfg().currentChatDisplay)
                .size(() -> 110, () -> 12)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "§aChat: §9Party §a(4 members)"));
        Feature.of("CurrentChatDisplay").section("Chat").when(() -> cfg().currentChatDisplay)
                .chat(CurrentChatDisplay::onChat)
                .tick(CurrentChatDisplay::tick)
                .hud("currentchatdisplay", CurrentChatDisplay::render);
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        PlayerChat.Msg pm = PlayerChat.current();
        if (pm != null) {
            if (pm.channel() == PlayerChat.Channel.DM_FROM || pm.channel() == PlayerChat.Channel.DM_TO) {
                if ("PRIVATE".equals(channel) && pm.author() != null && pm.author().equalsIgnoreCase(partner)) {
                    convoEnd = System.currentTimeMillis() + CONVO_MS;
                }
            }
            return;
        }
        if (!StringUtil.isServerMessage(raw)) return;
        Matcher m = CHANGED.matcher(t);
        if (m.matches()) {
            channel = m.group(1).toUpperCase();
            partner = null;
            return;
        }
        if (t.equals("You are not in a party and were moved to the ALL channel.")
                || t.equals("The conversation you were in expired and you have been moved back to the ALL channel.")) {
            channel = "ALL";
            partner = null;
            return;
        }
        m = OPENED.matcher(t);
        if (m.matches()) {
            channel = "PRIVATE";
            partner = m.group(1);
            convoEnd = System.currentTimeMillis() + CONVO_MS;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.gui.screen() instanceof ChatScreen) closedAt = now;
        if (channel == null || now - closedAt > 2000) {
            line = null;
            return;
        }
        StringBuilder sb = new StringBuilder("§aChat: ");
        switch (channel) {
            case "PRIVATE" -> {
                sb.append(partner != null ? "§6" + partner + " " : "§cUnknown ");
                long left = convoEnd - now;
                if (left <= 0) sb.append("§c(EXPIRED)");
                else sb.append("§b").append(left / 60_000).append("m ").append(left / 1000 % 60).append('s');
            }
            case "PARTY" -> {
                sb.append("§9Party");
                if (!PartyTracker.inParty()) sb.append(" §c(NOT IN PARTY)");
                else {
                    int size = PartyTracker.size();
                    if (mc.player != null && !PartyTracker.isMember(mc.player.getGameProfile().name())) size++;
                    sb.append(" §a(").append(size).append(" members)");
                }
            }
            case "ALL" -> sb.append("§eAll");
            case "GUILD" -> sb.append("§2Guild");
            case "OFFICER" -> sb.append("§3Officer");
            case "SKYBLOCK CO-OP" -> sb.append("§bCo-op");
            default -> sb.append("§f").append(channel);
        }
        line = sb.toString();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, el.x(), el.y(), el.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int w = mc.font.width(text);
        com.teslamaps.render.gui.HudStyle.panel(g, -2, -2, w + 4, 12, 2);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
