package com.teslamaps.dungeon;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.PlayerTabOverlayAccessor;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon score calculator - ported directly from Skyblocker's DungeonScore.java
 */
public class DungeonScore {
    // Scoreboard patterns
    private static final Pattern CLEARED_PATTERN = Pattern.compile("Cleared: (?<cleared>\\d+)%.*");
    private static final Pattern FLOOR_PATTERN = Pattern.compile(".*Cata.*ombs.*\\((?<floor>[EFM]\\d+)\\)");

    // Playerlist patterns
    private static final Pattern SECRETS_PATTERN = Pattern.compile("Secrets Found: (?<secper>\\d+\\.?\\d*)%");
    private static final Pattern PUZZLES_PATTERN = Pattern.compile(".+?(?=:): \\[(?<state>.)](?: \\(\\w*\\))?");
    private static final Pattern PUZZLE_COUNT_PATTERN = Pattern.compile("Puzzles: \\((?<count>\\d+)\\)");
    private static final Pattern CRYPTS_PATTERN = Pattern.compile("Crypts: (?<crypts>\\d+)");
    private static final Pattern COMPLETED_ROOMS_PATTERN = Pattern.compile(" *Completed Rooms: (?<rooms>\\d+)");

    // Chat patterns
    private static final Pattern DEATHS_PATTERN = Pattern.compile(" \u2620 (?<whodied>\\S+) .*");
    private static final Pattern MIMIC_PATTERN = Pattern.compile(".*?(?:Mimic dead!?|Mimic Killed!|\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$)$");
    private static final Pattern PRINCE_PATTERN = Pattern.compile(".*?(?:Prince dead!?|Prince Killed!)$");
    private static final String PRINCE_KILL_MESSAGE = "A Prince falls. +1 Bonus Score";

    // Other patterns
    private static final Pattern MIMIC_FLOORS_PATTERN = Pattern.compile("[FM][67]");

    // State
    private static FloorRequirement floorRequirement = FloorRequirement.NONE;
    private static String currentFloor = "";
    private static boolean isCurrentFloorEntrance = false;
    private static boolean floorHasMimics = false;
    private static boolean mimicKilled = false;
    private static boolean princeKilled = false;
    private static boolean dungeonStarted = false;
    private static boolean bloodRoomCompleted = false;
    private static boolean sent270 = false;
    private static boolean sent300 = false;
    private static long startingTime = 0L;
    private static int puzzleCount = 0;
    private static int deathCount = 0;
    private static int score = 0;

    // Tab list sorting
    private static Comparator<PlayerListEntry> tabListComparator = null;

    // Floor detection delay
    private static boolean floorDetectionPending = false;
    private static long floorDetectionTime = 0L;

    public static void reset() {
        floorRequirement = FloorRequirement.NONE;
        currentFloor = "";
        isCurrentFloorEntrance = false;
        floorHasMimics = false;
        mimicKilled = false;
        princeKilled = false;
        dungeonStarted = false;
        bloodRoomCompleted = false;
        sent270 = false;
        sent300 = false;
        startingTime = 0L;
        puzzleCount = 0;
        deathCount = 0;
        score = 0;
        floorDetectionPending = false;
        floorDetectionTime = 0L;
    }

    public static void onDungeonStart() {
        reset();
        dungeonStarted = true;
        startingTime = System.currentTimeMillis();

        // Wait 3 seconds for scoreboard to update before detecting floor
        floorDetectionPending = true;
        floorDetectionTime = System.currentTimeMillis() + 3000;

        // Logging disabled
    }

    private static int debugCounter = 0;

    public static int calculateScore() {
        if (!dungeonStarted) return 0;

        // Check if we need to detect floor after delay
        if (floorDetectionPending && System.currentTimeMillis() >= floorDetectionTime) {
            floorDetectionPending = false;
            setCurrentFloor();
            puzzleCount = getPuzzleCount();
            try {
                floorRequirement = FloorRequirement.valueOf(currentFloor);
            } catch (IllegalArgumentException e) {
                floorRequirement = FloorRequirement.F1;
            }
            floorHasMimics = MIMIC_FLOORS_PATTERN.matcher(currentFloor).matches();
            if (currentFloor.equals("E")) isCurrentFloorEntrance = true;
        }

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

        // Debug logging disabled to reduce spam
        debugCounter++;

        // Score milestone messages
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        long elapsed = (System.currentTimeMillis() - startingTime) / 1000;
        int minutes = (int) (elapsed / 60);
        int seconds = (int) (elapsed % 60);
        String timeStr = String.format("%02d:%02d", minutes, seconds);

        mc.player.sendMessage(
            Text.literal("[")
                .append(Text.literal("TeslaMaps").styled(style -> style.withColor(0x55FF55)))
                .append(Text.literal("] "))
                .append(Text.literal(milestone + " Score reached @ " + timeStr).styled(style -> style.withColor(0xFFFFFF))),
            false
        );
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
        int paulScore = TeslaMapsConfig.get().assumePaulMayor ? 10 : 0;
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

    private static int getCompletedRooms() {
        Matcher matcher = regexAt(43, COMPLETED_ROOMS_PATTERN);
        return matcher != null ? Integer.parseInt(matcher.group("rooms")) : 0;
    }

    /**
     * Assumes boss and blood room are done from the start, then removes assumption as they actually complete.
     * This prevents score jumps when entering boss or completing blood.
     */
    private static int getExtraCompletedRooms() {
        // Before blood room is done: assume both blood (1) and boss (1) are done = +2 rooms
        if (!bloodRoomCompleted) {
            return isCurrentFloorEntrance ? 1 : 2;
        }
        // After blood done but before entering boss: still assume boss is done = +1 room
        if (!DungeonManager.isInBoss() && !isCurrentFloorEntrance) {
            return 1;
        }
        // In boss room: both are actually done, no extra assumption needed
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

    private static int getDeathScorePenalty() {
        // Assume Spirit Pet on first death (like Skyblocker with API check)
        if (deathCount == 0) return 0;
        return 1 + (deathCount - 1) * 2;  // First death: -1, subsequent: -2 each
    }

    private static int getPuzzleCount() {
        Matcher matcher = regexAt(47, PUZZLE_COUNT_PATTERN);
        return matcher != null ? Integer.parseInt(matcher.group("count")) : 0;
    }

    private static int getPuzzlePenalty() {
        int incompletePuzzles = 0;
        for (int index = 0; index < puzzleCount; index++) {
            Matcher puzzleMatcher = regexAt(48 + index, PUZZLES_PATTERN);
            if (puzzleMatcher == null) break;
            // EXACT COPY from Skyblocker: Both ✖ (failed) and ✦ (not started) count as incomplete
            if (puzzleMatcher.group("state").matches("[✖✦]")) incompletePuzzles++;
        }
        return incompletePuzzles * 10;
    }

    private static double getSecretsPercentage() {
        Matcher matcher = regexAt(44, SECRETS_PATTERN);
        return matcher != null ? Double.parseDouble(matcher.group("secper")) : 0;
    }

    private static int getCrypts() {
        Matcher matcher = regexAt(33, CRYPTS_PATTERN);
        if (matcher == null) matcher = regexAt(32, CRYPTS_PATTERN);
        return matcher != null ? Integer.parseInt(matcher.group("crypts")) : 0;
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

    /**
     * Get tab list entry at index and match against pattern (like Skyblocker's PlayerListManager.regexAt)
     */
    private static Matcher regexAt(int index, Pattern pattern) {
        String str = strAt(index);
        if (str == null) return null;

        Matcher m = pattern.matcher(str);
        if (!m.matches()) {
            return null;
        }
        return m;
    }

    /**
     * Get tab list string at index (sorted like vanilla tab display)
     */
    private static String strAt(int index) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return null;

        // Get the comparator used by the vanilla tab overlay
        if (tabListComparator == null) {
            try {
                tabListComparator = PlayerTabOverlayAccessor.getOrdering();
            } catch (Exception e) {
                return null;
            }
        }

        // Sort entries like vanilla tab list display
        List<PlayerListEntry> playerList = mc.getNetworkHandler().getPlayerList()
                .stream()
                .sorted(tabListComparator)
                .toList();

        if (playerList.size() <= index) return null;

        var txt = playerList.get(index).getDisplayName();
        if (txt == null) return null;

        String str = txt.getString().trim();
        if (str.isEmpty()) return null;

        return str;
    }

    // Chat message handlers
    public static void onChatMessage(String message) {
        if (!dungeonStarted) return;

        // Death detection
        if (message.length() > 1 && message.charAt(1) == '\u2620') {
            Matcher matcher = DEATHS_PATTERN.matcher(message);
            if (matcher.matches()) {
                deathCount++;
            }
        }

        // Watcher complete
        if (message.equals("[BOSS] The Watcher: You have proven yourself. You may pass.")) {
            new Thread(() -> {
                try {
                    Thread.sleep(5500);
                    bloodRoomCompleted = true;
                } catch (InterruptedException ignored) {}
            }).start();
        }

        // Mimic detection
        if (floorHasMimics && MIMIC_PATTERN.matcher(message).matches()) {
            mimicKilled = true;
        }

        // Prince detection
        if (PRINCE_PATTERN.matcher(message).matches() || message.equals(PRINCE_KILL_MESSAGE)) {
            princeKilled = true;
        }
    }

    public static void onMimicKilled() {
        mimicKilled = true;
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

    /**
     * Get the secret percentage needed for 300 score (accounting for bonuses).
     * Max score: Time (100) + Skill (100) + Explore (100) + Bonus (0-17) = 317
     * For 300 with perfect time/skill: Explore + Bonus >= 100
     * Explore = 60 (rooms) + secretsScore (0-40)
     * So we need: secretsScore >= 40 - Bonus
     */
    public static double getNeededSecretsPercentFor300(int cryptsFound) {
        if (!dungeonStarted) return 100.0;

        // Calculate current bonuses
        int cryptsBonus = Math.clamp(cryptsFound, 0, 5);
        int mimicBonus = mimicKilled ? 2 : 0;
        int paulBonus = com.teslamaps.config.TeslaMapsConfig.get().assumePaulMayor ? 10 : 0;
        // Prince bonus not tracked reliably yet, assume 0
        int totalBonus = cryptsBonus + mimicBonus + paulBonus;

        // Secrets score needed for 300: max(0, 40 - totalBonus)
        int secretsScoreNeeded = Math.max(0, 40 - totalBonus);

        // If no secrets needed (enough bonuses), return 0
        if (secretsScoreNeeded <= 0) return 0.0;

        // Convert secrets score to percentage
        // secretsScore = (40 * min(secrets%, required%) / required%)
        // So: secrets% = (secretsScore / 40) * required%
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
