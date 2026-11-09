package com.teslamaps.features;

// Ported from Devonian's features/dungeons/f7/CompactMelodyMessages.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Party melody progress: only the newest line per player stays, or all get muted. */
public final class MelodyCompact {

    private static final Pattern FRAC = Pattern.compile("\\b([0-3])/[34]\\b");
    private static final Pattern PERC = Pattern.compile("\\b([27]5|[50]?0|33|6[67])%(?=[\\s\\W]|$)");
    private static final Set<String> KNOWN = new HashSet<>(List.of(
            "melody", "Melody Terminal start", "Melody Terminal Start",
            "Melody terminal is at 25%", "Melody terminal is at 50%", "Melody terminal is at 75%",
            "Melody ♪ Terminal [1/4]!", "Melody ♪ Terminal [2/4]!", "Melody ♪ Terminal [3/4]!",
            "Melody 25%", "Melody 50%", "Melody 75%",
            "Melody terminal is at 33%", "Melody terminal is at 67%",
            "Melody ♪ Terminal [1/3]!", "Melody ♪ Terminal [2/3]!", "Melody 33%", "Melody 67%"));
    private static final Map<String, String> lastMessage = new HashMap<>();
    private static final Map<String, String> prevLine = new HashMap<>();

    private MelodyCompact() {}

    /** From the chat filter; true = drop the line. */
    static boolean handle(String stripped) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatFilterEnabled || !c.melodyCompact) return false;
        PlayerChat.Msg pm = PlayerChat.current();
        if (pm == null || pm.channel() != PlayerChat.Channel.PARTY) return false;
        if (!DungeonManager.isInDungeon()) {
            prevLine.clear();
            lastMessage.clear();
            return false;
        }
        String msg = pm.message().trim();
        String name = pm.author();
        if (!(KNOWN.contains(msg) || FRAC.matcher(msg).find() || PERC.matcher(msg).find())) {
            lastMessage.put(name, msg);
            return false;
        }
        // the line before a player's first progress line is their start message
        String before = lastMessage.remove(name);
        if (before != null && KNOWN.size() < 200
                && (before.toLowerCase(java.util.Locale.ROOT).contains("melody") || before.contains("♪"))) KNOWN.add(before);
        if (c.melodyCompactMute) return true;
        String old = prevLine.put(name, stripped);
        if (old != null) delete(old);
        return false;
    }

    private static void delete(String plain) {
        ChatComponentAccessor acc = (ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat();
        List<GuiMessage> all = acc.teslamaps$getAllMessages();
        for (int i = 0; i < Math.min(all.size(), 100); i++) {
            if (ChatTimestamps.cut(StringUtil.strip(all.get(i).content().getString())).equals(plain)) {
                all.remove(i);
                acc.teslamaps$refreshTrimmedMessages();
                return;
            }
        }
    }
}
