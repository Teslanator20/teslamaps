package com.teslamaps.features.mining;

// After HypixelSimpleChat's MiningSummary.kt
import com.teslamaps.utils.LegacyFormat;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.ChatFilter;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Crystal Hollows treasure / loot chest reward block as one chat line. */
public final class MiningChestSummary {

    public static final String[] MODES = {"Off", "Compact", "Grey", "Hide"};

    private static final Pattern BAR = Pattern.compile("▬{16,}");
    private static final Pattern TITLE = Pattern.compile("(?:CHEST LOCKPICKED|LOOT CHEST COLLECTED)");
    private static final Pattern GAIN = Pattern.compile("(.+?)(?: x([\\d,]+))?");
    private static final int MAX_LINES = 24;

    private static boolean active, emitting;
    private static int seen;
    private static String title;
    private static final List<String> gains = new ArrayList<>();
    private static final List<Component> lines = new ArrayList<>();

    private MiningChestSummary() {}

    /** From ChatFilter.process; true = drop the line. */
    public static boolean handle(Component message, String text, String t) {
        String mode = TeslaMapsConfig.get().miningChestSummary;
        if (emitting) return false;
        if (mode == null || mode.equals("Off")) {
            active = false;
            return false;
        }
        String trimmed = t.trim();
        String legacy = legacy(message, text);
        if (BAR.matcher(trimmed).matches() && chestBar(legacy)) {
            if (!active) {
                if (!Mining.onMiningIsland()) return false;
                active = true;
                seen = 0;
                title = null;
                gains.clear();
                lines.clear();
                lines.add(message);
                return true;
            }
            active = false;
            lines.add(message);
            if (title == null) {
                release();
                return true;
            }
            if (mode.equals("Hide") || gains.isEmpty()) return true;
            ChatFilter.queueRewrite(text, summary(mode.equals("Grey")));
            return false;
        }
        if (!active) return false;
        if (PlayerChat.current() != null) return false;
        if (++seen > MAX_LINES) {
            active = false;
            release();
            return false;
        }
        if (trimmed.isEmpty() || trimmed.equals("REWARDS")) {
            lines.add(message);
            return true;
        }
        if (TITLE.matcher(trimmed).matches()) {
            String name = trimmed.replace(" LOCKPICKED", "").replace(" COLLECTED", "");
            title = "§" + colorBefore(legacy, "CHEST", '6') + "§l" + name;
            lines.add(message);
            return true;
        }
        if (title != null && indent(t) >= 4) {
            Matcher m = GAIN.matcher(trimmed);
            if (m.matches()) {
                String name = "§" + colorBefore(legacy, m.group(1), 'f') + m.group(1);
                gains.add(m.group(2) == null ? name : name + " §7x" + m.group(2));
                lines.add(message);
                return true;
            }
        }
        active = false;
        release();
        return false;
    }

    private static Component summary(boolean grey) {
        String head = title == null ? "§6§lCHEST" : title;
        String body = String.join(" §8· ", gains);
        String s = grey ? "§8" + StringUtil.strip(head + " " + body) : head + " §r" + body;
        MutableComponent hover = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) hover.append("\n");
            hover.append(lines.get(i));
        }
        lines.clear();
        gains.clear();
        return Component.literal(s).withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    /** Not a chest block after all: put the swallowed lines back, in order. */
    private static void release() {
        List<Component> back = new ArrayList<>(lines);
        lines.clear();
        gains.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        emitting = true;
        try {
            for (Component c : back) player.sendSystemMessage(c);
        } finally {
            emitting = false;
        }
    }

    private static int indent(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') n++;
        return n;
    }

    // chest bars are yellow or pink, the other ▬ blocks use other colours
    private static boolean chestBar(String legacy) {
        char c = colorBefore(legacy, "▬", ' ');
        return c == 'e' || c == 'd';
    }

    private static char colorBefore(String legacy, String part, char fallback) {
        int end = legacy.indexOf(part);
        if (end < 0) return fallback;
        for (int i = end - 2; i >= 0; i--) {
            if (legacy.charAt(i) != '§') continue;
            char c = Character.toLowerCase(legacy.charAt(i + 1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) return c;
        }
        return fallback;
    }

    private static String legacy(Component message, String text) {
        if (text.indexOf('§') >= 0) return text;
        StringBuilder sb = new StringBuilder();
        message.visit((style, part) -> {
            TextColor color = style.getColor();
            if (color != null) {
                ChatFormatting f = LegacyFormat.getByName(color.serialize());
                if (f != null) sb.append('§').append(LegacyFormat.getChar(f));
            }
            sb.append(part);
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }
}
