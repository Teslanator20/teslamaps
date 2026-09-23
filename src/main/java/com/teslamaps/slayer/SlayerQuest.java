package com.teslamaps.slayer;

// Ported from Skyblocker's SlayerManager.checkSlayerQuest and Skysoft's SlayerQuestState
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The own slayer quest as the scoreboard and the quest chat lines describe it. */
public final class SlayerQuest {

    private static final Pattern QUEST = Pattern.compile(
            "\\b(Revenant Horror|Atoned Horror|Tarantula Broodfather|Conjoined Brood|Sven Packmaster"
                    + "|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend|Bloodfiend)(?:\\s+(V|IV|III|II|I))?\\b");

    private static String boss;
    private static int tier;
    private static boolean bossActive;
    private static String fightBoss;
    private static int fightTier;

    private static long questStartMs;
    private static long spawnMs;
    private static long spawnTick = -1;
    private static boolean died;
    private static int ticks;

    private SlayerQuest() {}

    public static void register() {
        Feature.of("SlayerQuest").section("Combat")
                .tick(SlayerQuest::tick)
                .serverChat(SlayerQuest::onChat);
    }

    /** Boss name from the scoreboard, e.g. "Revenant Horror", or null without a quest. */
    /** Boss of the current fight, the scoreboard one otherwise. */
    public static String boss() { return fightBoss != null ? fightBoss : boss; }
    public static int tier() { return fightBoss != null ? fightTier : tier; }
    public static boolean bossActive() { return bossActive; }
    public static long questStartMs() { return questStartMs; }
    /** When "Slay the boss!" showed up, 0 if not this quest. */
    public static long spawnMs() { return spawnMs; }
    public static boolean died() { return died; }

    /** Server ticks since the boss spawned, -1 if unknown. */
    public static long fightTicks() {
        return spawnTick < 0 ? -1 : ServerTickTimer.now() - spawnTick;
    }

    /** "REVENANT_4" style key, or null without a quest. */
    public static String key() {
        String boss = boss();
        if (boss == null) return null;
        String type = switch (boss) {
            case "Revenant Horror", "Atoned Horror" -> "REVENANT";
            case "Tarantula Broodfather", "Conjoined Brood" -> "TARANTULA";
            case "Sven Packmaster" -> "SVEN";
            case "Voidgloom Seraph" -> "VOIDGLOOM";
            case "Inferno Demonlord" -> "DEMONLORD";
            default -> "VAMPIRE";
        };
        return type + "_" + tier();
    }

    /** Called once the kill is handled. */
    static void clearFight() {
        fightBoss = null;
        fightTier = 0;
        spawnMs = 0;
        spawnTick = -1;
        died = false;
    }

    static void tick() {
        if (++ticks < 2) return;
        ticks = 0;

        String b = null;
        int t = 0;
        boolean slay = false;
        if (com.teslamaps.utils.SkyblockUtils.isInSkyblock()) for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = ScoreboardUtils.cleanLine(raw);
            if (line.contains("Slay the boss")) {
                slay = true;
                continue;
            }
            if (b != null) continue;
            Matcher m = QUEST.matcher(line);
            if (m.find()) {
                b = m.group(1).equals("Bloodfiend") ? "Riftstalker Bloodfiend" : m.group(1);
                t = m.group(2) != null ? roman(m.group(2))
                        : (b.equals("Atoned Horror") || b.equals("Conjoined Brood")) ? 5 : 1;
            }
        }

        boolean active = slay;
        if (active && !bossActive) {
            spawnMs = System.currentTimeMillis();
            spawnTick = ServerTickTimer.now();
            fightBoss = b;
            fightTier = t;
            SlayerAlerts.onBossSpawn();
        }
        boss = b;
        tier = t;
        bossActive = active;
    }

    static void onChat(String raw) {
        String text = StringUtil.strip(raw).trim();
        switch (text) {
            case "SLAYER QUEST STARTED!" -> {
                questStartMs = System.currentTimeMillis();
                clearFight();
            }
            case "NICE! SLAYER BOSS SLAIN!" -> SlayerHUD.onSlayerComplete();
            case "Your Slayer Quest has been cancelled!" -> {
                questStartMs = 0;
                clearFight();
            }
            default -> {
                if (text.startsWith("☠ You ") && bossActive) died = true;
            }
        }
    }

    static void onQuestEnd() {
        questStartMs = 0;
        clearFight();
    }

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            default -> 1;
        };
    }
}
