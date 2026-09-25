package com.teslamaps.features;

// Ported from Skyblocker's SkyblockXpMessages
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "+N SkyBlock XP (reason) (x/100)" from the action bar, kept as a chat line. */
public final class SkyblockXpChat {

    private static final Pattern XP = Pattern.compile("\\+(\\d+) SkyBlock XP \\(([^()]+)\\) \\((\\d+/\\d+)\\)");
    private static final long DEDUPE_MS = 10_000;
    private static final Map<String, Long> recent = new HashMap<>();

    private SkyblockXpChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SkyblockXpChat").section("Chat").when(() -> cfg().skyblockXpChat)
                .config("Chat", b -> b.toggle("SkyBlock XP In Chat", () -> cfg().skyblockXpChat, v -> cfg().skyblockXpChat = v)
                        .desc("Copies the short \"+N SkyBlock XP\" action bar message into chat so it stays readable"));
    }

    /** From InGameHudMixin. */
    public static void onActionBar(Component message) {
        if (!cfg().skyblockXpChat || !cfg().section("Chat")) return;
        String raw = message.getString();
        if (!raw.contains("SkyBlock XP")) return;
        if (!SkyblockUtils.isInSkyblock()) return;
        Matcher m = XP.matcher(StringUtil.strip(raw));
        if (!m.find()) return;
        String key = m.group();
        long now = System.currentTimeMillis();
        recent.values().removeIf(t -> now - t > DEDUPE_MS);
        if (recent.containsKey(key)) return;
        recent.put(key, now);
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.sendSystemMessage(Component.literal("§b[TeslaMaps] §b+" + m.group(1) + " SkyBlock XP §7("
                + m.group(2) + "§7)§b (" + m.group(3) + ")"));
    }
}
