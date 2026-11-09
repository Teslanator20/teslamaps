package com.teslamaps.slayer;

// Ported from SkyHanni's SlayerBossSpawnSoon.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerBossSoon {

    private static final Pattern PROGRESS = Pattern.compile("\\(?([\\d,.]+[kK]?)/([\\d,.]+[kK]?)\\)? (?:Combat XP|Kills)");

    private static double lastCompletion = -1;
    private static boolean warned;
    private static int ticks;

    private SlayerBossSoon() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlayerBossSoon").section("Combat").when(() -> cfg().slayerBossSoon)
                .tick(SlayerBossSoon::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Boss Spawn Soon Warning", () -> c.slayerBossSoon, v -> c.slayerBossSoon = v)
                            .desc("Title, beep and chat \"Slayer boss soon!\" once the quest progress passes the percentage below")
                            .tags("slayer", "boss", "soon", "warning");
                    b.slider("  Warn At (%)", 50, 99, () -> (float) c.slayerBossSoonPercent, v -> c.slayerBossSoonPercent = Math.round(v), 0);
                    b.toggle("  Repeat On Every Kill", () -> c.slayerBossSoonRepeat, v -> c.slayerBossSoonRepeat = v);
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        if (SlayerQuest.boss() == null || SlayerQuest.bossActive() || !SkyblockUtils.isInSkyblock()) {
            warned = false;
            lastCompletion = -1;
            return;
        }
        double completion = -1;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = ScoreboardUtils.cleanLine(raw);
            if (!line.contains("/")) continue;
            Matcher m = PROGRESS.matcher(line);
            if (!m.find()) continue;
            double total = num(m.group(2));
            if (total > 0) completion = num(m.group(1)) / total;
            break;
        }
        if (completion < 0) return;
        if (completion > cfg().slayerBossSoonPercent / 100.0) {
            if (!warned || (cfg().slayerBossSoonRepeat && completion != lastCompletion)) {
                warn();
                warned = true;
            }
        } else {
            warned = false;
        }
        lastCompletion = completion;
    }

    private static void warn() {
        Minecraft mc = Minecraft.getInstance();
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §eSlayer boss soon!"));
        mc.gui.hud.setTimes(0, 40, 5);
        mc.gui.hud.setTitle(Component.literal("§eSlayer boss soon!"));
    }

    private static double num(String s) {
        s = s.replace(",", "");
        double mul = 1;
        if (s.endsWith("k") || s.endsWith("K")) {
            mul = 1000;
            s = s.substring(0, s.length() - 1);
        }
        try {
            return Double.parseDouble(s) * mul;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
