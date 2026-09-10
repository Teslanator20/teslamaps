package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Shows an image somebody shared as part of their chat message: the token is taken out of
 * the line, blank lines are appended to make room, and ChatImageMixin draws the picture
 * into that gap.
 */
public class SharedImageChat {

    // uploads come back on files.catbox.moe or litter.catbox.moe, so any subdomain counts
    private static final Pattern LINK = Pattern.compile(
            "(https?://(?:[\\w-]+\\.)*catbox\\.moe/\\S+\\.(?:png|jpg|jpeg))");

    // what the mod itself sends: a tag and a token, no domain in the message
    private static final Pattern TOKEN = Pattern.compile(
            Pattern.quote(ImageShare.TAG) + "\\s+([LF]-[A-Za-z0-9]{1,32}-[pj])");

    private static final Pattern SPEAKER =
            Pattern.compile("^(?:[\\w-]+ ?> )?(?:\\[[^\\]]+\\] *)*([A-Za-z0-9_]{1,16})");

    /** Lines that carry an image, keyed by their plain text. */
    private static final Map<String, String> LINE_TO_URL = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 64;
        }
    };

    private static String newestUrl;
    private static String newestSender;

    public static String newestUrl() {
        return newestUrl;
    }

    public static String newestSender() {
        return newestSender;
    }

    public static String urlForLine(String lineText) {
        if (lineText == null) return null;
        return LINE_TO_URL.get(StringUtil.strip(lineText).trim());
    }

    /** @return the rewritten line, or null to leave the message alone. */
    public static Component decorate(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.imageShareReceive) return null;

        String clean = StringUtil.strip(message.getString());
        if (!StringUtil.isPlayerChat(clean)) return null;

        String url = null;
        String remove = null;
        Matcher token = TOKEN.matcher(clean);
        if (token.find()) {
            url = ImageShare.fromToken(token.group(1));
            remove = token.group();
        } else {
            Matcher m = LINK.matcher(clean);
            if (m.find()) {
                url = m.group(1);
                remove = m.group(1);
            }
        }
        if (url == null) return null;

        String sender = sender(clean);
        if (isBlocked(sender)) return Component.literal("§8[TeslaMaps] blocked image from " + sender);

        Component line = without(message, remove);
        LINE_TO_URL.put(StringUtil.strip(line.getString()).trim(), url);
        newestUrl = url;
        newestSender = sender;

        SharedImageLoader.load(url, loaded -> { }, error -> { });
        return line;
    }

    /** Rebuilds the line without the token, keeping every colour it had. */
    private static Component without(Component message, String remove) {
        MutableComponent out = Component.empty();
        message.visit((style, text) -> {
            String cleaned = text.replace(remove, "").replaceAll(" {2,}", " ");
            if (!cleaned.isBlank()) out.append(Component.literal(cleaned).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** Draws nothing, but a line holding it still takes a row. */
    private static final net.minecraft.util.FormattedCharSequence PAD = sink -> true;

    public static boolean isPad(net.minecraft.client.multiplayer.chat.GuiMessage.Line line) {
        return line.content() == PAD;
    }

    /**
     * Puts the empty rows the picture is drawn into right below their message.
     * <p>
     * These used to be empty chat messages of their own. Chat mods that filter the chat -
     * Polyfrost's Chatting drops whatever the active tab does not match - threw them away and
     * the picture lost its room. Written straight into the line list they are past every filter,
     * and re-added whenever the list is rebuilt.
     */
    public static void ensurePadding(net.minecraft.client.gui.components.ChatComponent chat) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.imageShareReceive) return;

        List<net.minecraft.client.multiplayer.chat.GuiMessage.Line> lines =
                ((com.teslamaps.mixin.ChatComponentAccessor) chat).teslamaps$getTrimmedMessages();
        int gap = Math.max(1, c.imageShareChatLines);

        for (int i = 0; i < lines.size(); i++) {
            net.minecraft.client.multiplayer.chat.GuiMessage.Line line = lines.get(i);
            if (isPad(line)) continue;
            if (urlForLine(line.parent().content().getString()) == null) continue;
            // only the bottom row of a wrapped message, the gap goes underneath the whole thing
            if (i > 0 && lines.get(i - 1).parent() == line.parent() && !isPad(lines.get(i - 1))) continue;

            int have = 0;
            while (have < gap && i - 1 - have >= 0 && isPad(lines.get(i - 1 - have))) have++;
            for (int n = have; n < gap; n++) {
                lines.add(i, new net.minecraft.client.multiplayer.chat.GuiMessage.Line(
                        line.parent(), PAD, true));
            }
            i += gap - have;
        }
    }

    public static boolean isBlocked(String name) {
        for (String blocked : TeslaMapsConfig.get().imageShareBlocked) {
            if (blocked.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static void block(String name) {
        if (isBlocked(name)) return;
        TeslaMapsConfig.get().imageShareBlocked.add(name);
        TeslaMapsConfig.save();
        ImageShare.feedback("§7[TeslaMaps] Blocked images from §f" + name);
    }

    public static void unblock(String name) {
        TeslaMapsConfig.get().imageShareBlocked.removeIf(n -> n.equalsIgnoreCase(name));
        TeslaMapsConfig.save();
        ImageShare.feedback("§7[TeslaMaps] Unblocked §f" + name);
    }

    private static String sender(String clean) {
        Matcher m = SPEAKER.matcher(clean.trim());
        return m.find() ? m.group(1) : "Someone";
    }
}
