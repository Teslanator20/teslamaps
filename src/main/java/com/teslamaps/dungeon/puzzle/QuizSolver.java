/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

public class QuizSolver {

    private static Map<String, List<String>> answers;
    private static List<String> currentAnswers = null;
    private static final TriviaOption[] triviaOptions = new TriviaOption[3];

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

        if (message.startsWith("[STATUE] Oruo the Omniscient:") && message.endsWith("correctly!")) {
            if (message.contains("answered the final question")) {
                reset();
                return;
            }
            if (message.contains("answered Question #")) {
                for (int i = 0; i < 3; i++) {
                    triviaOptions[i] = new TriviaOption(triviaOptions[i].worldPos, false);
                }
            }
        }

        String trimmed = message.trim();

        int optionIndex = -1;
        if (trimmed.contains("ⓐ")) optionIndex = 0;
        else if (trimmed.contains("ⓑ")) optionIndex = 1;
        else if (trimmed.contains("ⓒ")) optionIndex = 2;

        if (optionIndex >= 0 && currentAnswers != null) {
            String answerText = trimmed;
            int circleIdx = Math.max(trimmed.indexOf("ⓐ"), Math.max(trimmed.indexOf("ⓑ"), trimmed.indexOf("ⓒ")));
            if (circleIdx >= 0 && circleIdx + 1 < trimmed.length()) {
                answerText = trimmed.substring(circleIdx + 1).trim();
            }

            TeslaMaps.LOGGER.info("[QuizSolver] Option {} text: '{}', looking for: {}",
                (char)('A' + optionIndex), answerText, currentAnswers);

            for (String answer : currentAnswers) {
                if (trimmed.endsWith(answer) || answerText.equalsIgnoreCase(answer)) {
                    triviaOptions[optionIndex] = new TriviaOption(triviaOptions[optionIndex].worldPos, true);
                    TeslaMaps.LOGGER.info("[QuizSolver] Correct answer found: {} (option {})", answer, (char)('A' + optionIndex));
                    break;
                }
            }
        }

        if (trimmed.equals("What SkyBlock year is it?")) {
            long skyblockYear = ((System.currentTimeMillis() / 1000) - 1560276000) / 446400 + 1;
            currentAnswers = List.of("Year " + skyblockYear);
            TeslaMaps.LOGGER.info("[QuizSolver] Dynamic answer: Year {}", skyblockYear);
            return;
        }

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
            return;
        }

        if (currentAnswers == null) return;

        if (clay == null) clay = DungeonWaypoints.scanClayPos(quizRoom);
        if (clay == null) return; // terracotta not loaded/scannable yet

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

            if (TeslaMapsConfig.get().quizBeacon) {
                ESPRenderer.drawBeaconBeam(matrices, pos, color, cameraPos);
            }
        }
    }

    public static boolean isActive() { return currentAnswers != null; }

    public enum QuizLine { NONE, CORRECT, WRONG }

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

    public static boolean shouldHide(String message) {
        return TeslaMapsConfig.get().quizHideWrongAnswers && classifyLine(message) == QuizLine.WRONG;
    }

    public static Component highlightLine(Component message) {
        if (!TeslaMapsConfig.get().quizChatHighlight) return null;
        if (classifyLine(message.getString()) != QuizLine.CORRECT) return null;
        String stripped = message.getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
        return Component.literal("§c§l" + stripped); // red + bold
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
