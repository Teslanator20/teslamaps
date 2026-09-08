package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
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

    /** Set while the padding lines are pushed, so the empty-line filter leaves them alone. */
    private static boolean sendingPadding = false;

    public static boolean sendingPadding() {
        return sendingPadding;
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
        padding(c.imageShareChatLines);
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

    /** Empty lines render nothing but still take a row, which is the gap the image fills. */
    private static void padding(int lines) {
        Minecraft mc = Minecraft.getInstance();
        com.teslamaps.utils.TickScheduler.schedule(1, () -> {
            if (mc.gui == null) return;
            sendingPadding = true;
            try {
                for (int i = 0; i < lines; i++) {
                    mc.gui.getChat().addClientSystemMessage(Component.empty());
                }
            } finally {
                sendingPadding = false;
            }
        });
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
