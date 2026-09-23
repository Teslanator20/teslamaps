package com.teslamaps.slayer;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class SlayerAlerts {

    private static final int TITLE_TICKS = 25;

    public static void onChatMessage(String rawText) {
        String text = StringUtil.strip(rawText).trim();
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
    }

    /** Scoreboard switched to "Slay the boss!" (SlayerQuest). */
    static void onBossSpawn() {
        if (!TeslaMapsConfig.get().slayerAlerts) return;
        setTitle("§cBOSS SPAWNED");
    }

    private static void setTitle(String title) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, TITLE_TICKS, 5);
        mc.gui.setTitle(Component.literal(title));
    }
}
