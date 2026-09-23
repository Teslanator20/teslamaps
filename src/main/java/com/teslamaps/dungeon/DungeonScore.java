package com.teslamaps.dungeon;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.TabListUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DungeonScore {
    private static final Pattern CLEARED_PATTERN = Pattern.compile("Cleared: (?<cleared>\\d+)%.*");
    private static final Pattern FLOOR_PATTERN = Pattern.compile(".*Cata.*ombs.*\\((?<floor>[EFM]\\d+)\\)");

    private static final Pattern SECRETS_PATTERN = Pattern.compile("Secrets Found: (?<secper>\\d+\\.?\\d*)%");
    private static final Pattern PUZZLES_PATTERN = Pattern.compile("[^:\\[]+: \\[(?<state>[✔✖✦])](?: ?\\(\\w*\\))?");
    private static final Pattern PUZZLE_COUNT_PATTERN = Pattern.compile("Puzzles: \\((?<count>\\d+)\\)");
    private static final Pattern CRYPTS_PATTERN = Pattern.compile("Crypts: (?<crypts>\\d+)");
    private static final Pattern COMPLETED_ROOMS_PATTERN = Pattern.compile("Completed Rooms: (?<rooms>\\d+)");

    private static final Pattern DEATHS_PATTERN = Pattern.compile(" \u2620 (?<whodied>\\S+) .*");
    // mod announces: Odin "Mimic Killed!", SkyHanni "Mimic dead!", ours "Mimic Dead!", Skytils marker
    public static final Pattern MIMIC_PATTERN =
            Pattern.compile("(?i).*\\bmimic (?:is )?(?:dead|died|killed|done)[.!]?\\s*$|.*\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$$");
    // matches other mods' party announces (Odin "Prince Killed!", Devonian "Prince dead!", ours "Prince Dead!")
    private static final Pattern PRINCE_PATTERN = Pattern.compile("(?i).*\\bprince (?:is )?(?:dead|died|killed)!\\s*$");
    private static final String PRINCE_KILL_MESSAGE = "A Prince falls."; // "+1 Bonus Score" tail varies, match the prefix

    private static final Pattern MIMIC_FLOORS_PATTERN = Pattern.compile("[FM][67]");

    private static FloorRequirement floorRequirement = FloorRequirement.NONE;
    private static String currentFloor = "";
    private static boolean isCurrentFloorEntrance = false;
    private static boolean floorHasMimics = false;
    private static boolean mimicKilled = false;
    private static boolean princeKilled = false;
    private static boolean princeDeadMessageSent = false;
    private static boolean cryptReminderSent = false;
    private static long cryptReminderAt = 0L;
    public static final String CRYPT_TRIGGER_BLOOD = "Blood Door +30s";
    public static final String CRYPT_TRIGGER_RUN_START = "Run Start";
    private static boolean dungeonStarted = false;
    private static boolean bloodRoomCompleted = false;
    private static boolean sent270 = false;
    private static boolean sent300 = false;
    private static long startingTime = 0L;
    private static int puzzleCount = 0;
    private static int deathCount = 0;
    private static int tabDeaths = -1;
    private static int score = 0;
    private static boolean firstDeathHasSpirit = true;

    private static int tabCrypts = 0;
    private static int tabCompletedRooms = 0;
    private static int tabPuzzlesDone = 0;
    private static double tabSecretsPct = 0;

    private static boolean floorDetectionPending = false;
    private static long floorDetectionTime = 0L;

    private static long lastScoreCalcTime = 0L; // recompute the score at most every 500ms (called per render frame)

    public static void reset() {
        floorRequirement = FloorRequirement.NONE;
        currentFloor = "";
        isCurrentFloorEntrance = false;
        floorHasMimics = false;
        mimicKilled = false;
        princeKilled = false;
        princeDeadMessageSent = false;
        cryptReminderSent = false;
        cryptReminderAt = 0L;
        dungeonStarted = false;
        bloodRoomCompleted = false;
        sent270 = false;
        sent300 = false;
        startingTime = 0L;
        puzzleCount = 0;
        deathCount = 0;
        tabDeaths = -1;
        score = 0;
        firstDeathHasSpirit = true;
        tabCrypts = 0;
        tabCompletedRooms = 0;
        tabPuzzlesDone = 0;
        tabSecretsPct = 0;
        floorDetectionPending = false;
        floorDetectionTime = 0L;
        lastScoreCalcTime = 0L;
    }

    public static void onDungeonStart() {
        reset();
        dungeonStarted = true;
        startingTime = System.currentTimeMillis();

        floorDetectionPending = true;
        floorDetectionTime = System.currentTimeMillis() + 3000;
    }

    private static int debugCounter = 0;

    public static int calculateScore() {
        if (!dungeonStarted) return 0;

        // throttle: this is called every render frame but the inputs change a few times/sec at most
        long nowMs = System.currentTimeMillis();
        if (lastScoreCalcTime != 0L && nowMs - lastScoreCalcTime < 500) return score;
        lastScoreCalcTime = nowMs;

        readTab();

        if (floorDetectionPending && System.currentTimeMillis() >= floorDetectionTime) {
            floorDetectionPending = false;
            setCurrentFloor();
            try {
                floorRequirement = FloorRequirement.valueOf(currentFloor);
            } catch (IllegalArgumentException e) {
                floorRequirement = FloorRequirement.F1;
            }
            floorHasMimics = MIMIC_FLOORS_PATTERN.matcher(currentFloor).matches();
            if (currentFloor.equals("E")) isCurrentFloorEntrance = true;
        }

        tabDeaths = TabListUtils.getDeaths();
        int timeScore = calculateTimeScore();
        int exploreScore = calculateExploreScore();
        int skillScore = calculateSkillScore();
        int bonusScore = calculateBonusScore();

        if (isCurrentFloorEntrance) {
            score = Math.round(timeScore * 0.7f) +
                    Math.round(exploreScore * 0.7f) +
                    Math.round(skillScore * 0.7f) +
                    Math.round(bonusScore * 0.7f);
        } else {
            score = timeScore + exploreScore + skillScore + bonusScore;
        }

        debugCounter++;

        if (!sent270 && !sent300 && score >= 270 && score < 300) {
            sent270 = true;
            sendScoreMessage(270);
        } else if (!sent300 && score >= 300) {
            sent300 = true;
            sendScoreMessage(300);
        }

        return score;
    }

    private static void sendScoreMessage(int milestone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long elapsed = (System.currentTimeMillis() - startingTime) / 1000;
        int minutes = (int) (elapsed / 60);
        int seconds = (int) (elapsed % 60);
        String timeStr = String.format("%02d:%02d", minutes, seconds);

        mc.player.sendSystemMessage(
            Component.literal("[")
                .append(Component.literal("TeslaMaps").withStyle(style -> style.withColor(0x55FF55)))
                .append(Component.literal("] "))
                .append(Component.literal(milestone + " Score reached @ " + timeStr).withStyle(style -> style.withColor(0xFFFFFF)))
        );

        TeslaMapsConfig config = TeslaMapsConfig.get();
        boolean announce = (milestone == 300 && config.announce300) || (milestone == 270 && config.announce270);
        if (announce && mc.getConnection() != null) {
            mc.getConnection().sendCommand("pc " + milestone + " Score reached @ " + timeStr);
        }
    }

    private static int calculateSkillScore() {
        int totalRooms = getTotalRooms();
        int completedRoomScore = Math.clamp((totalRooms != 0 ? (int) (80.0 * (getCompletedRooms() + getExtraCompletedRooms()) / totalRooms) : 0), 0, 80);
        return 20 + Math.clamp(completedRoomScore - getPuzzlePenalty() - getDeathScorePenalty(), 0, 80);
    }

    private static int calculateExploreScore() {
        int totalRooms = getTotalRooms();
        int completedRoomScore = Math.clamp(totalRooms != 0 ? (int) (60.0 * (getCompletedRooms() + getExtraCompletedRooms()) / totalRooms) : 0, 0, 60);
        int secretsScore = Math.clamp((int) (40 * Math.min(floorRequirement.percentage, getSecretsPercentage()) / floorRequirement.percentage), 0, 40);
        return completedRoomScore + secretsScore;
    }

    private static int calculateTimeScore() {
        int score = 100;
        int timeSpent = (int) (System.currentTimeMillis() - startingTime) / 1000;
        if (timeSpent < floorRequirement.timeLimit) return score;

        double timePastRequirement = ((double) (timeSpent - floorRequirement.timeLimit) / floorRequirement.timeLimit) * 100;
        if (timePastRequirement < 20) return score - (int) timePastRequirement / 2;
        if (timePastRequirement < 40) return score - (int) (10 + (timePastRequirement - 20) / 4);
        if (timePastRequirement < 50) return score - (int) (15 + (timePastRequirement - 40) / 5);
        if (timePastRequirement < 60) return score - (int) (17 + (timePastRequirement - 50) / 6);
        return Math.clamp(score - (int) (18 + (2.0 / 3.0) + (timePastRequirement - 60) / 7), 0, 100);
    }

    private static int calculateBonusScore() {
        int paulScore = paulBonus();
        int cryptsScore = Math.clamp(getCrypts(), 0, 5);
        int mimicScore = mimicKilled ? 2 : 0;
        if (getSecretsPercentage() >= 100 && floorHasMimics) mimicScore = 2;
        int princeScore = princeKilled ? 1 : 0;
        return paulScore + cryptsScore + mimicScore + princeScore;
    }

    private static int getTotalRooms() {
        double clearPct = getClearPercentage();
        if (clearPct <= 0) return 36;
        return (int) Math.round(getCompletedRooms() / clearPct);
    }

    private static int paulBonus() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.assumePaulMayor || (c.paulAutoDetect && com.teslamaps.api.ElectionApi.ezpz()) ? 10 : 0;
    }

    // scan every tab line: Hypixel moves the rows around, fixed indices break
    private static void readTab() {
        int crypts = 0, rooms = 0, done = 0, count = -1;
        double secrets = 0;
        for (String raw : com.teslamaps.utils.TabListUtils.getTabListLines()) {
            String line = com.teslamaps.utils.StringUtil.stripTrim(raw);
            if (line.length() < 8) continue;
            Matcher m;
            if ((m = CRYPTS_PATTERN.matcher(line)).matches()) crypts = Integer.parseInt(m.group("crypts"));
            else if ((m = SECRETS_PATTERN.matcher(line)).matches()) secrets = Double.parseDouble(m.group("secper"));
            else if ((m = COMPLETED_ROOMS_PATTERN.matcher(line)).matches()) rooms = Integer.parseInt(m.group("rooms"));
            else if ((m = PUZZLE_COUNT_PATTERN.matcher(line)).matches()) count = Integer.parseInt(m.group("count"));
            else if ((m = PUZZLES_PATTERN.matcher(line)).matches() && m.group("state").equals("✔")) done++;
        }
        tabCrypts = crypts;
        tabCompletedRooms = rooms;
        tabSecretsPct = secrets;
        tabPuzzlesDone = done;
        if (count >= 0) puzzleCount = count;
    }

    private static int getCompletedRooms() {
        return tabCompletedRooms;
    }

    private static int getExtraCompletedRooms() {
        if (!bloodRoomCompleted) {
            return isCurrentFloorEntrance ? 1 : 2;
        }
        if (!DungeonManager.isInBossArea() && !isCurrentFloorEntrance) {
            return 1;
        }
        return 0;
    }

    private static double getClearPercentage() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String clean = ScoreboardUtils.cleanLine(line);
            Matcher matcher = CLEARED_PATTERN.matcher(clean);
            if (matcher.matches()) {
                return Double.parseDouble(matcher.group("cleared")) / 100.0;
            }
        }
        return 0;
    }

    // Hypixel's "Team Deaths: n" when the tab shows it, else the chat count
    private static int getDeathScorePenalty() {
        int deaths = tabDeaths >= 0 ? tabDeaths : deathCount;
        if (deaths == 0) return 0;
        return deaths * 2 - (firstDeathHasSpirit ? 1 : 0);
    }

    private static int getPuzzlePenalty() {
        return Math.max(0, puzzleCount - tabPuzzlesDone) * 10;
    }

    private static double getSecretsPercentage() {
        return tabSecretsPct;
    }

    private static int getCrypts() {
        return tabCrypts;
    }

    private static void setCurrentFloor() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String clean = ScoreboardUtils.cleanLine(line);
            Matcher matcher = FLOOR_PATTERN.matcher(clean);
            if (matcher.matches()) {
                currentFloor = matcher.group("floor");
                return;
            }
        }
        currentFloor = "F1";
    }

    public static void onChatMessage(String message) {
        if (!dungeonStarted) return;

        if (message.equals("Starting in 1 second.")) {
            startingTime = System.currentTimeMillis();
            return;
        }

        if (message.length() > 1 && message.charAt(1) == '\u2620') {
            Matcher matcher = DEATHS_PATTERN.matcher(message);
            if (matcher.matches()) {
                deathCount++;
                if (deathCount == 1 && TeslaMapsConfig.get().spiritPetCheck) checkSpiritPet(matcher.group("whodied"));
            }
        }

        if (message.equals("[BOSS] The Watcher: You have proven yourself. You may pass.")) {
            new Thread(() -> {
                try {
                    Thread.sleep(5500);
                    bloodRoomCompleted = true;
                } catch (InterruptedException ignored) {}
            }).start();

            if (TeslaMapsConfig.get().watcherKillAlert) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.gui.setTimes(0, 30, 5);
                    mc.gui.setTitle(Component.literal("§c§lBLOOD DONE"));
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
                }
            }
        }

        String clean = com.teslamaps.utils.StringUtil.stripTrim(message);

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cryptReminderSent && cfg.cryptReminder) {
            boolean runStart = CRYPT_TRIGGER_RUN_START.equals(cfg.cryptReminderTrigger);
            if (!runStart && clean.equals("The BLOOD DOOR has been opened!")) {
                cryptReminderSent = true;
                cryptReminderAt = System.currentTimeMillis() + 30_000L;
            } else if (runStart && clean.equals("[NPC] Mort: Good luck.")) {
                cryptReminderSent = true;
                cryptReminderAt = System.currentTimeMillis() + Math.round(cfg.cryptReminderDelay * 60_000f);
            }
        }

        if (floorHasMimics && MIMIC_PATTERN.matcher(clean).matches()) {
            mimicKilled = true;
        }

        if (PRINCE_PATTERN.matcher(clean).matches() || clean.startsWith(PRINCE_KILL_MESSAGE)) {
            princeKilled = true;
            if (!princeDeadMessageSent && clean.startsWith(PRINCE_KILL_MESSAGE)
                    && TeslaMapsConfig.get().princeDeadMessage) {
                princeDeadMessageSent = true;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.getConnection() != null) {
                    mc.getConnection().sendCommand("pc Prince Dead!");
                }
            }
        }
    }

    // Skyblocker: only a legendary Spirit pet on the first death makes it -1 instead of -2
    private static void checkSpiritPet(String name) {
        Minecraft mc = Minecraft.getInstance();
        String who = name.equals("You") ? mc.getUser().getName() : name;
        long run = startingTime;
        com.teslamaps.api.HypixelApi.nameToUuid(who)
                .thenCompose(uuid -> uuid == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
                        : com.teslamaps.api.HypixelApi.getProfiles(uuid))
                .thenAccept(resp -> {
                    Boolean spirit = hasSpiritPet(resp);
                    if (spirit == null) return;
                    mc.execute(() -> {
                        if (startingTime != run) return;
                        firstDeathHasSpirit = spirit;
                        lastScoreCalcTime = 0L;
                        TeslaMaps.debug("[Score] first death {} spirit pet: {}", who, spirit);
                    });
                })
                .exceptionally(e -> null);
    }

    private static Boolean hasSpiritPet(com.google.gson.JsonObject resp) {
        if (resp == null || !resp.has("profiles")) return null;
        com.google.gson.JsonObject member = null;
        for (var e : resp.getAsJsonObject("profiles").entrySet()) {
            com.google.gson.JsonObject p = e.getValue().getAsJsonObject();
            if (!p.has("data")) continue;
            if (member == null || (p.has("current") && p.get("current").getAsBoolean())) member = p.getAsJsonObject("data");
        }
        if (member == null || !member.has("pets_data")) return null;
        try {
            for (var pet : member.getAsJsonObject("pets_data").getAsJsonArray("pets")) {
                com.google.gson.JsonObject o = pet.getAsJsonObject();
                if ("SPIRIT".equals(o.get("type").getAsString()) && "LEGENDARY".equals(o.get("tier").getAsString())) return true;
            }
        } catch (Exception e) {
            return null;
        }
        return false;
    }

    public static void markPrinceKilledManually() {
        if (princeKilled || !DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return;
        princeKilled = true;
        lastScoreCalcTime = 0L;
        if (!princeDeadMessageSent && TeslaMapsConfig.get().princeDeadMessage) {
            princeDeadMessageSent = true;
            com.teslamaps.features.Announce.party("Prince Dead!");
        }
    }

    public static void onMimicKilled() {
        mimicKilled = true;
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("CryptReminder")
                .when(() -> cryptReminderAt > 0L)
                .tick(() -> {
                    if (System.currentTimeMillis() < cryptReminderAt) return;
                    cryptReminderAt = 0L;
                    fireCryptReminder();
                });
    }

    private static void fireCryptReminder() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.cryptReminder || !DungeonManager.isInDungeon()) return;
        if (!cfg.cryptReminderClasses.allowsLocal()) return;
        readTab();
        int crypts = getCrypts();
        if (crypts >= 5) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
                "§c§l Crypts: " + crypts + "/5 §r§7— need 5 for the full +5 bonus score!"));
        mc.player.playSound(com.teslamaps.utils.SoundOptions.resolve(cfg.cryptReminderSound), 1.0f, 1.0f);
        if (cfg.cryptReminderTitle) {
            mc.gui.setTimes(0, 40, 10);
            mc.gui.setTitle(Component.literal("§c" + crypts + "/5 Crypts"));
        }
        com.teslamaps.features.Announce.send(cfg.cryptReminderChannel, crypts + "/5 crypts, need more!");
    }

    public static int getScore() {
        return score;
    }

    public static boolean floorHasMimics() {
        return floorHasMimics;
    }

    public static boolean isMimicKilled() {
        return mimicKilled;
    }

    public static boolean isPrinceKilled() {
        return princeKilled;
    }

    public static double getNeededSecretsPercentFor300(int cryptsFound) {
        if (!dungeonStarted) return 100.0;

        int cryptsBonus = Math.clamp(cryptsFound, 0, 5);
        int mimicBonus = mimicKilled ? 2 : 0;
        int princeBonus = princeKilled ? 1 : 0;
        int paulBonus = paulBonus();
        int totalBonus = cryptsBonus + mimicBonus + princeBonus + paulBonus;

        // A death costs skill points, and secrets are the only lever left to make them
        // back up, so the penalty has to be added on top of the secrets budget.
        int secretsScoreNeeded = Math.max(0, 40 - totalBonus + getDeathScorePenalty());

        if (secretsScoreNeeded <= 0) return 0.0;

        double neededPercent = (secretsScoreNeeded / 40.0) * floorRequirement.percentage;

        return Math.min(100.0, neededPercent);
    }

    enum FloorRequirement {
        E(30, 1200),
        F1(30, 600),
        F2(40, 600),
        F3(50, 600),
        F4(60, 720),
        F5(70, 600),
        F6(85, 720),
        F7(100, 840),
        M1(100, 480),
        M2(100, 480),
        M3(100, 480),
        M4(100, 480),
        M5(100, 480),
        M6(100, 600),
        M7(100, 840),
        NONE(0, 0);

        final int percentage;
        final int timeLimit;

        FloorRequirement(int percentage, int timeLimit) {
            this.percentage = percentage;
            this.timeLimit = timeLimit;
        }
    }
}
