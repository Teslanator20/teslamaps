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
        if (all.size() < 2) return;

        GuiMessage newest = all.get(0);
        String newBase = baseOf(newest);
        if (shouldNotStack(newBase)) return;

        int windowTicks = Math.max(1, c.chatStackWindowMinutes) * 60 * 20;

        int matchIdx = -1;
        for (int i = 1; i < all.size(); i++) {
            GuiMessage prev = all.get(i);
            if (newest.addedTime() - prev.addedTime() > windowTicks) break; // past the window -> stop
            if (baseOf(prev).equals(newBase)) { matchIdx = i; break; }
        }
        if (matchIdx < 0) return;

        int count = parseCount(plainOf(all.get(matchIdx))) + 1;

        Style counterStyle = Style.EMPTY.withColor(0xAAAAAA)
                .withStrikethrough(false).withUnderlined(false).withBold(false)
                .withItalic(false).withObfuscated(false);
        MutableComponent stacked = newest.content().copy()
                .append(Component.literal(" (" + count + "x)").withStyle(counterStyle));

        GuiMessage replacement = new GuiMessage(newest.addedTime(), stacked,
                newest.signature(), newest.source(), newest.tag());

        all.set(0, replacement);
        all.remove(matchIdx); // drop the older occurrence; the stack moves to the bottom
        acc.teslamaps$refreshTrimmedMessages();
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
