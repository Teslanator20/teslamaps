package com.teslamaps.slayer;

// Sounds, miniboss name and per-alert toggles ported from Skysoft's SlayerBossAlerts / SlayerMinibossAlert
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlayerAlerts {

    private static final Pattern MINIBOSS = Pattern.compile("^SLAYER MINI-BOSS (?<name>.+?) has spawned!$");

    public static void onChatMessage(String rawText) {
        String text = StringUtil.strip(rawText).trim();
        if (text.contains("YOU COCOONED YOUR SLAYER BOSS")) com.teslamaps.features.SkillTracker.onCocoon();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slayerAlerts) return;

        if (text.contains("YOU COCOONED YOUR SLAYER BOSS")) {
            if (c.slayerAlertCocoonTitle) setTitle("§aCOCOON", null);
            if (c.slayerAlertCocoonSound) repeat(SoundEvents.HORSE_SADDLE.value(), 1.75f, 4, 9);
        } else if (text.startsWith("CAUGHT! You cocooned a ")
                && (text.contains("Atoned Champion") || text.contains("Atoned Revenant"))) {
            if (c.slayerAlertCocoonTitle) setTitle("§aCOCOONED MINI", null);
        } else if (text.contains("SLAYER MINI-BOSS") && text.contains("has spawned")) {
            Matcher m = MINIBOSS.matcher(text);
            if (c.slayerAlertMinibossTitle) {
                if (c.slayerAlertMinibossName && m.matches()) setTitle("§c§l" + m.group("name"), "§7Miniboss Spawned!");
                else setTitle("§cMINIBOSS", null);
            }
            if (c.slayerAlertMinibossSound) repeat(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 3, 9);
        }
    }

    public static void tick() {
    }

    /** Scoreboard switched to "Slay the boss!" (SlayerQuest), once per quest. */
    static void onBossSpawn() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slayerAlerts) return;
        if (c.slayerAlertSpawnTitle) setTitle("§cBOSS SPAWNED", null);
        if (c.slayerAlertSpawnSound) repeat(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 4, 5);
    }

    private static void repeat(SoundEvent sound, float volume, int plays, int gapTicks) {
        LoudSound.play(sound, volume, 1.0f);
        for (int i = 1; i < plays; i++) TickScheduler.schedule(i * gapTicks, () -> LoudSound.play(sound, volume, 1.0f));
    }

    private static void setTitle(String title, String subtitle) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, Math.max(5, c.slayerAlertTicks), 5);
        MutableComponent t;
        if (c.slayerAlertCustomColor) {
            t = Component.literal(StringUtil.strip(title)).withColor(rgb(c.slayerAlertColor));
            if (title.contains("§l")) t = t.withStyle(ChatFormatting.BOLD);
        } else {
            t = Component.literal(title);
        }
        mc.gui.hud.setTitle(t);
        if (subtitle != null) mc.gui.hud.setSubtitle(Component.literal(subtitle));
    }

    private static int rgb(String hex) {
        try {
            String h = hex == null ? "" : hex.replace("#", "");
            if (h.length() == 8) h = h.substring(2);
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFF5555;
        }
    }
}
