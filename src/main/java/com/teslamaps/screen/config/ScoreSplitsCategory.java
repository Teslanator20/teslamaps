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
        b.buttonRow(new String[]{"Mimic Killed", "Prince Killed"}, new Runnable[]{
                com.teslamaps.dungeon.MimicDetector::markKilledManually,
                com.teslamaps.dungeon.DungeonScore::markPrinceKilledManually});
        b.info("Sets the bonus by hand and sends the message if it was missed");
        b.toggle("Crypt Reminder (<5 at boss)", () -> config.cryptReminder, v -> config.cryptReminder = v);
        b.soundDropdown("  Crypt Reminder Sound",
                com.teslamaps.utils.SoundOptions.keys(), () -> config.cryptReminderSound, v -> config.cryptReminderSound = v);
        b.classFilter("  Crypt Reminder: Only as Class", config.cryptReminderClasses);
        b.dropdown("  Crypt Reminder Trigger", new String[]{com.teslamaps.dungeon.DungeonScore.CRYPT_TRIGGER_BLOOD,
                com.teslamaps.dungeon.DungeonScore.CRYPT_TRIGGER_RUN_START},
                () -> config.cryptReminderTrigger, v -> config.cryptReminderTrigger = v);
        b.slider("  Delay After Run Start (min)", 1f, 5f,
                () -> config.cryptReminderDelay, v -> config.cryptReminderDelay = v, 1);
        b.toggle("  Crypt Reminder Title", () -> config.cryptReminderTitle, v -> config.cryptReminderTitle = v);
        b.channel("  Crypt Reminder Announce", () -> config.cryptReminderChannel, v -> config.cryptReminderChannel = v);
        b.label("Dungeon Splits");
        b.toggle("Enable Splits HUD", () -> config.splitsEnabled, v -> config.splitsEnabled = v);
        b.toggle("  Show PBs (per split)", () -> config.splitsShowPb, v -> config.splitsShowPb = v);
        b.toggle("  Show Delta To PB", () -> config.splitsShowDelta, v -> config.splitsShowDelta = v);
        b.toggle("  Send All Splits on End", () -> config.splitsSendAllOnEnd, v -> config.splitsSendAllOnEnd = v);
        b.toggle("Live Room Splits HUD", () -> config.roomSplits, v -> config.roomSplits = v);
        b.slider("  Room Splits Scale", 0.5f, 2.0f,
                () -> config.roomSplitsScale, v -> config.roomSplitsScale = v, 1);
        b.label("Alerts & Stats");
        b.toggle("Insta-Clear Alert", () -> config.instaClearAlert, v -> config.instaClearAlert = v);
        b.toggle("Room Cleared Alert", () -> config.roomClearedAlert, v -> config.roomClearedAlert = v)
                .desc("Title + pling when the room you stand in gets its checkmark");
        b.toggle("  Only Blood Rush Rooms", () -> config.roomClearedOnlyBloodRush, v -> config.roomClearedOnlyBloodRush = v)
                .desc("Only rooms with a wither or blood door");
        b.toggle("  No Blood Room Alert", () -> config.roomClearedNoBlood, v -> config.roomClearedNoBlood = v);
        b.toggle("Secrets Done Alert", () -> config.roomSecretsDoneAlert, v -> config.roomSecretsDoneAlert = v)
                .desc("Title when your room goes from white to green check");
        b.slider("Room Alert Time", 0.5f, 3.0f, () -> config.roomAlertTime, v -> config.roomAlertTime = v, 1);
        b.toggle("Trinity Alert", () -> config.rareRoomAlert, v -> config.rareRoomAlert = v);
        b.slider("  Alert Volume", 0.0f, 5.0f,
                () -> config.rareRoomVolume, v -> config.rareRoomVolume = v, 1);
        b.info("Title + chat when Trinity is scanned, once per run");
        b.toggle("Auto Extra Stats", () -> config.autoExtraStats, v -> config.autoExtraStats = v);
        b.info("Runs /showextrastats at the end of a run");
    }
}
