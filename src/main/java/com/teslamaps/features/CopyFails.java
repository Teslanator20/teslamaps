package com.teslamaps.features;

// Concept from Skytils' autoCopyFailToClipboard
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CopyFails {

    private static final Pattern FAIL = Pattern.compile(
            "^☠ .+ and became a ghost\\.$|^PUZZLE FAIL! .+$|^\\[STATUE] Oruo the Omniscient: .+ chose the wrong answer!.*");
    private static final String[] THORN_MISS = {"chickens", "shot", "dodg", "thumbs", "aim"};

    private CopyFails() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CopyFails").section("Chat").when(() -> cfg().copyFails)
                .serverChat(CopyFails::onChat)
                .config("Chat", b -> {
                    b.label("Dungeon Fails");
                    b.toggle("Copy Fails To Clipboard", () -> cfg().copyFails, v -> cfg().copyFails = v)
                            .desc("Deaths, puzzle fails, wrong Oruo answers and missed Thorn shots go to the clipboard, with a click-to-copy line.");
                });
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String m = StringUtil.stripTrim(raw);
        if (m.isEmpty() || m.contains("disconnect")) return;
        if (!FAIL.matcher(m).matches() && !thornMiss(m)) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.keyboardHandler.setClipboard(m);
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Copied fail. ")
                    .append(Component.literal("§8[Click to copy]").withStyle(s -> s
                            .withClickEvent(new ClickEvent.CopyToClipboard(m))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7" + m))))));
        });
    }

    private static boolean thornMiss(String m) {
        if (!m.startsWith("[CROWD]")) return false;
        String low = m.toLowerCase(Locale.ROOT);
        boolean miss = false;
        for (String k : THORN_MISS) if (low.contains(k)) { miss = true; break; }
        if (!miss) return false;
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) if (m.contains(p.getName())) return true;
        return false;
    }
}
