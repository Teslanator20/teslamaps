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
        b.toggle("  Completion Time in Chat", () -> config.simonSaysTime, v -> config.simonSaysTime = v)
                .desc("Time from the start of P3 until Simon Says is done, with PB.");
        b.label("Phase Timers");
        b.toggle("Purple Pad Timer", () -> config.purplePadTimer, v -> config.purplePadTimer = v);
        b.toggle("Necron Drop Timer", () -> config.necronDropTimer, v -> config.necronDropTimer = v);
        b.toggle("Goldor Tick Timer", () -> config.goldorTickTimer, v -> config.goldorTickTimer = v);
        b.toggle("Storm Pad Timer", () -> config.stormPadTimer, v -> config.stormPadTimer = v);
        b.toggle("Storm Lightning Timer", () -> config.lightningTimer, v -> config.lightningTimer = v);
        b.toggle("  Lightning Title Countdown (synced)", () -> config.lightningTimerTitle, v -> config.lightningTimerTitle = v);
        b.toggle("Relic Timer (M7)", () -> config.relicTimer, v -> config.relicTimer = v);
        b.slider("  Relic Spawn Ticks", 20f, 80f, () -> (float) config.relicSpawnTicks, v -> config.relicSpawnTicks = Math.round(v), 0);
        b.toggle("King Relics (M7 P5)", () -> config.kingRelics, v -> config.kingRelics = v)
                .desc("Beacon at your relic's cauldron, place time per color with PB, relic spawn time.");
        b.toggle("  Cauldron Beacon", () -> config.kingRelicBeacon, v -> config.kingRelicBeacon = v);
        b.toggle("  Place Time + PB", () -> config.kingRelicPlaceTime, v -> config.kingRelicPlaceTime = v);
        b.toggle("  Relic Spawn Time", () -> config.kingRelicSpawnMsg, v -> config.kingRelicSpawnMsg = v);
        b.toggle("Maxor/Storm Start Timers", () -> config.bossStartTimers, v -> config.bossStartTimers = v);
        b.toggle("Maxor Energy Crystals", () -> config.maxorCrystals, v -> config.maxorCrystals = v)
                .desc("P1 energy crystal helpers.").tags("crystal");
        b.toggle("  Crystal Spawn Timer", () -> config.maxorCrystalSpawnTimer, v -> config.maxorCrystalSpawnTimer = v);
        b.toggle("  Crystal Place Time in Chat (PB)", () -> config.maxorCrystalPlaceTimer, v -> config.maxorCrystalPlaceTimer = v);
        b.toggle("  Unplaced Crystal Alert", () -> config.maxorCrystalAlert, v -> config.maxorCrystalAlert = v);
        b.toggle("Separate Tick Timer HUDs (/tmap gui)", () -> config.tickTimersSeparate, v -> config.tickTimersSeparate = v);
        b.label("Other");
        b.toggle("Leap Counter", () -> config.leapCounter, v -> config.leapCounter = v);
        b.toggle("  Leap Spots (EE2/EE3/Core/P5)", () -> config.leapCounterSpots, v -> config.leapCounterSpots = v);
        b.toggle("  All Leaped Alert", () -> config.leapCounterAlert, v -> config.leapCounterAlert = v);
        b.label("P3 Terminals");
        b.toggle("Terminal Titles", () -> config.terminalTitles, v -> config.terminalTitles = v);
        b.info("Replaces or hides the \"<name> activated a terminal! (n/7)\" titles");
        b.dropdown("  Title Mode", com.teslamaps.features.TerminalTitles.MODES,
                () -> com.teslamaps.features.TerminalTitles.MODES[Math.max(0, Math.min(2, config.terminalTitleMode))],
                v -> config.terminalTitleMode = Math.max(0, java.util.Arrays.asList(com.teslamaps.features.TerminalTitles.MODES).indexOf(v)));
        b.dropdown("  Title Format", com.teslamaps.features.TerminalTitles.FORMAT_NAMES,
                () -> com.teslamaps.features.TerminalTitles.FORMAT_NAMES[Math.max(0, Math.min(2, config.terminalTitleFormat))],
                v -> config.terminalTitleFormat = Math.max(0, java.util.Arrays.asList(com.teslamaps.features.TerminalTitles.FORMAT_NAMES).indexOf(v)));
        b.toggle("  Show As Subtitle", () -> config.terminalTitleSubtitle, v -> config.terminalTitleSubtitle = v);
        b.toggle("  Only Replace Own", () -> config.terminalTitleSelfOnly, v -> config.terminalTitleSelfOnly = v);
        b.toggle("  Phase Done Title", () -> config.terminalTitlePhaseDone, v -> config.terminalTitlePhaseDone = v);
        b.toggle("  For Terminals", () -> config.terminalTitleTerminal, v -> config.terminalTitleTerminal = v);
        b.toggle("  For Levers", () -> config.terminalTitleLever, v -> config.terminalTitleLever = v);
        b.toggle("  For Devices", () -> config.terminalTitleDevice, v -> config.terminalTitleDevice = v);
        b.toggle("Term Info HUD", () -> config.termInfoHud, v -> config.termInfoHud = v);
        b.info("Terms / Levers / Device / Gate of the current section (/tmap gui)");
        b.toggle("  Compact (n/7)", () -> config.termInfoCompact, v -> config.termInfoCompact = v);
        b.toggle("Terminal Breakdown", () -> config.terminalBreakdown, v -> config.terminalBreakdown = v);
        b.info("Chat summary per player when the core opens");
        b.label("P3 Devices");
        b.toggle("Arrows Device Solver", () -> config.arrowsDeviceSolver, v -> config.arrowsDeviceSolver = v)
                .desc("Sharp shooter device: marks hit blocks and the current target.").tags("sharp shooter", "4th device");
        b.color("  Hit Color", () -> config.arrowsDeviceHitColor, v -> config.arrowsDeviceHitColor = v);
        b.color("  Target Color", () -> config.arrowsDeviceTargetColor, v -> config.arrowsDeviceTargetColor = v);
        b.toggle("  Aim Points", () -> config.arrowsDeviceAim, v -> config.arrowsDeviceAim = v);
        b.toggle("  Device Complete Alert", () -> config.arrowsDeviceAlert, v -> config.arrowsDeviceAlert = v);
        b.toggle("Lights Device Levers", () -> config.lightsDeviceHighlight, v -> config.lightsDeviceHighlight = v)
                .desc("Marks every unpowered lever of the lights device.");
        b.color("  Lever Color", () -> config.lightsDeviceColor, v -> config.lightsDeviceColor = v);
        b.toggle("Gate Highlight", () -> config.p3GateHighlight, v -> config.p3GateHighlight = v)
                .desc("Box around your section's gate while it still stands.");
        b.espStyle("  Gate", () -> config.p3GateEsp);
        b.toggle("Spring Boots Ready Alert", () -> config.springBootsReady, v -> config.springBootsReady = v)
                .desc("Boss only: title + beep after 5 charge sounds while sneaking in Spring Boots.");
        b.toggle("F7 Titles (crystals, stuns, deaths)", () -> config.f7Titles, v -> config.f7Titles = v);
        b.toggle("  Compact Crystal Titles", () -> config.f7CrystalTitles, v -> config.f7CrystalTitles = v);
        b.toggle("  Boss Stun/Death/Enrage Titles", () -> config.f7BossTitles, v -> config.f7BossTitles = v);
        b.label("P3 Terminals");
        b.toggle("Inactive Terminal Waypoints", () -> config.terminalWaypoints, v -> config.terminalWaypoints = v);
        b.toggle("  Terminals", () -> config.terminalWaypointsTerms, v -> config.terminalWaypointsTerms = v);
        b.toggle("  Devices", () -> config.terminalWaypointsDevices, v -> config.terminalWaypointsDevices = v);
        b.toggle("  Levers", () -> config.terminalWaypointsLevers, v -> config.terminalWaypointsLevers = v);
        b.toggle("  Box", () -> config.terminalWaypointsBox, v -> config.terminalWaypointsBox = v);
        b.toggle("  Label", () -> config.terminalWaypointsText, v -> config.terminalWaypointsText = v);
        b.toggle("  Beacon", () -> config.terminalWaypointsBeacon, v -> config.terminalWaypointsBeacon = v);
        b.toggle("  Through Walls", () -> config.terminalWaypointsThroughWalls, v -> config.terminalWaypointsThroughWalls = v);
        b.toggle("  Hide Hypixel Nametags", () -> config.terminalWaypointsHideNames, v -> config.terminalWaypointsHideNames = v);
        b.color("  Color", () -> config.terminalWaypointsColor, v -> config.terminalWaypointsColor = v);
        b.label("Wither Dragons");
        b.toggle("Wither Dragons", () -> config.witherDragons, v -> config.witherDragons = v);
        b.toggle("Dimensional Slash Alert", () -> config.dimensionalSlashAlert, v -> config.dimensionalSlashAlert = v);
        b.label("Spawn Timer & Alerts");
        b.toggle("Dragon Spawn Timer", () -> config.witherDragonTimer, v -> config.witherDragonTimer = v);
        b.toggle("Dragon Spawn Boxes", () -> config.witherDragonBoxes, v -> config.witherDragonBoxes = v);
        b.slider("  Box Line Width", 1f, 20f,
                () -> config.witherDragonBoxWidth, v -> config.witherDragonBoxWidth = v, 1);
        b.toggle("  Last Breath Spots (beams)", () -> config.witherDragonLbSpots, v -> config.witherDragonLbSpots = v);
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
        b.toggle("  Big Center Countdown", () -> config.witherDragonCenterTimer, v -> config.witherDragonCenterTimer = v);
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
        b.slider("  Tracer Width", 1f, 5f,
                () -> config.witherDragonTracerWidth, v -> config.witherDragonTracerWidth = v, 1);
        b.label("Live Dragons");
        b.toggle("Dragon Boxes (live)", () -> config.dragonBoxes, v -> config.dragonBoxes = v);
        b.dropdown("  Box Mode", new String[]{"Full Box", "Part Hitboxes"},
                () -> config.dragonBoxMode == 1 ? "Part Hitboxes" : "Full Box",
                v -> config.dragonBoxMode = "Part Hitboxes".equals(v) ? 1 : 0);
        b.slider("  Hitbox Line Width", 1f, 10f,
                () -> config.dragonHitboxWidth, v -> config.dragonHitboxWidth = v, 1);
        b.toggle("  Custom Hitbox Color", () -> config.dragonHitboxCustomColor, v -> config.dragonHitboxCustomColor = v);
        b.color("    Hitbox Color", () -> config.dragonHitboxColor, v -> config.dragonHitboxColor = v);
        b.toggle("Dragon Glow (own color)", () -> config.dragonGlow, v -> config.dragonGlow = v).legit();
        b.toggle("Dragon Health %", () -> config.dragonHealth, v -> config.dragonHealth = v);
        b.toggle("  Color Name (colorblind)", () -> config.dragonHealthColorName, v -> config.dragonHealthColorName = v);
        b.toggle("  \"R\" While In Statue", () -> config.dragonHealthStatue, v -> config.dragonHealthStatue = v);
        b.toggle("Hide Dying Dragons", () -> config.hideDyingDragons, v -> config.hideDyingDragons = v);
        b.toggle("Dragon Arrow Hit Counter (M7)", () -> config.dragonHitCounter, v -> config.dragonHitCounter = v)
                .desc("Counts your arrow hits on the priority dragon and posts them in chat.");
        b.toggle("Recolor Dragons (model tint)", () -> config.dragonRecolor, v -> config.dragonRecolor = v);
        b.toggle("Hide Dragon Hurt Flash", () -> config.dragonHideHurt, v -> config.dragonHideHurt = v);
        b.toggle("Dragon Perch Warning", () -> config.dragonPerchWarning, v -> config.dragonPerchWarning = v);
        b.toggle("Hide Other Dragons' Boss Bars (needs priority)", () -> config.dragonHideBossbars, v -> config.dragonHideBossbars = v);
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
