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
        b.toggle("  Completion Time in Chat", () -> config.simonSaysTime, v -> config.simonSaysTime = v).fresh()
                .desc("Time from the start of P3 until Simon Says is done, with PB.");
        b.label("Phase Timers");
        b.toggle("Purple Pad Timer", () -> config.purplePadTimer, v -> config.purplePadTimer = v);
        b.toggle("Necron Drop Timer", () -> config.necronDropTimer, v -> config.necronDropTimer = v);
        b.toggle("Goldor Tick Timer", () -> config.goldorTickTimer, v -> config.goldorTickTimer = v);
        b.toggle("Storm Pad Timer", () -> config.stormPadTimer, v -> config.stormPadTimer = v);
        b.toggle("Storm Lightning Timer", () -> config.lightningTimer, v -> config.lightningTimer = v);
        b.toggle("  Lightning Title Countdown (synced)", () -> config.lightningTimerTitle, v -> config.lightningTimerTitle = v);
        b.toggle("Relic Timer (M7)", () -> config.relicTimer, v -> config.relicTimer = v);
        b.slider("  Relic Spawn Ticks", 20f, 80f, () -> (float) config.relicSpawnTicks, v -> config.relicSpawnTicks = Math.round(v), 0).fresh();
        b.toggle("King Relics (M7 P5)", () -> config.kingRelics, v -> config.kingRelics = v).fresh()
                .desc("Beacon at your relic's cauldron, place time per color with PB, relic spawn time.");
        b.toggle("  Cauldron Beacon", () -> config.kingRelicBeacon, v -> config.kingRelicBeacon = v).fresh();
        b.toggle("  Place Time + PB", () -> config.kingRelicPlaceTime, v -> config.kingRelicPlaceTime = v).fresh();
        b.toggle("  Relic Spawn Time", () -> config.kingRelicSpawnMsg, v -> config.kingRelicSpawnMsg = v).fresh();
        b.toggle("Maxor/Storm Start Timers", () -> config.bossStartTimers, v -> config.bossStartTimers = v);
        b.toggle("Maxor Energy Crystals", () -> config.maxorCrystals, v -> config.maxorCrystals = v).fresh()
                .desc("P1 energy crystal helpers.").tags("crystal");
        b.toggle("  Crystal Spawn Timer", () -> config.maxorCrystalSpawnTimer, v -> config.maxorCrystalSpawnTimer = v).fresh();
        b.toggle("  Crystal Place Time in Chat (PB)", () -> config.maxorCrystalPlaceTimer, v -> config.maxorCrystalPlaceTimer = v).fresh();
        b.toggle("  Unplaced Crystal Alert", () -> config.maxorCrystalAlert, v -> config.maxorCrystalAlert = v).fresh();
        b.label("Other");
        b.toggle("Storm Pillars as Glass (P2)", () -> config.fuckDiorite, v -> config.fuckDiorite = v).fresh()
                .desc("Client-side: the diorite of the 4 Storm pillars becomes glass.").tags("fuck diorite", "diorite");
        b.dropdown("  Glass Color Mode", com.teslamaps.features.FuckDiorite.MODES,
                () -> com.teslamaps.features.FuckDiorite.MODES[Math.max(0, Math.min(2, config.fuckDioriteMode))],
                v -> config.fuckDioriteMode = Math.max(0, java.util.Arrays.asList(com.teslamaps.features.FuckDiorite.MODES).indexOf(v))).fresh();
        b.dropdown("  Fixed Color", com.teslamaps.features.FuckDiorite.COLORS,
                () -> com.teslamaps.features.FuckDiorite.COLORS[Math.max(0, Math.min(16, config.fuckDioriteColor))],
                v -> config.fuckDioriteColor = Math.max(0, java.util.Arrays.asList(com.teslamaps.features.FuckDiorite.COLORS).indexOf(v))).fresh();
        b.label("P3 Terminals").fresh();
        b.toggle("Terminal Titles", () -> config.terminalTitles, v -> config.terminalTitles = v).fresh();
        b.info("Replaces or hides the \"<name> activated a terminal! (n/7)\" titles").fresh();
        b.dropdown("  Title Mode", com.teslamaps.features.TerminalTitles.MODES,
                () -> com.teslamaps.features.TerminalTitles.MODES[Math.max(0, Math.min(2, config.terminalTitleMode))],
                v -> config.terminalTitleMode = Math.max(0, java.util.Arrays.asList(com.teslamaps.features.TerminalTitles.MODES).indexOf(v))).fresh();
        b.dropdown("  Title Format", com.teslamaps.features.TerminalTitles.FORMAT_NAMES,
                () -> com.teslamaps.features.TerminalTitles.FORMAT_NAMES[Math.max(0, Math.min(2, config.terminalTitleFormat))],
                v -> config.terminalTitleFormat = Math.max(0, java.util.Arrays.asList(com.teslamaps.features.TerminalTitles.FORMAT_NAMES).indexOf(v))).fresh();
        b.toggle("  Show As Subtitle", () -> config.terminalTitleSubtitle, v -> config.terminalTitleSubtitle = v).fresh();
        b.toggle("  Only Replace Own", () -> config.terminalTitleSelfOnly, v -> config.terminalTitleSelfOnly = v).fresh();
        b.toggle("  Phase Done Title", () -> config.terminalTitlePhaseDone, v -> config.terminalTitlePhaseDone = v).fresh();
        b.toggle("  For Terminals", () -> config.terminalTitleTerminal, v -> config.terminalTitleTerminal = v).fresh();
        b.toggle("  For Levers", () -> config.terminalTitleLever, v -> config.terminalTitleLever = v).fresh();
        b.toggle("  For Devices", () -> config.terminalTitleDevice, v -> config.terminalTitleDevice = v).fresh();
        b.toggle("Term Info HUD", () -> config.termInfoHud, v -> config.termInfoHud = v).fresh();
        b.info("Terms / Levers / Device / Gate of the current section (/tmap gui)").fresh();
        b.toggle("  Compact (n/7)", () -> config.termInfoCompact, v -> config.termInfoCompact = v).fresh();
        b.toggle("Terminal Breakdown", () -> config.terminalBreakdown, v -> config.terminalBreakdown = v).fresh();
        b.info("Chat summary per player when the core opens").fresh();
        b.toggle("Inactive Terminal Waypoints", () -> config.terminalWaypoints, v -> config.terminalWaypoints = v).fresh();
        b.toggle("  Terminals", () -> config.terminalWaypointsTerms, v -> config.terminalWaypointsTerms = v).fresh();
        b.toggle("  Devices", () -> config.terminalWaypointsDevices, v -> config.terminalWaypointsDevices = v).fresh();
        b.toggle("  Levers", () -> config.terminalWaypointsLevers, v -> config.terminalWaypointsLevers = v).fresh();
        b.toggle("  Box", () -> config.terminalWaypointsBox, v -> config.terminalWaypointsBox = v).fresh();
        b.toggle("  Label", () -> config.terminalWaypointsText, v -> config.terminalWaypointsText = v).fresh();
        b.toggle("  Beacon", () -> config.terminalWaypointsBeacon, v -> config.terminalWaypointsBeacon = v).fresh();
        b.toggle("  Through Walls", () -> config.terminalWaypointsThroughWalls, v -> config.terminalWaypointsThroughWalls = v).fresh();
        b.toggle("  Hide Hypixel Nametags", () -> config.terminalWaypointsHideNames, v -> config.terminalWaypointsHideNames = v).fresh();
        b.color("  Color", () -> config.terminalWaypointsColor, v -> config.terminalWaypointsColor = v).fresh();
        b.label("P3 Devices").fresh();
        b.toggle("Arrows Device Solver", () -> config.arrowsDeviceSolver, v -> config.arrowsDeviceSolver = v).fresh()
                .desc("Sharp shooter device: marks hit blocks and the current target.").tags("sharp shooter", "4th device");
        b.color("  Hit Color", () -> config.arrowsDeviceHitColor, v -> config.arrowsDeviceHitColor = v).fresh();
        b.color("  Target Color", () -> config.arrowsDeviceTargetColor, v -> config.arrowsDeviceTargetColor = v).fresh();
        b.toggle("  Aim Points", () -> config.arrowsDeviceAim, v -> config.arrowsDeviceAim = v).fresh();
        b.toggle("  Device Complete Alert", () -> config.arrowsDeviceAlert, v -> config.arrowsDeviceAlert = v).fresh();
        b.toggle("Lights Device Levers", () -> config.lightsDeviceHighlight, v -> config.lightsDeviceHighlight = v).fresh()
                .desc("Marks every unpowered lever of the lights device.");
        b.color("  Lever Color", () -> config.lightsDeviceColor, v -> config.lightsDeviceColor = v).fresh();
        b.toggle("Spring Boots Ready Alert", () -> config.springBootsReady, v -> config.springBootsReady = v).fresh()
                .desc("Boss only: title + beep after 5 charge sounds while sneaking in Spring Boots.");
        b.toggle("F7 Titles (crystals, stuns, deaths)", () -> config.f7Titles, v -> config.f7Titles = v).fresh();
        b.toggle("  Compact Crystal Titles", () -> config.f7CrystalTitles, v -> config.f7CrystalTitles = v).fresh();
        b.toggle("  Boss Stun/Death/Enrage Titles", () -> config.f7BossTitles, v -> config.f7BossTitles = v).fresh();
        b.label("Wither Dragons");
        b.toggle("Wither Dragons", () -> config.witherDragons, v -> config.witherDragons = v);
        b.toggle("Dimensional Slash Alert", () -> config.dimensionalSlashAlert, v -> config.dimensionalSlashAlert = v).fresh();
        b.label("Spawn Timer & Alerts");
        b.toggle("Dragon Spawn Timer", () -> config.witherDragonTimer, v -> config.witherDragonTimer = v);
        b.toggle("Dragon Spawn Boxes", () -> config.witherDragonBoxes, v -> config.witherDragonBoxes = v);
        b.slider("  Box Line Width", 1f, 20f,
                () -> config.witherDragonBoxWidth, v -> config.witherDragonBoxWidth = v, 1).fresh();
        b.toggle("  Last Breath Spots (beams)", () -> config.witherDragonLbSpots, v -> config.witherDragonLbSpots = v).fresh();
        b.toggle("Dragon Spawn Title", () -> config.witherDragonTitle, v -> config.witherDragonTitle = v);
        b.toggle("  Show HIGH in Spawn Title", () -> config.witherDragonShowHigh, v -> config.witherDragonShowHigh = v);
        b.slider("  Spawn Title Duration (ticks)", 5f, 100f,
                () -> (float) config.witherDragonAlertTicks, v -> config.witherDragonAlertTicks = Math.round(v));
        b.toggle("  Mute Spawn Title Sound", () -> config.witherDragonAlertMute, v -> config.witherDragonAlertMute = v);
        b.dropdown("Timer Style",
                new String[]{"Milliseconds", "Seconds", "Ticks"},
                () -> switch (config.witherDragonTimerStyle) { case 1 -> "Seconds"; case 2 -> "Ticks"; default -> "Milliseconds"; },
                v -> config.witherDragonTimerStyle = switch (v) { case "Seconds" -> 1; case "Ticks" -> 2; default -> 0; });
        b.toggle("Dragon Timer HUD", () -> config.witherDragonHud, v -> config.witherDragonHud = v);
        b.toggle("  Big Center Countdown", () -> config.witherDragonCenterTimer, v -> config.witherDragonCenterTimer = v).fresh();
        b.toggle("Send Dragon Confirmation", () -> config.witherDragonConfirm, v -> config.witherDragonConfirm = v);
        b.toggle("Send Dragon Time Alive", () -> config.witherDragonSendTime, v -> config.witherDragonSendTime = v);
        b.toggle("Send Ice Sprayed", () -> config.witherDragonSendSpray, v -> config.witherDragonSendSpray = v);
        b.label("Priority & Splits");
        b.toggle("Dragon Priority", () -> config.witherDragonPriority, v -> config.witherDragonPriority = v)
                .desc("Dragons spawn one after another: the solo class takes the later dragon, everyone else the earlier one.");
        b.dropdown("  Solo Priority", com.teslamaps.dungeon.DragonPriority.SOLO_CLASSES,
                () -> config.witherDragonSoloClass, v -> config.witherDragonSoloClass = v).fresh();
        b.toggle("  First Dragon Only", () -> config.witherDragonFirstOnly, v -> config.witherDragonFirstOnly = v).fresh()
                .desc("After the first two dragons everyone goes to the earliest spawn.");
        b.toggle("Priority Tracer", () -> config.witherDragonPriorityTracer, v -> config.witherDragonPriorityTracer = v);
        b.slider("  Tracer Width", 1f, 5f,
                () -> config.witherDragonTracerWidth, v -> config.witherDragonTracerWidth = v, 1).fresh();
        b.toggle("Dragon Stack Aimer (M7)", () -> config.dragonStackAimer, v -> config.dragonStackAimer = v).fresh()
                .desc("Aim point + tracer for the arrow stack on your priority dragon, and ms until release (/tmap gui).");
        b.toggle("  Account for Ping", () -> config.dragonStackAimerPing, v -> config.dragonStackAimerPing = v).fresh();
        b.toggle("  Release Timer HUD", () -> config.dragonStackAimerHud, v -> config.dragonStackAimerHud = v).fresh();
        b.toggle("  Any Class (off = Archer/Berserk)", () -> config.dragonStackAimerAnyClass, v -> config.dragonStackAimerAnyClass = v).fresh();
        b.color("  Aim Color", () -> config.dragonStackAimerColor, v -> config.dragonStackAimerColor = v).fresh();
        b.color("  Tracer Color", () -> config.dragonStackAimerTracerColor, v -> config.dragonStackAimerTracerColor = v).fresh();
        b.label("Live Dragons");
        b.toggle("Dragon Boxes (live)", () -> config.dragonBoxes, v -> config.dragonBoxes = v);
        b.dropdown("  Box Mode", new String[]{"Full Box", "Part Hitboxes"},
                () -> config.dragonBoxMode == 1 ? "Part Hitboxes" : "Full Box",
                v -> config.dragonBoxMode = "Part Hitboxes".equals(v) ? 1 : 0).fresh();
        b.slider("  Hitbox Line Width", 1f, 10f,
                () -> config.dragonHitboxWidth, v -> config.dragonHitboxWidth = v, 1).fresh();
        b.toggle("  Custom Hitbox Color", () -> config.dragonHitboxCustomColor, v -> config.dragonHitboxCustomColor = v).fresh();
        b.color("    Hitbox Color", () -> config.dragonHitboxColor, v -> config.dragonHitboxColor = v).fresh();
        b.toggle("Dragon Glow (own color)", () -> config.dragonGlow, v -> config.dragonGlow = v).fresh().legit();
        b.toggle("Dragon Health %", () -> config.dragonHealth, v -> config.dragonHealth = v);
        b.toggle("  Color Name (colorblind)", () -> config.dragonHealthColorName, v -> config.dragonHealthColorName = v).fresh();
        b.toggle("  \"R\" While In Statue", () -> config.dragonHealthStatue, v -> config.dragonHealthStatue = v).fresh();
        b.toggle("Hide Dying Dragons", () -> config.hideDyingDragons, v -> config.hideDyingDragons = v);
        b.toggle("Dragon Arrow Hit Counter (M7)", () -> config.dragonHitCounter, v -> config.dragonHitCounter = v).fresh()
                .desc("Counts your arrow hits on the priority dragon and posts them in chat.");
        b.toggle("Recolor Dragons (model tint)", () -> config.dragonRecolor, v -> config.dragonRecolor = v).fresh();
        b.toggle("Hide Dragon Hurt Flash", () -> config.dragonHideHurt, v -> config.dragonHideHurt = v).fresh();
        b.toggle("Dragon Perch Warning", () -> config.dragonPerchWarning, v -> config.dragonPerchWarning = v).fresh();
        b.toggle("Hide Other Dragons' Boss Bars (needs priority)", () -> config.dragonHideBossbars, v -> config.dragonHideBossbars = v).fresh();
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
