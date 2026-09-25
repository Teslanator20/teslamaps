package com.teslamaps.features;

// After OdinClient's EscrowFix
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;

import java.util.Set;

/** Reopens /ah or /bz after an escrow message closed the menu. One command per trigger, 5s cooldown. */
public final class EscrowFix {

    private static final Set<String> AH = Set.of(
            "There was an error with the auction house! (AUCTION_EXPIRED_OR_NOT_FOUND)",
            "There was an error with the auction house! (INVALID_BID)",
            "Claiming BIN auction...",
            "Visit the Auction House to collect your item!");
    private static final long COOLDOWN_MS = 5000;
    private static final int DELAY_TICKS = 8;

    private static String pending;
    private static int wait;
    private static long lastSent;

    private EscrowFix() {}

    public static void register() {
        Feature.of("EscrowFix").section("Inventory").legit()
                .when(() -> TeslaMapsConfig.get().escrowFix)
                .serverChat(EscrowFix::onChat)
                .tick(EscrowFix::tick);
    }

    private static void onChat(String text) {
        if (pending != null || LegitMode.blocksCheats()) return;
        String l = StringUtil.stripTrim(text);
        if (AH.contains(l)) pending = "ah";
        else if (l.startsWith("Escrow refunded ") && l.endsWith(" coins for Bazaar Instant Buy Submit!")) pending = "bz";
        else return;
        wait = DELAY_TICKS;
    }

    private static void tick() {
        if (pending == null || --wait > 0) return;
        String cmd = pending;
        pending = null;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        // only when the menu really closed, never twice in a row
        if (mc.screen != null || mc.getConnection() == null || now - lastSent < COOLDOWN_MS || LegitMode.blocksCheats()) return;
        lastSent = now;
        mc.getConnection().sendCommand(cmd);
    }
}
