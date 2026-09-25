package com.teslamaps.features;

// Ported from Skysoft's ChatNotifier.kt (longest match wins, bold colour, ping, ignore own)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.ChatUserFilter;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;

/** Own chat filters with the "Notify" action: highlight the match and ping. */
public final class ChatNotify {

    public static final String ACTION = "Notify";

    private ChatNotify() {}

    public static boolean isNotify(ChatUserFilter f) {
        return f != null && ACTION.equals(f.action);
    }

    /** Rewritten line, or null when no notify filter matched. */
    public static Component decorate(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        List<ChatUserFilter> filters = c.chatUserFilters;
        if (filters == null || filters.isEmpty()) return null;
        boolean any = false;
        for (ChatUserFilter f : filters) if (isNotify(f)) { any = true; break; }
        if (!any) return null;
        if (c.chatNotifyIgnoreOwn && own()) return null;

        StringBuilder plain = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        StringDecomposer.iterateFormatted(message, Style.EMPTY, (i, style, cp) -> {
            int before = plain.length();
            plain.appendCodePoint(cp);
            for (int k = before; k < plain.length(); k++) styles.add(style);
            return true;
        });
        String text = plain.toString();

        List<int[]> ranges = new ArrayList<>();
        for (ChatUserFilter f : filters) {
            if (!isNotify(f) || f.text == null || f.text.isEmpty()) continue;
            find(f, text, ranges);
        }
        if (ranges.isEmpty()) return null;

        ranges.sort((a, b) -> (b[1] - b[0]) != (a[1] - a[0]) ? (b[1] - b[0]) - (a[1] - a[0]) : a[0] - b[0]);
        boolean[] hit = new boolean[text.length()];
        for (int[] r : ranges) {
            boolean free = true;
            for (int i = r[0]; i < r[1]; i++) if (hit[i]) { free = false; break; }
            if (free) for (int i = r[0]; i < r[1]; i++) hit[i] = true;
        }

        int color;
        try {
            color = Integer.parseInt(c.chatNotifyColor, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            color = 0xFFFF55;
        }
        MutableComponent out = Component.empty();
        int start = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (i < text.length() && hit[i] == hit[start] && styles.get(i).equals(styles.get(start))) continue;
            Style s = styles.get(start);
            if (hit[start]) s = s.withColor(color).withBold(true);
            out.append(Component.literal(text.substring(start, i)).setStyle(s));
            start = i;
        }

        if (c.chatNotifyVolume > 0) {
            Minecraft.getInstance().execute(() ->
                    LoudSound.play(SoundOptions.resolve(c.chatNotifySound), c.chatNotifyVolume, 1.0f));
        }
        return out;
    }

    private static void find(ChatUserFilter f, String text, List<int[]> out) {
        String needle = f.text;
        switch (f.mode == null ? "contains" : f.mode) {
            case "regex" -> {
                Matcher m = ChatUserFilters.regex(needle).matcher(text);
                while (m.find()) if (m.end() > m.start()) out.add(new int[]{m.start(), m.end()});
            }
            case "equals", "startswith", "endswith" -> {
                String t = text.trim();
                int lead = text.indexOf(t.isEmpty() ? " " : t.substring(0, 1));
                boolean ok = switch (f.mode) {
                    case "equals" -> t.equals(needle);
                    case "startswith" -> t.startsWith(needle);
                    default -> t.endsWith(needle);
                };
                if (!ok || lead < 0) return;
                if (f.mode.equals("endswith")) out.add(new int[]{lead + t.length() - needle.length(), lead + t.length()});
                else out.add(new int[]{lead, lead + needle.length()});
            }
            default -> {
                String low = text.toLowerCase(Locale.ROOT);
                String n = needle.toLowerCase(Locale.ROOT);
                if (low.length() != text.length()) return;
                int from = 0;
                while (true) {
                    int at = low.indexOf(n, from);
                    if (at < 0) break;
                    out.add(new int[]{at, at + n.length()});
                    from = at + 1;
                }
            }
        }
    }

    private static boolean own() {
        PlayerChat.Msg m = PlayerChat.current();
        if (m == null) return false;
        if (m.channel() == PlayerChat.Channel.DM_TO) return true;
        var p = Minecraft.getInstance().player;
        return p != null && m.author() != null && m.author().equalsIgnoreCase(p.getGameProfile().name());
    }
}
