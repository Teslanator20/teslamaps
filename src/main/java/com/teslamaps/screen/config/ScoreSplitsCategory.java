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
        b.toggle("  270 Score Title", () -> config.score270Title, v -> config.score270Title = v);
        b.toggle("  300 Score Title", () -> config.score300Title, v -> config.score300Title = v);
        b.button("  Edit Score Texts", () -> Minecraft.getInstance().setScreen(new TextFieldsScreen(
                Minecraft.getInstance().screen, "Score Texts", "& = color, {time} = run time, empty message = default",
                java.util.List.of(
                        new TextFieldsScreen.Field("270 Title", () -> config.score270TitleText, v -> config.score270TitleText = v),
                        new TextFieldsScreen.Field("270 Message", () -> config.score270Text, v -> config.score270Text = v),
                        new TextFieldsScreen.Field("300 Title", () -> config.score300TitleText, v -> config.score300TitleText = v),
                        new TextFieldsScreen.Field("300 Message", () -> config.score300Text, v -> config.score300Text = v)))))
                .desc("Title text and the chat/party message for 270 and 300 score.");
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
        b.toggle("  Custom Splits (per floor)", () -> config.customSplits, v -> config.customSplits = v)
                .desc("Own split points per floor, triggered by a chat message. They replace that floor's boss splits.");
        b.button("    Edit Custom Splits", () -> Minecraft.getInstance().setScreen(
                new CustomSplitsScreen(Minecraft.getInstance().screen)));
        b.toggle("  Show Tick Time", () -> config.splitsTickTime, v -> config.splitsTickTime = v)
                .desc("Server-tick time behind every split, lag independent");
        b.toggle("  Boss Entry Split", () -> config.splitsBossEntry, v -> config.splitsBossEntry = v)
                .desc("Extra row: run start until the boss, with PB");
        b.toggle("  Current Split HUD", () -> config.splitsCurrentHud, v -> config.splitsCurrentHud = v)
                .desc("Only the running split in ticks, move it in /tmap gui");
        b.toggle("  Terminal Splits (P3 Sections)", () -> config.terminalSplits, v -> config.terminalSplits = v)
                .desc("Adds section and P3 time to every terminal/device line, summary when the core opens.");
        b.toggle("    Use Server Ticks", () -> config.terminalSplitsServerTicks, v -> config.terminalSplitsServerTicks = v);
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
        b.toggle("Compact End-of-Run Summary", () -> config.extraStatsCompact, v -> config.extraStatsCompact = v)
                .desc("Hides Hypixel's end block and prints ~6 centered lines, details on hover. Best with Auto Extra Stats");
        b.dropdown("  Team Stats", new String[]{"Off", "Personal", "Team", "Both"},
                () -> config.extraStatsTeam, v -> config.extraStatsTeam = v);
        b.toggle("  Show Teammates", () -> config.extraStatsTeammates, v -> config.extraStatsTeammates = v);
        b.label("Dungeon Info");
        b.toggle("Death Counter", () -> config.deathCounter, v -> config.deathCounter = v)
                .desc("Deaths this run as a HUD (yellow, red, dark red)");
        b.toggle("  \"N. DEATH!\" Chat Line", () -> config.deathCounterChat, v -> config.deathCounterChat = v);
        b.toggle("Track Room Time Per Player", () -> config.visitedTracking, v -> config.visitedTracking = v)
                .desc("/visited <room> lists how long each player stood in that room this run");
    }
}
