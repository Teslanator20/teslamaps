package com.teslamaps.features;

// SkyHanni ScoreboardData: Hide Piggy / Color Month Names
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ScoreboardText {

    private static final String[] MONTHS = {
            "Early Spring", "Late Spring", "Spring",
            "Early Summer", "Late Summer", "Summer",
            "Early Autumn", "Late Autumn", "Autumn",
            "Early Winter", "Late Winter", "Winter"
    };
    private static final ChatFormatting[] COLORS = {
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.GOLD, ChatFormatting.GOLD,
            ChatFormatting.YELLOW, ChatFormatting.YELLOW, ChatFormatting.YELLOW,
            ChatFormatting.BLUE, ChatFormatting.BLUE, ChatFormatting.BLUE
    };

    private static final Map<String, MutableComponent> CACHE = new HashMap<>();

    private ScoreboardText() {}

    public static boolean active() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.scoreboardPiggyToPurse || c.scoreboardColorMonths;
    }

    public static void clear() {
        CACHE.clear();
    }

    public static MutableComponent apply(MutableComponent line) {
        if (!active()) return line;
        String raw = line.getString();
        MutableComponent hit = CACHE.get(raw);
        if (hit != null) return hit;
        MutableComponent out = rebuild(line, raw);
        if (CACHE.size() > 128) CACHE.clear();
        CACHE.put(raw, out);
        return out;
    }

    private static MutableComponent rebuild(MutableComponent line, String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String plain = StringUtil.strip(raw);
        String trimmed = plain.trim();
        if (c.scoreboardColorMonths) {
            for (int i = 0; i < MONTHS.length; i++) {
                if (trimmed.startsWith(MONTHS[i])) return Component.literal(plain).withStyle(COLORS[i]);
            }
        }
        if (c.scoreboardPiggyToPurse && trimmed.startsWith("Piggy: ")) {
            MutableComponent out = Component.empty();
            line.visit((style, text) -> {
                out.append(Component.literal(text.replace("Piggy", "Purse")).withStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            return out;
        }
        return line;
    }
}
