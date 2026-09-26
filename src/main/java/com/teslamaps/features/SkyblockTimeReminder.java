package com.teslamaps.features;

// Ported from Bax's TimeReminder.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkyblockTimeReminder {

    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(am|pm)");
    private static final int[] AT = {18 * 60, 19 * 60, 4 * 60, 5 * 60};
    private static final String[] TITLE = {"§eNight in 1h", "§9Nighttime!", "§eDay in 1h", "§6Daytime!"};
    private static final boolean[] alerted = new boolean[AT.length];
    private static final int[] ALARM_AT = {20 * 60, 21 * 60};
    private static final String[] ALARM_NAME = {"Zombie Villager", "Ghast"};
    private static final boolean[] alarmed = new boolean[2];
    private static final String[] COLOR_NAMES = {"Red", "Gold", "Yellow", "Green", "Aqua", "Light Purple", "White"};
    private static final String[] COLOR_CODES = {"§c", "§6", "§e", "§a", "§b", "§d", "§f"};
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SkyblockTimeReminder").section("Timers")
                .when(() -> cfg().skyblockTimeReminder || cfg().bestiaryAlarms)
                .tick(SkyblockTimeReminder::tick)
                .config("Timers", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("SkyBlock Time");
                    b.toggle("Day / Night Reminder", () -> c.skyblockTimeReminder, v -> c.skyblockTimeReminder = v)
                            .desc("Title at 6pm (night in 1h), 7pm (night), 4am (day in 1h) and 5am (day), read from the scoreboard clock.")
                            .tags("night", "day", "time", "clock");
                    b.toggle("  Sound", () -> c.skyblockTimeReminderSound, v -> c.skyblockTimeReminderSound = v);
                    b.toggle("Bestiary Alarms", () -> c.bestiaryAlarms, v -> c.bestiaryAlarms = v)
                            .desc("Title + mob sound at 8pm (Zombie Villager) and 9pm (Ghast), the night-only bestiary mobs.")
                            .tags("bestiary", "zombie villager", "ghast", "alarm", "night");
                    b.dropdown("  Title Color", COLOR_NAMES, () -> c.bestiaryAlarmColor, v -> c.bestiaryAlarmColor = v);
                    b.slider("  Volume", 0f, 2f, () -> c.bestiaryAlarmVolume, v -> c.bestiaryAlarmVolume = v, 1);
                    b.buttonRow(new String[]{"Test Zombie Villager", "Test Ghast"},
                            new Runnable[]{() -> alarm(0), () -> alarm(1)});
                });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        int minutes = -1;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = TIME.matcher(ScoreboardUtils.cleanLine(line));
            if (!m.find()) continue;
            int hour = Integer.parseInt(m.group(1));
            boolean pm = m.group(3).equals("pm");
            int h24 = hour == 12 ? (pm ? 12 : 0) : (pm ? hour + 12 : hour);
            minutes = h24 * 60 + Integer.parseInt(m.group(2));
            break;
        }
        if (minutes < 0) return;
        for (int i = 0; i < ALARM_AT.length; i++) {
            if (minutes >= ALARM_AT[i] && minutes < ALARM_AT[i] + 10) {
                if (!alarmed[i]) {
                    alarmed[i] = true;
                    if (cfg().bestiaryAlarms) alarm(i);
                }
            } else {
                alarmed[i] = false;
            }
        }
        if (!cfg().skyblockTimeReminder) return;
        for (int i = 0; i < AT.length; i++) {
            if (minutes >= AT[i] && minutes < AT[i] + 10) {
                if (!alerted[i]) {
                    alerted[i] = true;
                    show(TITLE[i]);
                }
            } else {
                alerted[i] = false;
            }
        }
    }

    private static void alarm(int i) {
        Minecraft mc = Minecraft.getInstance();
        String code = "§c";
        for (int k = 0; k < COLOR_NAMES.length; k++) if (COLOR_NAMES[k].equals(cfg().bestiaryAlarmColor)) code = COLOR_CODES[k];
        mc.gui.setTimes(5, 40, 10);
        mc.gui.setTitle(Component.literal(code + ALARM_NAME[i]));
        mc.gui.setSubtitle(Component.literal(""));
        LoudSound.play(i == 0 ? SoundEvents.ZOMBIE_VILLAGER_AMBIENT : SoundEvents.GHAST_AMBIENT, cfg().bestiaryAlarmVolume, 1.0f);
    }

    private static void show(String title) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(5, 40, 10);
        mc.gui.setTitle(Component.literal(title));
        if (cfg().skyblockTimeReminderSound) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
    }
}
