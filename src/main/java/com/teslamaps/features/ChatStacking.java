package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatStacking {

    private static final Pattern COUNTER = Pattern.compile("^(.*?)\\s\\((\\d+)x\\)$", Pattern.DOTALL);

    public static void afterAdd(ChatComponent chat) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatStacking) return;

        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.teslamaps$getAllMessages();
        if (all.isEmpty()) return;

        GuiMessage newest = all.get(0);
        if (c.chatStackBlocks && isDivider(plainOf(newest))) {
            trackDivider(all);
            return;
        }
        if (all.size() < 2) {
            trackLine(newest);
            return;
        }

        String newBase = baseOf(newest);
        if (shouldNotStack(newBase)) {
            trackLine(newest);
            return;
        }
        boolean colors = c.chatStackCompareColors;
        boolean normalize = c.chatStackNormalizeNumbers;
        boolean player = normalize && com.teslamaps.utils.PlayerChat.parse(newest.content()) != null;
        boolean numbers = normalize && !player;
        String newKey = keyOf(newest, colors, numbers);

        int windowTicks = Math.max(1, c.chatStackWindowMinutes) * 60 * 20;

        int matchIdx = -1;
        for (int i = 1; i < all.size(); i++) {
            GuiMessage prev = all.get(i);
            if (newest.addedTime() - prev.addedTime() > windowTicks) break; // past the window -> stop
            if (player && i > 1) break; // player chat only with the line right above
            if (keyOf(prev, colors, numbers).equals(newKey)) { matchIdx = i; break; }
        }
        if (matchIdx < 0) {
            trackLine(newest);
            return;
        }

        int count = parseCount(plainOf(all.get(matchIdx))) + 1;

        Style counterStyle = Style.EMPTY.withColor(0xAAAAAA)
                .withStrikethrough(false).withUnderlined(false).withBold(false)
                .withItalic(false).withObfuscated(false);
        MutableComponent stacked = newest.content().copy()
                .append(Component.literal(" (" + count + "x)").withStyle(counterStyle));

        GuiMessage replacement = new GuiMessage(newest.addedTime(), stacked,
                newest.signature(), newest.source(), newest.tag());

        if (colors && !numbers) putCapped(colorCache, stacked, newKey);
        GuiMessage old = all.get(matchIdx);
        all.set(0, replacement);
        all.remove(matchIdx); // drop the older occurrence; the stack moves to the bottom
        if (c.chatStackBlocks) removeDividers(all, old, windowTicks);
        trackLine(replacement);
        acc.teslamaps$refreshTrimmedMessages();
    }

    // ---- divider blocks (ported from Skysoft's ChatCompactor.trackDividerBlock) ----
    // A block = divider, lines, divider within 5s. Its first line remembers the opening divider,
    // its last line the closing one; when that line repeats, the old dividers go with it.

    private static final long BLOCK_TIMEOUT_MS = 5_000;
    private static final Map<GuiMessage, GuiMessage> openOf = new IdentityHashMap<>();
    private static final Map<GuiMessage, GuiMessage> closeOf = new IdentityHashMap<>();
    private static GuiMessage openDivider, firstLine;
    private static int blockLines;
    private static long openedAt;

    private static boolean isDivider(String plain) {
        String t = plain.trim();
        if (t.length() <= 5) return false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch != '-' && ch != '=' && ch != '▬') return false;
        }
        return true;
    }

    private static void trackLine(GuiMessage line) {
        if (openDivider == null) return;
        if (System.currentTimeMillis() - openedAt >= BLOCK_TIMEOUT_MS) {
            openDivider = null;
            return;
        }
        if (++blockLines == 1) firstLine = line;
    }

    private static void trackDivider(List<GuiMessage> all) {
        GuiMessage divider = all.get(0);
        boolean open = openDivider != null && System.currentTimeMillis() - openedAt < BLOCK_TIMEOUT_MS;
        if (open && blockLines > 0 && all.size() > 1) {
            if (openOf.size() > 256) { openOf.clear(); closeOf.clear(); }
            openOf.put(firstLine, openDivider);
            closeOf.put(all.get(1), divider);
            openDivider = null;
            firstLine = null;
            return;
        }
        openDivider = divider;
        firstLine = null;
        blockLines = 0;
        openedAt = System.currentTimeMillis();
    }

    private static void removeDividers(List<GuiMessage> all, GuiMessage old, int windowTicks) {
        GuiMessage open = openOf.remove(old), close = closeOf.remove(old);
        if (open == null && close == null) return;
        int newest = all.get(0).addedTime();
        for (int i = 1; i < all.size() && (open != null || close != null); i++) {
            GuiMessage m = all.get(i);
            if (newest - m.addedTime() > windowTicks) break;
            if (m == open) { all.remove(i--); open = null; }
            else if (m == close) { all.remove(i--); close = null; }
        }
    }

    private static boolean shouldNotStack(String plain) {
        String t = plain.trim();
        if (t.isEmpty()) return true;                                          // empty / whitespace-only
        if (t.chars().noneMatch(Character::isLetterOrDigit)) return true;       // separators ("------", etc.)
        if (t.contains("ⓐ") || t.contains("ⓑ") || t.contains("ⓒ")) return true; // quiz answer options
        if (com.teslamaps.dungeon.puzzle.QuizSolver.isActive()) return true;    // quiz question lines
        return false;
    }

    // stripping + the counter regex used to run over the whole visible history on
    // every incoming message; the plain form of a message never changes
    // identity keyed: Component.hashCode walks the whole tree
    private static final Map<Component, String> plainCache = new IdentityHashMap<>();
    private static final Map<Component, String> baseCache = new IdentityHashMap<>();
    private static final Map<Component, String> colorCache = new IdentityHashMap<>();
    private static final Map<Component, String> numberCache = new IdentityHashMap<>();
    private static final Pattern NUMBER = Pattern.compile("(?<!§)\\d[\\d,.]*");

    private static String keyOf(GuiMessage msg, boolean colors, boolean numbers) {
        if (!numbers) return keyOf(msg, colors);
        Component c = msg.content();
        String cached = numberCache.get(c);
        if (cached != null && cached.charAt(0) == (colors ? 'c' : 'p')) return cached;
        String k = (colors ? 'c' : 'p') + NUMBER.matcher(keyOf(msg, colors)).replaceAll("#");
        putCapped(numberCache, c, k);
        return k;
    }

    /** Plain base, or with {@code colors} the whole formatted line (colours must match too). */
    private static String keyOf(GuiMessage msg, boolean colors) {
        if (!colors) return baseOf(msg);
        Component c = msg.content();
        String cached = colorCache.get(c);
        if (cached != null) return cached;
        String k = com.teslamaps.utils.ChatText.formatted(c);
        putCapped(colorCache, c, k);
        return k;
    }

    private static void putCapped(Map<Component, String> map, Component c, String v) {
        if (map.size() > 512) map.clear();
        map.put(c, v);
    }

    private static String plainOf(GuiMessage msg) {
        Component c = msg.content();
        String cached = plainCache.get(c);
        if (cached != null) return cached;
        String plain = strip(c.getString());
        if (plainCache.size() > 512) plainCache.clear();
        plainCache.put(c, plain);
        return plain;
    }

    private static String baseOf(GuiMessage msg) {
        Component c = msg.content();
        String cached = baseCache.get(c);
        if (cached != null) return cached;
        String b = base(plainOf(msg));
        if (baseCache.size() > 512) baseCache.clear();
        baseCache.put(c, b);
        return b;
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.strip(s);
    }

    private static String base(String plain) {
        Matcher m = COUNTER.matcher(plain);
        return m.matches() ? m.group(1) : plain;
    }

    private static int parseCount(String plain) {
        Matcher m = COUNTER.matcher(plain);
        if (m.matches()) {
            try { return Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }
}
