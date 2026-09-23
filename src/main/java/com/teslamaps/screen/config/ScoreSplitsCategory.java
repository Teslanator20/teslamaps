package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class ScoreSplitsCategory {

    private ScoreSplitsCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Score Announce");
        b.toggle("Announce 300 (/pc)", () -> config.announce300, v -> config.announce300 = v);
        b.toggle("Announce 270 (/pc)", () -> config.announce270, v -> config.announce270 = v);
        b.toggle("Mimic Dead Message", () -> config.mimicDeadMessage, v -> config.mimicDeadMessage = v);
        b.toggle("Prince Dead Message", () -> config.princeDeadMessage, v -> config.princeDeadMessage = v);
        b.toggle("Crypt Reminder (<5 at boss)", () -> config.cryptReminder, v -> config.cryptReminder = v);
        b.soundDropdown("  Crypt Reminder Sound",
                com.teslamaps.utils.SoundOptions.keys(), () -> config.cryptReminderSound, v -> config.cryptReminderSound = v);
        b.classFilter("  Crypt Reminder: Only as Class", config.cryptReminderClasses);
        b.label("Dungeon Splits");
        b.toggle("Enable Splits HUD", () -> config.splitsEnabled, v -> config.splitsEnabled = v);
        b.toggle("  Show PBs (per split)", () -> config.splitsShowPb, v -> config.splitsShowPb = v);
        b.toggle("  Send All Splits on End", () -> config.splitsSendAllOnEnd, v -> config.splitsSendAllOnEnd = v);
        b.toggle("Live Room Splits HUD", () -> config.roomSplits, v -> config.roomSplits = v);
        b.slider("  Room Splits Scale", 0.5f, 2.0f,
                () -> config.roomSplitsScale, v -> config.roomSplitsScale = v, 1);
        b.label("Alerts & Stats");
        b.toggle("Insta-Clear Alert", () -> config.instaClearAlert, v -> config.instaClearAlert = v);
        b.toggle("Trinity Alert", () -> config.rareRoomAlert, v -> config.rareRoomAlert = v);
        b.slider("  Alert Volume", 0.0f, 5.0f,
                () -> config.rareRoomVolume, v -> config.rareRoomVolume = v, 1);
        b.info("Title + chat when Trinity is scanned, once per run");
        b.toggle("Auto Extra Stats", () -> config.autoExtraStats, v -> config.autoExtraStats = v);
        b.info("Runs /showextrastats at the end of a run");
    }
}
