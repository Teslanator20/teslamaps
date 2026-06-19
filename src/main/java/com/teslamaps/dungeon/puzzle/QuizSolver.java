package com.teslamaps.dungeon.puzzle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonWaypoints;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Quiz Solver - Highlights correct answer for Quiz (Trivia) puzzle.
 * Listens to chat messages for questions and options.
 */
public class QuizSolver {

    private static Map<String, List<String>> answers;
    private static List<String> currentAnswers = null;
    private static final TriviaOption[] triviaOptions = new TriviaOption[3];

    // Relative positions for answer stands (A, B, C)
    private static final int[][] TYPE_BLOCKS = {
        {20, 6},  // ⓐ
        {15, 9},  // ⓑ
        {10, 6}   // ⓒ
    };

    private record TriviaOption(BlockPos worldPos, boolean isCorrect) {}

    private static int[] clay = null; // {clayX, clayZ, rotation} for the current Quiz room
    private static DungeonRoom quizRoom = null;

    static {
        loadAnswers();
        for (int i = 0; i < 3; i++) {
            triviaOptions[i] = new TriviaOption(null, false);
        }
    }

    private static void loadAnswers() {
        try {
            InputStream is = QuizSolver.class.getResourceAsStream("/assets/teslamaps/puzzles/quizAnswers.json");
            if (is != null) {
                Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
                answers = new Gson().fromJson(new InputStreamReader(is), type);
                TeslaMaps.LOGGER.info("[QuizSolver] Loaded {} quiz questions", answers.size());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[QuizSolver] Failed to load answers", e);
            answers = Map.of();
        }
    }

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().solveQuiz) return;
        if (!DungeonManager.isInDungeon()) return;

        // Check for puzzle complete
        if (message.startsWith("[STATUE] Oruo the Omniscient:") && message.endsWith("correctly!")) {
            if (message.contains("answered the final question")) {
                reset();
                return;
            }
            if (message.contains("answered Question #")) {
                // Reset correct flags for next question
                for (int i = 0; i < 3; i++) {
                    triviaOptions[i] = new TriviaOption(triviaOptions[i].worldPos, false);
                }
            }
        }

        // Check for answer options (ⓐ, ⓑ, ⓒ)
        String trimmed = message.trim();

        // Detect answer option lines
        int optionIndex = -1;
        if (trimmed.contains("ⓐ")) optionIndex = 0;
        else if (trimmed.contains("ⓑ")) optionIndex = 1;
        else if (trimmed.contains("ⓒ")) optionIndex = 2;

        if (optionIndex >= 0 && currentAnswers != null) {
            // Extract the answer text after the circle letter
            String answerText = trimmed;
            int circleIdx = Math.max(trimmed.indexOf("ⓐ"), Math.max(trimmed.indexOf("ⓑ"), trimmed.indexOf("ⓒ")));
            if (circleIdx >= 0 && circleIdx + 1 < trimmed.length()) {
                answerText = trimmed.substring(circleIdx + 1).trim();
            }

            TeslaMaps.LOGGER.info("[QuizSolver] Option {} text: '{}', looking for: {}",
                (char)('A' + optionIndex), answerText, currentAnswers);

            for (String answer : currentAnswers) {
                // Odin-style precise match: the option line ends with the answer text.
                if (trimmed.endsWith(answer) || answerText.equalsIgnoreCase(answer)) {
                    triviaOptions[optionIndex] = new TriviaOption(triviaOptions[optionIndex].worldPos, true);
                    TeslaMaps.LOGGER.info("[QuizSolver] Correct answer found: {} (option {})", answer, (char)('A' + optionIndex));
                    break;
                }
            }
        }

        // Check for dynamic SkyBlock year question
        if (trimmed.equals("What SkyBlock year is it?")) {
            long skyblockYear = ((System.currentTimeMillis() / 1000) - 1560276000) / 446400 + 1;
            currentAnswers = List.of("Year " + skyblockYear);
            TeslaMaps.LOGGER.info("[QuizSolver] Dynamic answer: Year {}", skyblockYear);
            return;
        }

        // Check for questions in database
        for (Map.Entry<String, List<String>> entry : answers.entrySet()) {
            if (trimmed.contains(entry.getKey())) {
                currentAnswers = entry.getValue();
                TeslaMaps.LOGGER.info("[QuizSolver] Found question: {} -> {}", entry.getKey(), currentAnswers);
                return;
            }
        }
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().solveQuiz) {
            reset();
            return;
        }

        if (!DungeonManager.isInDungeon()) {
            reset();
            return;
        }

        // Find Quiz room from dungeon grid if we don't have it yet
        if (quizRoom == null && DungeonManager.getGrid() != null) {
            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                if (room != null && "Quiz".equals(room.getName())) {
                    quizRoom = room;
                    TeslaMaps.LOGGER.info("[QuizSolver] Found Quiz room in dungeon");
                    break;
                }
            }
        }

        if (quizRoom == null) {
            // Quiz room not discovered yet
            return;
        }

        // Only resolve stand positions while actively in a quiz (avoids fighting the shared clay
        // cache used by DungeonWaypoints when we're in a different room).
        if (currentAnswers == null) return;

        // Use the real BLUE_TERRACOTTA marker (clayPos) + Odin's rotateAroundNorth, exactly like
        // DungeonWaypoints. The room corner/rotation differs from clayPos for some rotations, which
        // made the box land on the wrong stand intermittently.
        if (clay == null) clay = DungeonWaypoints.scanClayPos(quizRoom);
        if (clay == null) return; // terracotta not loaded/scannable yet

        // Update world positions for answer stands
        for (int i = 0; i < 3; i++) {
            int[] worldPos = DungeonWaypoints.relativeToWorld(clay, TYPE_BLOCKS[i][0], TYPE_BLOCKS[i][1]);
            triviaOptions[i] = new TriviaOption(
                new BlockPos(worldPos[0], 70, worldPos[1]),
                triviaOptions[i].isCorrect
            );
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().solveQuiz) return;
        if (currentAnswers == null) return;

        int color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorQuiz);

        for (int i = 0; i < triviaOptions.length; i++) {
            TriviaOption option = triviaOptions[i];
            if (!option.isCorrect) continue;
            if (option.worldPos == null) {
                TeslaMaps.LOGGER.warn("[QuizSolver] Option {} is correct but has no world position!", (char)('A' + i));
                continue;
            }

            BlockPos pos = option.worldPos.below(); // Render on ground
            AABB box = new AABB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
            );
            ESPRenderer.drawESPBox(matrices, box, color, cameraPos);

            // Draw beacon beam
            if (TeslaMapsConfig.get().quizBeacon) {
                ESPRenderer.drawBeaconBeam(matrices, pos, color, cameraPos);
            }
        }
    }

    /** Classification of a chat line that is a quiz answer option. */
    public enum QuizLine { NONE, CORRECT, WRONG }

    /**
     * Classify a chat line as a correct/wrong quiz option (or NONE if it isn't one,
     * or the quiz isn't active). Used by ChatMixin to recolor / hide answers in place.
     */
    public static QuizLine classifyLine(String message) {
        if (!TeslaMapsConfig.get().solveQuiz) return QuizLine.NONE;
        if (currentAnswers == null) return QuizLine.NONE;

        String trimmed = message.trim();
        int circleIdx = -1;
        if (trimmed.contains("ⓐ")) circleIdx = trimmed.indexOf("ⓐ");
        else if (trimmed.contains("ⓑ")) circleIdx = trimmed.indexOf("ⓑ");
        else if (trimmed.contains("ⓒ")) circleIdx = trimmed.indexOf("ⓒ");
        if (circleIdx < 0) return QuizLine.NONE;

        String answerText = (circleIdx + 1 < trimmed.length()) ? trimmed.substring(circleIdx + 1).trim() : "";
        for (String answer : currentAnswers) {
            if (trimmed.endsWith(answer) || answerText.equalsIgnoreCase(answer)) {
                return QuizLine.CORRECT;
            }
        }
        return QuizLine.WRONG;
    }

    /** True if this wrong-answer line should be hidden from chat. */
    public static boolean shouldHide(String message) {
        return TeslaMapsConfig.get().quizHideWrongAnswers && classifyLine(message) == QuizLine.WRONG;
    }

    /**
     * If chat highlight is enabled and this line is the correct answer, return a
     * recolored (green + bold) replacement Component. Otherwise return null (leave as-is).
     */
    public static Component highlightLine(Component message) {
        if (!TeslaMapsConfig.get().quizChatHighlight) return null;
        if (classifyLine(message.getString()) != QuizLine.CORRECT) return null;
        String stripped = message.getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
        return Component.literal("§a§l" + stripped);
    }

    public static void reset() {
        currentAnswers = null;
        quizRoom = null;
        clay = null;
        for (int i = 0; i < 3; i++) {
            triviaOptions[i] = new TriviaOption(null, false);
        }
    }
}
