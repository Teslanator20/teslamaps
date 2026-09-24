package com.teslamaps.utils;

import java.util.regex.Pattern;

// String.replaceAll compiles the pattern on every call, which showed up in the
// profiler on the per-frame and per-chat-message paths.
public class StringUtil {

    private static final Pattern COLOR_CODES = Pattern.compile("(?i)§[0-9A-FK-OR]");

    public static String strip(String s) {
        if (s == null || s.indexOf('§') < 0) return s;
        return COLOR_CODES.matcher(s).replaceAll("");
    }

    public static String stripTrim(String s) {
        String stripped = strip(s);
        return stripped == null ? "" : stripped.trim();
    }

    /**
     * Player chat, so anything another player typed and could have made up.
     * <p>
     * Matches "Name: text", "[MVP+] Name: text", "Party > [MVP+] Name: text",
     * "Guild > Name [Officer]: text" and the /msg forms. A plain colon is not enough
     * on its own, real server lines like "Sweep Details: 442 Sweep" have one too - the
     * part before the colon has to look like a channel and a 16 character name.
     */
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            // Hypixel's own speakers use the same "[TAG] Name: text" shape
            "^(?!\\[(?:NPC|STATUE|BOSS|CROWD|SACKS|IMPORTANT|EVENT|OPEN|WATCHDOG ANNOUNCEMENT)\\])"
            // the channel prefixes always carry the arrow - without it "Guild Name: Sequoia"
            // from /g list reads as guild chat and the roster never gets parsed
            + "(?:(?:Party|Guild|Officer|Co-op|Team|Squad|Toggle) *> *)*"
            + "(?:(?:From|To|Sender) +)?"
            + "(?:\\[[^\\]]{1,24}\\] *)*"
            + "[A-Za-z0-9_]{1,16}"
            + "(?: \\[[^\\]]{1,24}\\])*: .*", Pattern.DOTALL);

    public static boolean isPlayerChat(String stripped) {
        if (stripped == null) return false;
        String s = stripped.trim();
        if (s.indexOf(':') < 0) return false;
        if (PLAYER_CHAT.matcher(s).matches()) return true;
        // level / emblem / ironman forms; rank-less all chat needs the colour check in PlayerChat
        PlayerChat.Msg m = PlayerChat.parse(s);
        return m != null && m.channel() != PlayerChat.Channel.NPC
                && (m.channel() != PlayerChat.Channel.ALL || m.level() != null || m.rank() != null);
    }

    /** Strips colours and reports whether the line is safe to act on. */
    public static boolean isServerMessage(String raw) {
        return !isPlayerChat(strip(raw));
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** "ultimate wise" -> "Ultimate Wise" */
    public static String titleCase(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : WHITESPACE.split(raw.trim().toLowerCase(java.util.Locale.ROOT))) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }
}
