package com.teslamaps.features;

// Slim take on SkyHanni's PlayerNameFormatter.kt + SkyOcean's WhiteNonMessage.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Rebuilds the author part of player chat lines (rank, level brackets, name colour) and can
 * turn grey message text white. Runs right before vanilla stores the line, after every
 * handler saw the original, and keeps click/hover styles per character.
 */
public final class PlayerChatFormat {

    private static final TextColor GRAY = TextColor.fromLegacyFormat(ChatFormatting.GRAY);

    private PlayerChatFormat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PlayerChatFormat")
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Player Chat Format");
                    b.toggle("Reformat Player Chat", () -> c.playerChatFormat, v -> c.playerChatFormat = v)
                            .desc("Cleans up player messages in all, party, guild, co-op and private chat.")
                            .tags("rank", "level", "player chat");
                    b.toggle("  Hide Ranks", () -> c.playerChatHideRank, v -> c.playerChatHideRank = v)
                            .desc("[MVP+] and co. are removed, the name keeps the rank colour.");
                    b.toggle("    Keep YouTube / Admin", () -> c.playerChatKeepYoutube, v -> c.playerChatKeepYoutube = v);
                    b.toggle("  Level Without Brackets", () -> c.playerChatNoLevelBrackets, v -> c.playerChatNoLevelBrackets = v)
                            .desc("[312] becomes 312.");
                    b.toggle("  Name In Level Colour", () -> c.playerChatLevelColorName, v -> c.playerChatLevelColorName = v)
                            .desc("All chat only, the other channels show no level.");
                    b.toggle("  White Message Text", () -> c.playerChatWhiteText, v -> c.playerChatWhiteText = v)
                            .desc("Grey text of non-rank players and private messages turns white.");
                });
    }

    private record Ch(char c, Style s) {}

    public static Component rewrite(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.playerChatFormat || !c.section("Chat")) return message;
        try {
            return format(message, c);
        } catch (RuntimeException e) {
            return message;
        }
    }

    private static Component format(Component message, TeslaMapsConfig c) {
        PlayerChat.Msg m = PlayerChat.parse(message);
        if (m == null || m.channel() == PlayerChat.Channel.NPC || m.channel() == PlayerChat.Channel.SHOW_ITEM) return message;

        List<Ch> chars = expand(message);
        StringBuilder sb = new StringBuilder(chars.size());
        for (Ch ch : chars) sb.append(ch.c);
        String s = sb.toString();
        if (s.isEmpty() || Character.isWhitespace(s.charAt(0)) || s.indexOf(m.author()) < 0) return message;

        int authorStart = switch (m.channel()) {
            case PARTY, COOP, GUILD, OFFICER -> s.indexOf(" > ") + 3;
            case DM_FROM -> s.startsWith("From ") ? 5 : -1;
            case DM_TO -> s.startsWith("To ") ? 3 : -1;
            default -> 0;
        };
        if (authorStart < 0 || authorStart >= s.length()) return message;
        int authorEnd = s.indexOf(": ", authorStart);
        if (authorEnd < 0) return message;

        String rank = m.rank();
        boolean dropRank = c.playerChatHideRank && rank != null
                && !(c.playerChatKeepYoutube && (rank.contains("YOUTUBE") || rank.contains("ADMIN")));

        // level colour = colour of its first digit
        TextColor levelColor = null;
        List<Ch> author = new ArrayList<>(authorEnd - authorStart);
        int i = authorStart;
        while (i < authorEnd) {
            int end = s.indexOf(' ', i);
            if (end < 0 || end > authorEnd) end = authorEnd;
            String token = s.substring(i, end);
            boolean bracket = token.length() > 2 && token.charAt(0) == '[' && token.charAt(token.length() - 1) == ']';
            boolean level = bracket && m.level() != null && token.equals("[" + m.level() + "]") && i == authorStart;
            boolean isRank = bracket && !level && rank != null && token.equals("[" + rank + "]");
            boolean isName = token.equals(m.author());

            if (isRank && dropRank) {
                i = end + 1;
                continue;
            }
            if (!author.isEmpty()) author.add(chars.get(i - 1));
            for (int k = i; k < end; k++) {
                Ch ch = chars.get(k);
                if (level) {
                    if (levelColor == null && Character.isDigit(ch.c)) levelColor = ch.s.getColor();
                    if (c.playerChatNoLevelBrackets && (ch.c == '[' || ch.c == ']')) continue;
                }
                if (isName && c.playerChatLevelColorName && levelColor != null) ch = new Ch(ch.c, ch.s.withColor(levelColor));
                author.add(ch);
            }
            i = end + 1;
        }

        List<Ch> out = new ArrayList<>(chars.size());
        out.addAll(chars.subList(0, authorStart));
        out.addAll(author);
        boolean white = c.playerChatWhiteText;
        for (int k = authorEnd; k < chars.size(); k++) {
            Ch ch = chars.get(k);
            if (white && k >= authorEnd + 2 && Objects.equals(ch.s.getColor(), GRAY)) {
                ch = new Ch(ch.c, ch.s.withColor(ChatFormatting.WHITE));
            }
            out.add(ch);
        }
        return collapse(out);
    }

    /** One entry per visible char, legacy § codes folded into the style. */
    private static List<Ch> expand(Component message) {
        List<Ch> out = new ArrayList<>(message.getString().length());
        message.visit((base, text) -> {
            Style st = base;
            for (int k = 0, n = text.length(); k < n; k++) {
                char ch = text.charAt(k);
                if (ch == '§' && k + 1 < n) {
                    ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(k + 1)));
                    k++;
                    if (f == null) continue;
                    if (f == ChatFormatting.RESET) {
                        st = Style.EMPTY.withClickEvent(base.getClickEvent()).withHoverEvent(base.getHoverEvent());
                    } else {
                        st = st.applyLegacyFormat(f);
                    }
                    continue;
                }
                out.add(new Ch(ch, st));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static Component collapse(List<Ch> chars) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        Style cur = null;
        for (Ch ch : chars) {
            if (cur != null && !ch.s.equals(cur)) {
                out.append(Component.literal(run.toString()).setStyle(cur));
                run.setLength(0);
            }
            cur = ch.s;
            run.append(ch.c);
        }
        if (cur != null && !run.isEmpty()) out.append(Component.literal(run.toString()).setStyle(cur));
        return out;
    }
}
