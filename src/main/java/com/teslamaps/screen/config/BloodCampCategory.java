package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class BloodCampCategory {

    private BloodCampCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.classFilter("Blood Camp: Only as Class", config.bloodCampClasses);
        b.toggle("Move Timer HUD", () -> config.bloodCampMoveTimer, v -> config.bloodCampMoveTimer = v);
        b.toggle("Move Message", () -> config.bloodCampMoveMessage, v -> config.bloodCampMoveMessage = v);
        b.toggle("Party Move Message", () -> config.bloodCampPartyMessage, v -> config.bloodCampPartyMessage = v);
        b.toggle("\"Kill Mobs\" Title", () -> config.bloodCampKillTitle, v -> config.bloodCampKillTitle = v);
        b.toggle("Watcher HP Bar", () -> config.bloodCampHpBar, v -> config.bloodCampHpBar = v);
        b.label("Self Alert (title + sound)");
        b.toggle("\"KILL BLOOD\" on Watcher move", () -> config.bloodCampMoveAlert, v -> config.bloodCampMoveAlert = v);
        b.soundDropdown("  Alert Sound",
                com.teslamaps.utils.SoundOptions.keys(), () -> config.bloodCampMoveSound, v -> config.bloodCampMoveSound = v);
        b.slider("  Alert Volume", 0f, 20f,
                () -> config.bloodCampMoveVolume, v -> config.bloodCampMoveVolume = v);
        b.label("Return to Blood Timer");
        b.toggle("Return Timer", () -> config.bloodReturnTimer, v -> config.bloodReturnTimer = v);
        b.slider("  Start Estimate (s)", 10f, 60f,
                () -> (float) config.bloodReturnEstimate, v -> config.bloodReturnEstimate = Math.round(v));
        b.label("Spawn Boxes (Assist)");
        b.toggle("Blood Camp Assist", () -> config.bloodCampAssist, v -> config.bloodCampAssist = v);
        b.toggle("  Spawn Countdown Text", () -> config.bloodAssistTime, v -> config.bloodAssistTime = v);
        b.slider("    Countdown Decimals", 0f, 3f,
                () -> (float) config.bloodAssistDecimals, v -> config.bloodAssistDecimals = Math.round(v), 0);
        b.toggle("  Line to Spawn", () -> config.bloodAssistLine, v -> config.bloodAssistLine = v);
        b.toggle("  Camp Mob Sound (cowbell)", () -> config.bloodCampSound, v -> config.bloodCampSound = v)
                .desc("Plays once per mob when it should be hittable. Spawn prediction is still untuned.");
        b.slider("    Sound Threshold (ticks)", 0f, 10f,
                () -> config.bloodCampSoundTicks, v -> config.bloodCampSoundTicks = v, 1);
        b.slider("  Box Size", 0.1f, 1.0f,
                () -> config.bloodAssistBoxSize, v -> config.bloodAssistBoxSize = v);
        b.color("  Spawn Box", () -> config.colorBloodSpawn, v -> config.colorBloodSpawn = v);
        b.color("  Position Box", () -> config.colorBloodPosition, v -> config.colorBloodPosition = v);
        b.color("  Final Box", () -> config.colorBloodFinal, v -> config.colorBloodFinal = v);
        b.label("Advanced (tune in-game)");
        b.toggle("Ping Offset", () -> config.bloodAssistPingOffset, v -> config.bloodAssistPingOffset = v);
        b.slider("Manual Offset (ms)", 0f, 300f,
                () -> config.bloodAssistManualOffset, v -> config.bloodAssistManualOffset = v);
        b.toggle("Interpolation", () -> config.bloodAssistInterpolation, v -> config.bloodAssistInterpolation = v);
        b.slider("Tick Offset", -100f, 100f,
                () -> (float) config.bloodAssistOffset, v -> config.bloodAssistOffset = Math.round(v));
        b.slider("Spawn Tick", 35f, 41f,
                () -> (float) config.bloodAssistTick, v -> config.bloodAssistTick = Math.round(v));
        b.label("Watcher");
        b.toggle("Watcher Kill Alert (blood done)", () -> config.watcherKillAlert, v -> config.watcherKillAlert = v);
        b.toggle("Watcher Speed Grade", () -> config.watcherSpeedGrade, v -> config.watcherSpeedGrade = v);
        b.classFilter("  Watcher Grade: Only as Class", config.watcherGradeClasses);
        b.toggle("  Send Grade to Party", () -> config.watcherSpeedAnnounce, v -> config.watcherSpeedAnnounce = v)
                .desc("Posts Fast/Normal/Slow and the time in party chat.");
        b.toggle("Watcher Stats HUD", () -> config.watcherHud, v -> config.watcherHud = v);
        b.slider("  Watcher HUD Scale", 0.5f, 2.0f,
                () -> config.watcherHudScale, v -> config.watcherHudScale = v, 1);
        b.toggle("  Show for All Classes (not only Mage)", () -> config.watcherHudAllClasses, v -> config.watcherHudAllClasses = v);
        b.dropdown("  Time Format", new String[]{"Real Time", "Server Ticks", "Both"},
                () -> switch (config.watcherTimeFormat) { case 1 -> "Server Ticks"; case 2 -> "Both"; default -> "Real Time"; },
                v -> config.watcherTimeFormat = switch (v) { case "Server Ticks" -> 1; case "Both" -> 2; default -> 0; });
        b.toggle("Watcher Splits in Chat (blood done)", () -> config.watcherChatSplits, v -> config.watcherChatSplits = v);
        b.toggle("Watcher Bossbar Kill Counter", () -> config.watcherBossbarKills, v -> config.watcherBossbarKills = v)
                .desc("Adds \"n/total\" blood mobs to the Watcher's boss bar.");
        b.toggle("Hide Watcher Bossbar Outside Blood", () -> config.watcherBossbarHide, v -> config.watcherBossbarHide = v);
    }
}
