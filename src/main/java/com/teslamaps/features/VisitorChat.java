package com.teslamaps.features;

// Ported from SkyHanni's GardenVisitorCompactChat.kt and GardenVisitorChat.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OFFER ACCEPTED block as one line, visitor NPC chatter and the arrival line hidden. */
public final class VisitorChat {

    private static final Pattern ACCEPTED = Pattern.compile("^OFFER ACCEPTED with (.+) \\((.+)\\)$");
    private static final Pattern NPC = Pattern.compile("^\\[NPC] (.+?): .*");
    private static final Pattern AMOUNT_FIRST = Pattern.compile("^([\\d,]+(?:\\.\\d)?k?)x? (.+)$");
    private static final Pattern AMOUNT_LAST = Pattern.compile("^(.+?) x(\\d+)$");
    private static final Pattern COLOR = Pattern.compile("§([0-9a-f])");
    private static final Pattern TAB_COUNT = Pattern.compile("^Visitors: \\(\\d+\\)");
    private static final Set<String> NAMELESS = Set.of("Copper", "Farming XP", "Farming Experience", "Garden Experience", "Bits");

    private static final List<Component> held = new ArrayList<>();
    private static final List<String> rewards = new ArrayList<>();
    private static final Set<String> visitors = new HashSet<>();
    private static String visitor = "";
    private static long lastLine;
    private static boolean emitting;
    private static int ticks;

    private VisitorChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("VisitorChat").section("Garden").when(() -> cfg().visitorChat)
                .tick(VisitorChat::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Visitor Chat");
                    b.toggle("Visitor Chat Cleanup", () -> c.visitorChat, v -> c.visitorChat = v)
                            .desc("Less visitor spam in chat. Needs the Chat section on")
                            .tags("visitor", "offer accepted", "compact", "spam", "garden");
                    b.toggle("  Compact OFFER ACCEPTED", () -> c.visitorCompactRewards, v -> c.visitorCompactRewards = v)
                            .desc("The reward block becomes one line, hover it for the original");
                    b.toggle("  Hide Visitor NPC Chat", () -> c.visitorHideNpcChat, v -> c.visitorHideNpcChat = v)
                            .desc("What the visitors say. Jacob, Anita and the other garden NPCs stay");
                    b.toggle("  Hide \"has arrived\" Line", () -> c.visitorHideArrival, v -> c.visitorHideArrival = v);
                });
    }

    private static void tick() {
        if (!visitor.isEmpty() && System.currentTimeMillis() - lastLine > 250) flush();
        if (ticks++ % 40 != 0 || !SkyblockUtils.isInGarden() || !cfg().visitorHideNpcChat) return;
        boolean in = false;
        for (String raw : TabListUtils.getOrderedLines()) {
            String l = StringUtil.stripTrim(raw);
            if (TAB_COUNT.matcher(l).find()) { in = true; continue; }
            if (!in) continue;
            if (l.isEmpty() || l.endsWith(":")) break;
            if (visitors.size() < 500) visitors.add(l);
        }
    }

    /** From the chat filter; true = drop the line. */
    static boolean handle(Component message, String stripped) {
        TeslaMapsConfig c = cfg();
        if (emitting || !c.visitorChat || !c.section("Garden")) return false;
        PlayerChat.Msg pm = PlayerChat.current();
        String t = stripped.trim();
        if (pm == null && c.visitorHideArrival && t.endsWith(" has arrived on your Garden!")) return true;
        if (!SkyblockUtils.isInGarden()) return false;
        if (c.visitorHideNpcChat && (pm == null || pm.channel() == PlayerChat.Channel.NPC) && npcLine(message, t)) return true;
        if (pm != null || !c.visitorCompactRewards) return false;

        Matcher m = ACCEPTED.matcher(t);
        if (m.matches()) {
            if (!visitor.isEmpty()) flush();
            String fmt = ChatText.formatted(message).replace("§r", "");
            visitor = colorBefore(fmt, m.group(1)) + m.group(1);
            held.add(message);
            lastLine = System.currentTimeMillis();
            return true;
        }
        if (visitor.isEmpty()) return false;
        if (t.equals("REWARDS")) {
            held.add(message);
            lastLine = System.currentTimeMillis();
            return true;
        }
        if (!stripped.startsWith("    ") || t.isEmpty()) return false;
        held.add(message);
        lastLine = System.currentTimeMillis();
        rewards.add(reward(ChatText.formatted(message).replace("§r", ""), t));
        return true;
    }

    private static boolean npcLine(Component message, String t) {
        Matcher m = NPC.matcher(t);
        if (!m.matches()) return false;
        String name = m.group(1);
        String color = colorBefore(ChatText.formatted(message).replace("§r", ""), name);
        if (color.isEmpty() || color.equals("§e")) return false;
        return visitors.contains(name);
    }

    /** Colour code right in front of {@code name} in the formatted line, "" if none. */
    private static String colorBefore(String fmt, String name) {
        int i = fmt.indexOf(name);
        if (i < 2 || fmt.charAt(i - 2) != '§') return "";
        return fmt.substring(i - 2, i);
    }

    private static String reward(String fmt, String t) {
        String color = "§f";
        Matcher cm = COLOR.matcher(fmt);
        while (cm.find()) {
            char ch = cm.group(1).charAt(0);
            if (ch != '7' && ch != '8') { color = "§" + ch; break; }
        }
        if (t.startsWith("+")) t = t.substring(1);
        Matcher a = AMOUNT_FIRST.matcher(t);
        if (a.matches()) {
            String name = a.group(2);
            return NAMELESS.contains(name) ? color + a.group(1) : color + a.group(1) + " " + name;
        }
        a = AMOUNT_LAST.matcher(t);
        if (a.matches()) return color + a.group(2) + " " + a.group(1);
        return color + t;
    }

    private static void flush() {
        List<Component> lines = new ArrayList<>(held);
        String out = "§6§lOFFER ACCEPTED §7w/§r" + visitor + "§6§l!§r " + String.join("§7, ", rewards);
        held.clear();
        rewards.clear();
        visitor = "";
        MutableComponent hover = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) hover.append("\n");
            hover.append(lines.get(i));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        emitting = true;
        try {
            mc.player.sendSystemMessage(Component.literal(out).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
        } finally {
            emitting = false;
        }
    }
}
