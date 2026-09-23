package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class F7M7Category {

    private F7M7Category() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Simon Says");
        b.toggle("Simon Says Solver", () -> config.solveSimonSays, v -> config.solveSimonSays = v);
        b.toggle("  Block Wrong Clicks (sneak = override)", () -> config.simonSaysBlockWrongClicks, v -> config.simonSaysBlockWrongClicks = v);
        b.toggle("  Broke/Restart Alerts", () -> config.simonSaysAlerts, v -> config.simonSaysAlerts = v);
        b.toggle("    Alerts to Party Chat", () -> config.simonSaysAlertChat, v -> config.simonSaysAlertChat = v);
        b.toggle("  Simon Says Progress", () -> config.simonSaysProgress, v -> config.simonSaysProgress = v);
        b.label("Phase Timers");
        b.toggle("Purple Pad Timer", () -> config.purplePadTimer, v -> config.purplePadTimer = v);
        b.toggle("Necron Drop Timer", () -> config.necronDropTimer, v -> config.necronDropTimer = v);
        b.toggle("Goldor Tick Timer", () -> config.goldorTickTimer, v -> config.goldorTickTimer = v);
        b.toggle("Storm Pad Timer", () -> config.stormPadTimer, v -> config.stormPadTimer = v);
        b.toggle("Storm Lightning Timer", () -> config.lightningTimer, v -> config.lightningTimer = v);
        b.toggle("  Lightning Title Countdown (synced)", () -> config.lightningTimerTitle, v -> config.lightningTimerTitle = v);
        b.toggle("Relic Timer (M7)", () -> config.relicTimer, v -> config.relicTimer = v);
        b.toggle("Maxor/Storm Start Timers", () -> config.bossStartTimers, v -> config.bossStartTimers = v);
        b.toggle("Separate Tick Timer HUDs (/tmap gui)", () -> config.tickTimersSeparate, v -> config.tickTimersSeparate = v);
        b.label("Other");
        b.toggle("Leap Counter", () -> config.leapCounter, v -> config.leapCounter = v);
        b.toggle("  Leap Spots (EE2/EE3/Core/P5)", () -> config.leapCounterSpots, v -> config.leapCounterSpots = v);
        b.toggle("  All Leaped Alert", () -> config.leapCounterAlert, v -> config.leapCounterAlert = v);
        b.label("Wither Dragons");
        b.toggle("Wither Dragons", () -> config.witherDragons, v -> config.witherDragons = v);
        b.label("Spawn Timer & Alerts");
        b.toggle("Dragon Spawn Timer", () -> config.witherDragonTimer, v -> config.witherDragonTimer = v);
        b.toggle("Dragon Spawn Boxes", () -> config.witherDragonBoxes, v -> config.witherDragonBoxes = v);
        b.toggle("Dragon Spawn Title", () -> config.witherDragonTitle, v -> config.witherDragonTitle = v);
        b.toggle("  Show HIGH in Spawn Title", () -> config.witherDragonShowHigh, v -> config.witherDragonShowHigh = v);
        b.slider("  Spawn Title Duration (ticks)", 5f, 100f,
                () -> (float) config.witherDragonAlertTicks, v -> config.witherDragonAlertTicks = Math.round(v));
        b.toggle("  Mute Spawn Title Sound", () -> config.witherDragonAlertMute, v -> config.witherDragonAlertMute = v);
        b.dropdown("Timer Style",
                new String[]{"Milliseconds", "Seconds", "Ticks"},
                () -> switch (config.witherDragonTimerStyle) { case 1 -> "Seconds"; case 2 -> "Ticks"; default -> "Milliseconds"; },
                v -> config.witherDragonTimerStyle = switch (v) { case "Seconds" -> 1; case "Ticks" -> 2; default -> 0; });
        b.toggle("Timer Symbol", () -> config.witherDragonTimerSymbol, v -> config.witherDragonTimerSymbol = v);
        b.toggle("Dragon Timer HUD", () -> config.witherDragonHud, v -> config.witherDragonHud = v);
        b.toggle("Dragon Spawn Messages", () -> config.witherDragonMsg, v -> config.witherDragonMsg = v);
        b.toggle("Dragon Health (spawn phase)", () -> config.witherDragonHealth, v -> config.witherDragonHealth = v);
        b.toggle("Send Dragon Confirmation", () -> config.witherDragonConfirm, v -> config.witherDragonConfirm = v);
        b.toggle("Send Dragon Time Alive", () -> config.witherDragonSendTime, v -> config.witherDragonSendTime = v);
        b.toggle("Send Ice Sprayed", () -> config.witherDragonSendSpray, v -> config.witherDragonSendSpray = v);
        b.label("Priority & Splits");
        b.toggle("Dragon Priority (split)", () -> config.witherDragonPriority, v -> config.witherDragonPriority = v);
        b.slider("  Normal Power (split)", 0f, 32f,
                () -> (float) config.witherDragonNormalPower, v -> config.witherDragonNormalPower = Math.round(v));
        b.slider("  Easy Power (purple split)", 0f, 32f,
                () -> (float) config.witherDragonEasyPower, v -> config.witherDragonEasyPower = Math.round(v));
        b.toggle("  Healer Solos Purple (off=Tank)", () -> config.witherDragonSoloDebuff == 1, v -> config.witherDragonSoloDebuff = v ? 1 : 0);
        b.toggle("  Solo Debuff on All Splits", () -> config.witherDragonSoloDebuffAll, v -> config.witherDragonSoloDebuffAll = v);
        b.toggle("  Paul Buff (×1.25 power)", () -> config.witherDragonPaulBuff, v -> config.witherDragonPaulBuff = v);
        b.toggle("  Auto Paul Buff (Election API)", () -> config.witherDragonPaulAuto, v -> config.witherDragonPaulAuto = v);
        b.toggle("  Custom Priority Strings", () -> config.witherDragonCustomPrio, v -> config.witherDragonCustomPrio = v);
        b.button("    Edit Priority Strings", () -> Minecraft.getInstance().setScreen(new DragonPrioScreen(Minecraft.getInstance().screen)));
        b.toggle("Priority Tracer", () -> config.witherDragonPriorityTracer, v -> config.witherDragonPriorityTracer = v);
        b.label("Live Dragons");
        b.toggle("Dragon Boxes (live)", () -> config.dragonBoxes, v -> config.dragonBoxes = v);
        b.toggle("Dragon Health %", () -> config.dragonHealth, v -> config.dragonHealth = v);
        b.toggle("Hide Dying Dragons", () -> config.hideDyingDragons, v -> config.hideDyingDragons = v);
        b.label("Wither Highlight");
        b.toggle("Wither Highlight", () -> config.witherHighlight, v -> config.witherHighlight = v);
        b.toggle("  Wither Highlight Box", () -> config.witherHighlightBox, v -> config.witherHighlightBox = v);
        b.color("  Wither Highlight Color", () -> config.witherHighlightColor, v -> config.witherHighlightColor = v);
        b.toggle("  Glow Instead of Box", () -> config.witherHighlightGlow, v -> config.witherHighlightGlow = v).legit();
        b.toggle("  Color per Boss (bossbar)", () -> config.witherHighlightPerBoss, v -> config.witherHighlightPerBoss = v);
        b.color("    Maxor", () -> config.witherColorMaxor, v -> config.witherColorMaxor = v);
        b.color("    Storm", () -> config.witherColorStorm, v -> config.witherColorStorm = v);
        b.color("    Goldor", () -> config.witherColorGoldor, v -> config.witherColorGoldor = v);
        b.color("    Necron", () -> config.witherColorNecron, v -> config.witherColorNecron = v);
    }
}
