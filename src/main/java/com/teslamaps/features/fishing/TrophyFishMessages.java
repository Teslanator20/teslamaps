package com.teslamaps.features.fishing;

// Ported from SkyHanni's TrophyFishMessages.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public final class TrophyFishMessages {

    private static Feature feature;

    private TrophyFishMessages() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("TrophyFishMessages").section("Fishing").when(() -> cfg().trophyFishMessages)
                .serverChat(TrophyFishMessages::onChat)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Trophy Fish Chat Messages", () -> c.trophyFishMessages, v -> c.trophyFishMessages = v)
                            .desc("Rewrites the TROPHY FISH! line: \"FIRST Bronze Blobfish\" or \"You caught your 12th Bronze Blobfish\". "
                                    + "Counts come from the Trophy Fish data (Odger menu / Import From API)")
                            .tags("trophy", "fish", "chat", "duplicate", "gold", "diamond");
                    b.toggle("  Show Total Of That Fish", () -> c.trophyFishMsgTotal, v -> c.trophyFishMsgTotal = v);
                    b.toggle("  Hover With All Tiers", () -> c.trophyFishMsgHover, v -> c.trophyFishMsgHover = v);
                    b.toggle("  Hide Duplicate Bronze", () -> c.trophyFishHideBronze, v -> c.trophyFishHideBronze = v);
                    b.toggle("  Hide Duplicate Silver", () -> c.trophyFishHideSilver, v -> c.trophyFishHideSilver = v);
                    b.toggle("  Gold Alert", () -> c.trophyFishGoldAlert, v -> c.trophyFishGoldAlert = v);
                    b.toggle("  Diamond Alert", () -> c.trophyFishDiamondAlert, v -> c.trophyFishDiamondAlert = v);
                    b.toggle("    Alert Sound", () -> c.trophyFishAlertSound, v -> c.trophyFishAlertSound = v);
                });
    }

    private static boolean current(String raw) {
        return TrophyFishData.lastRaw != null && TrophyFishData.lastRaw.equals(raw)
                && System.currentTimeMillis() - TrophyFishData.lastAt < 1_000;
    }

    private static void onChat(String raw) {
        if (!current(raw)) return;
        TeslaMapsConfig c = cfg();
        int tier = TrophyFishData.lastTier;
        if (tier == 2 && c.trophyFishGoldAlert || tier == 3 && c.trophyFishDiamondAlert) {
            Minecraft mc = Minecraft.getInstance();
            TrophyFishData.Fish f = TrophyFishData.byKey(TrophyFishData.lastKey);
            mc.gui.hud.setTimes(0, 50, 10);
            mc.gui.hud.setTitle(Component.literal((f == null ? "§f" : f.color()) + (f == null ? "" : f.display()) + " "
                    + TrophyFishData.TIER_COLORS[tier] + "§l" + TrophyFishData.TIERS[tier] + " §8" + TrophyFishData.lastAmount + "§c!"));
            mc.gui.hud.setSubtitle(Component.literal(""));
            if (c.trophyFishAlertSound && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f);
        }
    }

    /** From ChatFilter: drop a repeated bronze/silver line. */
    public static boolean hide(String stripped) {
        if (feature == null || !feature.active() || TrophyFishData.lastRaw == null) return false;
        if (System.currentTimeMillis() - TrophyFishData.lastAt > 1_000) return false;
        if (!StringUtil.stripTrim(TrophyFishData.lastRaw).equals(stripped.trim())) return false;
        int tier = TrophyFishData.lastTier;
        if (TrophyFishData.lastAmount <= 1) return false;
        return tier == 0 && cfg().trophyFishHideBronze || tier == 1 && cfg().trophyFishHideSilver;
    }

    /** From ChatMixin's rewrite chain. */
    public static Component rewrite(Component message) {
        if (feature == null || !feature.active()) return message;
        String raw = message.getString();
        if (!raw.contains("TROPHY ") || !current(raw)) return message;
        TrophyFishData.Fish f = TrophyFishData.byKey(TrophyFishData.lastKey);
        if (f == null) return message;
        int tier = TrophyFishData.lastTier;
        int amount = TrophyFishData.lastAmount;
        String tierText = TrophyFishData.TIER_COLORS[tier] + "§l" + TrophyFishData.TIERS[tier];
        String name = nameColor(raw, f) + f.display();
        String body = amount == 1
                ? "§c§lFIRST §r" + tierText + " §r" + name
                : "§bYou caught your " + String.format(Locale.ROOT, "%,d", amount) + ordinal(amount) + " " + tierText + " §r" + name + "§b.";
        MutableComponent out = Component.literal("§6§lTROPHY FISH! " + body);
        int[] n = TrophyFishData.counts(f.key());
        if (cfg().trophyFishMsgTotal) {
            int total = n[0] + n[1] + n[2] + n[3];
            out.append(Component.literal(" §7(" + String.format(Locale.ROOT, "%,d", total) + ordinal(total) + " total)"));
        }
        if (cfg().trophyFishMsgHover) {
            StringBuilder h = new StringBuilder(f.color()).append(f.display());
            for (int i = 0; i < 4; i++) {
                String t = TrophyFishData.TIERS[i];
                h.append("\n").append(TrophyFishData.TIER_COLORS[i]).append(t.charAt(0)).append(t.substring(1).toLowerCase(Locale.ROOT))
                        .append("§7: ").append(n[i] > 0 ? "§f" + String.format(Locale.ROOT, "%,d", n[i]) : "§c✖");
            }
            out.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(h.toString()))));
        }
        return out;
    }

    private static String nameColor(String raw, TrophyFishData.Fish f) {
        int i = raw.indexOf("caught a");
        if (i < 0) return f.color();
        for (int j = i; j < raw.length() - 1; j++) {
            if (raw.charAt(j) != '§') continue;
            ChatFormatting fmt = ChatFormatting.getByCode(raw.charAt(j + 1));
            if (fmt != null && net.minecraft.network.chat.TextColor.fromLegacyFormat(fmt) != null && fmt != ChatFormatting.WHITE) return "§" + raw.charAt(j + 1);
        }
        return f.color();
    }

    private static String ordinal(int n) {
        int m100 = n % 100;
        if (m100 >= 11 && m100 <= 13) return "th";
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
