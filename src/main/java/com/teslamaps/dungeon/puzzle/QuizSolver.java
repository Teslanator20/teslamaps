package com.teslamaps.dungeon.puzzle;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quiz Solver - Highlights correct answer for Quiz (Trivia) puzzle.
 * Listens to chat messages for questions and options.
 */
public class QuizSolver {

    private static Map<String, List<String>> answers;
    private static List<String> currentAnswers = null;
    private static final TriviaOption[] triviaOptions = new TriviaOption[3];

    // Relative positions for answer stands (A, B, C)
    private static final BlockPos[] RELATIVE_POSITIONS = {
        new BlockPos(20, 70, 6),  // A
        new BlockPos(15, 70, 9),  // B
        new BlockPos(10, 70, 6)   // C
    };

    private record TriviaOption(BlockPos worldPos, boolean isCorrect) {}

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
        if (currentAnswers != null) {
            if (trimmed.startsWith("ⓐ") || trimmed.startsWith("ⓑ") || trimmed.startsWith("ⓒ")) {
                for (String answer : currentAnswers) {
                    if (trimmed.endsWith(answer)) {
                        int index = switch (trimmed.charAt(0)) {
                            case 'ⓐ' -> 0;
                            case 'ⓑ' -> 1;
                            case 'ⓒ' -> 2;
                            default -> -1;
                        };
                        if (index >= 0) {
                            triviaOptions[index] = new TriviaOption(triviaOptions[index].worldPos, true);
                            TeslaMaps.LOGGER.info("[QuizSolver] Correct answer: {} (option {})", answer, (char)('A' + index));
                        }
                        break;
                    }
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

        // Check if we're in Quiz room and update positions
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null || !room.getName().equals("Quiz")) {
            return;
        }

        BlockPos corner = room.getCorner();
        if (corner == null) return;
        int rotation = room.getRotation();

        // Update world positions for answer stands
        for (int i = 0; i < 3; i++) {
            BlockPos worldPos = transformPos(RELATIVE_POSITIONS[i], corner, rotation);
            triviaOptions[i] = new TriviaOption(worldPos, triviaOptions[i].isCorrect);
        }
    }

    private static BlockPos transformPos(BlockPos relative, BlockPos corner, int rotation) {
        int x = relative.getX();
        int z = relative.getZ();

        int rx, rz;
        switch (rotation) {
            case 1 -> { rx = 30 - z; rz = x; }
            case 2 -> { rx = 30 - x; rz = 30 - z; }
            case 3 -> { rx = z; rz = 30 - x; }
            default -> { rx = x; rz = z; }
        }

        return new BlockPos(corner.getX() + rx, relative.getY(), corner.getZ() + rz);
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().solveQuiz) return;
        if (currentAnswers == null) return;

        int color = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorQuiz);

        for (TriviaOption option : triviaOptions) {
            if (!option.isCorrect || option.worldPos == null) continue;

            BlockPos pos = option.worldPos.down(); // Render on ground
            Box box = new Box(
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

    public static void reset() {
        currentAnswers = null;
        for (int i = 0; i < 3; i++) {
            triviaOptions[i] = new TriviaOption(null, false);
        }
    }
}
