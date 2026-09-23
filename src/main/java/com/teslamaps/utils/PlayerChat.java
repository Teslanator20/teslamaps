package com.teslamaps.utils;

// Ported from SkyHanni's PlayerChatManager.kt
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a chat line into channel, author and message. Everything that is not player chat
 * parses to {@code null} (system line). Works on stripped text: every channel prefix sits at
 * the start of the line, where no player can put text of his own.
 * <p>
 * ChatMixin calls {@link #begin} once per line, {@link #current()} parses lazily and caches.
 */
public final class PlayerChat {

    public enum Channel { PARTY, COOP, GUILD, OFFICER, DM_FROM, DM_TO, ALL, NPC, SHOW_ITEM }

    /** {@code author} is the bare name, {@code authorRaw} the part with rank and emblems. */
    public record Msg(Channel channel, String author, String authorRaw, String rank, String level,
                      String guildRank, String message, boolean own) {
        public boolean isDm() { return channel == Channel.DM_FROM || channel == Channel.DM_TO; }
    }

    private static final Pattern PREFIXED = Pattern.compile(
            "^(Party|Co-op|Guild|Officer) > (?<author>[^:]{1,60}?): (?<msg>.*)$", Pattern.DOTALL);
    private static final Pattern DM = Pattern.compile(
            "^(From|To) (?!stash: )(?<author>[^:]{1,40}?): (?<msg>.*)$", Pattern.DOTALL);
    private static final Pattern NPC = Pattern.compile("^\\[NPC] (?<author>[^:]{1,40}?): (?<msg>.*)$", Pattern.DOTALL);
    // [level] emblem [rank] name: message
    private static final Pattern ALL = Pattern.compile(
            "^(?:\\[(?<level>\\d{1,4})] )?(?<author>(?:[^\\w\\s\\[]{1,2} )?(?:\\[[^\\]]{1,20}] )?[A-Za-z0-9_]{1,16})(?: [^\\w\\s:]{1,2})?: (?<msg>.*)$",
            Pattern.DOTALL);
    private static final Pattern SHOW_ITEM = Pattern.compile(
            "^(?:\\[(?<level>\\d{1,4})] )?(?<author>(?:[^\\w\\s\\[]{1,2} )?(?:\\[[^\\]]{1,20}] )?[A-Za-z0-9_]{1,16})(?: ♲)* (?<msg>(?:is holding|is friends with a|is wearing|has) \\[.+])$",
            Pattern.DOTALL);
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    // Hypixel's own speakers use the player shape too
    private static final Pattern NOT_A_RANK = Pattern.compile("BOSS|STATUE|CROWD|SACKS|IMPORTANT|EVENT|OPEN|WATCHDOG ANNOUNCEMENT|NPC|Bazaar|Auction|SkyBlock|Skyblock");

    private static Component current;
    private static Msg parsed;
    private static boolean done;

    private PlayerChat() {}

    public record State(Component current, Msg parsed, boolean done) {}

    /** Returns the outer line's state; hand it to {@link #end} so nested lines don't clobber it. */
    public static State begin(Component message) {
        State prev = new State(current, parsed, done);
        current = message;
        parsed = null;
        done = false;
        return prev;
    }

    public static void end(State prev) {
        current = prev.current;
        parsed = prev.parsed;
        done = prev.done;
    }

    /** The line ChatMixin is handling right now; null = system line. */
    public static Msg current() {
        if (!done) {
            done = true;
            parsed = current == null ? null : parse(current);
        }
        return parsed;
    }

    /** Like {@link #parse(String)}, plus the colour check SkyHanni does for rank-less all chat. */
    public static Msg parse(Component c) {
        Msg m = parse(c.getString());
        if (m == null || m.channel != Channel.ALL || m.level != null || m.rank != null) return m;
        // "Area: Hub" has the same shape; a real player line is grey or white up to the colon
        String f = ChatText.formatted(c).replace("§r", "");
        return f.contains(m.author + "§7: ") || f.contains(m.author + "§f: ") || m.own ? m : null;
    }

    public static Msg parse(String raw) {
        if (raw == null) return null;
        String s = StringUtil.strip(raw).trim();
        if (s.indexOf(' ') < 0) return null;

        Matcher m = PREFIXED.matcher(s);
        if (m.matches()) {
            Channel ch = switch (m.group(1)) {
                case "Party" -> Channel.PARTY;
                case "Co-op" -> Channel.COOP;
                case "Guild" -> Channel.GUILD;
                default -> Channel.OFFICER;
            };
            return build(ch, m.group("author"), null, m.group("msg"));
        }
        m = DM.matcher(s);
        if (m.matches()) {
            return build(m.group(1).equals("From") ? Channel.DM_FROM : Channel.DM_TO, m.group("author"), null, m.group("msg"));
        }
        m = NPC.matcher(s);
        if (m.matches()) {
            String author = m.group("author").trim();
            return new Msg(Channel.NPC, author, "[NPC] " + author, "NPC", null, null, m.group("msg"), false);
        }
        m = ALL.matcher(s);
        if (m.matches()) return build(Channel.ALL, m.group("author"), m.group("level"), m.group("msg"));
        m = SHOW_ITEM.matcher(s);
        if (m.matches()) return build(Channel.SHOW_ITEM, m.group("author"), m.group("level"), m.group("msg"));
        return null;
    }

    // "[MVP+] Name [Officer]" / "⚔ [MVP++] Name" -> rank, name, guild rank
    private static Msg build(Channel ch, String authorRaw, String level, String message) {
        String rank = null, guildRank = null, name = null;
        for (String token : authorRaw.trim().split(" ")) {
            if (token.isEmpty()) continue;
            if (token.startsWith("[") && token.endsWith("]")) {
                String inner = token.substring(1, token.length() - 1);
                if (name == null) {
                    if (NOT_A_RANK.matcher(inner).matches()) return null;
                    rank = inner;
                } else {
                    guildRank = inner;
                }
                continue;
            }
            if (NAME.matcher(token).matches()) {
                if (name != null) return null;
                name = token;
            } else if (name != null) {
                return null;
            }
        }
        if (name == null) return null;
        return new Msg(ch, name, authorRaw.trim(), rank, level, guildRank, message, isOwn(name));
    }

    private static boolean isOwn(String name) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getGameProfile().name().equalsIgnoreCase(name);
    }
}
