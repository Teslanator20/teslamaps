package com.teslamaps.features;

// Channel compaction after HypixelSimpleChat's ChannelFormat / Zen's CleanPartyMessage,
// discord warning after HypixelSimpleChat's ChatRules.stripDiscordWarning
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "Guild > [MVP+] Name [Member]: hi" -> "G > Name: hi", and Hypixel's discord warning cut off. */
public final class ChatCompact {

    private static final Pattern DISCORD_WARNING = Pattern.compile(
            "\\s*Please be mindful of Discord links in chat as they may pose a security risk\\.?\\s*",
            Pattern.CASE_INSENSITIVE);

    private record Edit(int start, int end, String with) {}

    private ChatCompact() {}

    /** True = the line is nothing but the warning. From ChatMixin's HEAD handler. */
    public static boolean dropLine(String text) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatStripDiscordWarning || !c.section("Chat")) return false;
        if (text.indexOf("mindful") < 0) return false;
        return DISCORD_WARNING.matcher(StringUtil.strip(text)).matches();
    }

    /** Right before vanilla stores the line, after every handler saw the original. */
    public static Component rewrite(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatCompactChannels && !c.chatStripDiscordWarning) return message;
        if (!c.section("Chat")) return message;
        String raw = message.getString();
        String s = StringUtil.strip(raw);
        List<Edit> edits = new ArrayList<>();

        if (c.chatStripDiscordWarning && s.indexOf("mindful") >= 0) {
            Matcher m = DISCORD_WARNING.matcher(s);
            if (m.find() && m.start() > 0) edits.add(new Edit(m.start(), m.end(), ""));
        }
        if (c.chatCompactChannels) channelEdits(c, message, s, edits);
        if (edits.isEmpty()) return message;
        edits.sort((a, b) -> Integer.compare(a.start, b.start));
        for (int i = 1; i < edits.size(); i++) {
            if (edits.get(i).start < edits.get(i - 1).end) return message;
        }
        return apply(message, raw, edits);
    }

    private static void channelEdits(TeslaMapsConfig c, Component message, String s, List<Edit> edits) {
        if (hasItem(message)) return;
        PlayerChat.Msg pm = PlayerChat.parse(message);
        if (pm == null) return;
        int head;
        switch (pm.channel()) {
            case PARTY -> {
                if (!c.chatCompactParty || !s.startsWith("Party > ")) return;
                edits.add(new Edit(0, 5, "P"));
                head = 8;
            }
            case GUILD -> {
                if (!c.chatCompactGuild || !s.startsWith("Guild > ")) return;
                edits.add(new Edit(0, 5, "G"));
                head = 8;
            }
            case OFFICER -> {
                if (!c.chatCompactGuild || !s.startsWith("Officer > ")) return;
                edits.add(new Edit(0, 7, "O"));
                head = 10;
            }
            case ALL -> {
                if (!c.chatCompactPublic) return;
                head = 0;
                if (pm.level() != null) {
                    String lv = "[" + pm.level() + "] ";
                    if (!s.startsWith(lv)) return;
                    if (c.chatCompactHideLevel) edits.add(new Edit(0, lv.length(), ""));
                    head = lv.length();
                }
                String ar = pm.authorRaw();
                if (!ar.isEmpty() && ar.charAt(0) != '[' && !Character.isLetterOrDigit(ar.charAt(0)) && ar.charAt(0) != '_') {
                    int sp = s.indexOf(' ', head);
                    if (sp > head && c.chatCompactHideEmblem) edits.add(new Edit(head, sp + 1, ""));
                }
            }
            default -> {
                return;
            }
        }
        int colon = s.indexOf(": ", head);
        if (colon < 0) return;
        if (c.chatCompactHideRank && pm.rank() != null) {
            String tag = "[" + pm.rank() + "] ";
            int at = s.indexOf(tag, head);
            if (at >= 0 && at < colon) edits.add(new Edit(at, at + tag.length(), ""));
        }
        if (c.chatCompactHideGuildRank && pm.guildRank() != null) {
            String tag = " [" + pm.guildRank() + "]";
            int at = s.lastIndexOf(tag, colon);
            if (at >= head && at + tag.length() <= colon) edits.add(new Edit(at, at + tag.length(), ""));
        }
    }

    private static boolean hasItem(Component c) {
        return c.visit((style, text) -> style.getHoverEvent() instanceof HoverEvent.ShowItem
                ? Optional.of(Boolean.TRUE) : Optional.<Boolean>empty(), Style.EMPTY).isPresent();
    }

    private static boolean isCode(char ch) {
        ch = Character.toLowerCase(ch);
        return ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'f' || ch >= 'k' && ch <= 'o' || ch == 'r';
    }

    /** Edits are on stripped positions; styles and § codes outside them stay. */
    private static Component apply(Component message, String raw, List<Edit> edits) {
        int[] map = new int[raw.length() + 1];
        int n = 0;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '§' && i + 1 < raw.length() && isCode(raw.charAt(i + 1))) {
                i++;
                continue;
            }
            map[n++] = i;
        }
        int[] rs = new int[edits.size()], re = new int[edits.size()];
        for (int k = 0; k < edits.size(); k++) {
            Edit e = edits.get(k);
            if (e.end > n || e.start >= e.end) return message;
            rs[k] = map[e.start];
            re[k] = map[e.end - 1] + 1;
        }
        List<String> texts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        message.visit((style, text) -> {
            texts.add(text);
            styles.add(style);
            return Optional.empty();
        }, Style.EMPTY);

        MutableComponent out = Component.empty();
        int pos = 0, k = 0;
        for (int si = 0; si < texts.size(); si++) {
            String t = texts.get(si);
            StringBuilder b = new StringBuilder(t.length());
            for (int j = 0; j < t.length(); j++, pos++) {
                while (k < rs.length && pos >= re[k]) k++;
                if (k < rs.length && pos >= rs[k]) {
                    if (pos == rs[k]) b.append(edits.get(k).with);
                    continue;
                }
                b.append(t.charAt(j));
            }
            if (b.length() > 0) out.append(Component.literal(b.toString()).setStyle(styles.get(si)));
        }
        return out;
    }
}
