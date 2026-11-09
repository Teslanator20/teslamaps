package com.teslamaps.features;

// SkyHanni ScoreboardData: Hide Piggy
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ScoreboardText {

    private static final Map<String, MutableComponent> CACHE = new HashMap<>();

    private ScoreboardText() {}

    public static boolean active() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.scoreboardPiggyToPurse;
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
