package com.teslamaps.slayer;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class SlayerAlerts {

    private static final int TITLE_TICKS = 25;

    private static int scoreboardTicks = 0;
    private static boolean wasSlayBoss = false;

    public static void onChatMessage(String rawText) {
        String text = rawText.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
        if (text.contains("YOU COCOONED YOUR SLAYER BOSS")) com.teslamaps.features.SkillTracker.onCocoon();
        if (!TeslaMapsConfig.get().slayerAlerts) return;

        if (text.contains("YOU COCOONED YOUR SLAYER BOSS")) {
            setTitle("§aCOCOON");
        } else if (text.startsWith("CAUGHT! You cocooned a ")
                && (text.contains("Atoned Champion") || text.contains("Atoned Revenant"))) {
            setTitle("§aCOCOONED MINI");
        } else if (text.contains("SLAYER MINI-BOSS") && text.contains("has spawned")) {
            setTitle("§cMINIBOSS");
        }
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().slayerAlerts) {
            wasSlayBoss = false;
            return;
        }
        if (++scoreboardTicks < 10) return;
        scoreboardTicks = 0;

        boolean slayBoss = false;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (ScoreboardUtils.cleanLine(line).contains("Slay the boss")) { slayBoss = true; break; }
        }
        if (slayBoss && !wasSlayBoss) setTitle("§cBOSS SPAWNED");
        wasSlayBoss = slayBoss;
    }

    private static void setTitle(String title) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, TITLE_TICKS, 5);
        mc.gui.setTitle(Component.literal(title));
    }
}
