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
    private static boolean soulsRequested = false;

    private static final String FAIRY_SOULS_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/fairy_souls.json";
    // question island -> NEU location key (Skyblocker Trivia)
    private static final String[][] SOUL_QUESTIONS = {
        {"How many total Fairy Souls are there?", "Max Souls"},
        {"How many Fairy Souls are there in Spider's Den?", "combat_1"},
        {"How many Fairy Souls are there in Spiders Den?", "combat_1"},
        {"How many Fairy Souls are there in The End?", "combat_3"},
        {"How many Fairy Souls are there in The Farming Islands?", "farming_1"},
        {"How many Fairy Souls are there in Crimson Isle?", "crimson_isle"},
        {"How many Fairy Souls are there in The Park?", "foraging_1"},
        {"How many Fairy Souls are there in Jerry's Workshop?", "winter"},
        {"How many Fairy Souls are there in Hub?", "hub"},
        {"How many Fairy Souls are there in The Hub?", "hub"},
        {"How many Fairy Souls are there in Deep Caverns?", "mining_2"},
        {"How many Fairy Souls are there in Gold Mine?", "mining_1"},
        {"How many Fairy Souls are there in Dungeon Hub?", "dungeon_hub"},
        {"How many Fairy Souls are there in Backwater Bayou?", "fishing_1"},
    };
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
                TeslaMaps.debug("[QuizSolver] Loaded {} quiz questions", answers.size());
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

            TeslaMaps.debug("[QuizSolver] Option {} text: '{}', looking for: {}",
                (char)('A' + optionIndex), answerText, currentAnswers);

            for (String answer : currentAnswers) {
                if (trimmed.endsWith(answer) || answerText.equalsIgnoreCase(answer)) {
                    triviaOptions[optionIndex] = new TriviaOption(triviaOptions[optionIndex].worldPos, true);
                    TeslaMaps.debug("[QuizSolver] Correct answer found: {} (option {})", answer, (char)('A' + optionIndex));
                    break;
                }
            }
        }

        if (trimmed.equals("What SkyBlock year is it?")) {
            long skyblockYear = ((System.currentTimeMillis() / 1000) - 1560276000) / 446400 + 1;
            currentAnswers = List.of("Year " + skyblockYear);
            TeslaMaps.debug("[QuizSolver] Dynamic answer: Year {}", skyblockYear);
            return;
        }

        for (Map.Entry<String, List<String>> entry : answers.entrySet()) {
            if (trimmed.contains(entry.getKey())) {
                currentAnswers = entry.getValue();
                TeslaMaps.debug("[QuizSolver] Found question: {} -> {}", entry.getKey(), currentAnswers);
                return;
            }
        }
    }

    private static void loadFairySouls() {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10)).build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(FAIRY_SOULS_URL))
                    .timeout(java.time.Duration.ofSeconds(20)).header("User-Agent", "TeslaMaps").GET().build();
            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, err) -> {
                        if (err != null) TeslaMaps.LOGGER.warn("[QuizSolver] fairy soul fetch failed: {}", err.toString());
                        else applyFairySouls(response);
                    });
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[QuizSolver] fairy soul fetch failed: {}", e.toString());
        }
    }

    private static void applyFairySouls(java.net.http.HttpResponse<String> response) {
        try {
            if (response.statusCode() != 200) return;
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            Map<String, List<String>> live = new java.util.HashMap<>();
            for (String[] q : SOUL_QUESTIONS) {
                if (!root.has(q[1])) continue;
                var v = root.get(q[1]);
                int count = v.isJsonArray() ? v.getAsJsonArray().size() : v.getAsInt();
                live.put(q[0], List.of(count + " Fairy Souls"));
            }
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (answers == null) return;
                Map<String, List<String>> merged = new java.util.LinkedHashMap<>(answers);
                merged.putAll(live);
                answers = merged;
                TeslaMaps.debug("[QuizSolver] {} fairy soul answers from NEU", live.size());
            });
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[QuizSolver] fairy soul fetch failed: {}", e.toString());
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

        if (!soulsRequested) {
            soulsRequested = true;
            loadFairySouls();
        }

        if (quizRoom == null && DungeonManager.getGrid() != null) {
            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                if (room != null && "Quiz".equals(room.getName())) {
                    quizRoom = room;
                    TeslaMaps.debug("[QuizSolver] Found Quiz room in dungeon");
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
            PuzzleDraw.box(matrices, box, color, TeslaMapsConfig.get().quizStyle, 3.0f, cameraPos, !TeslaMapsConfig.get().quizDepthCheck);

            if (TeslaMapsConfig.get().quizBeacon) {
                ESPRenderer.drawBeaconBeam(matrices, pos, color, cameraPos);
            }
        }
    }

    /** true = swallow the click: next to a wrong answer while exactly one answer is known right (sneak overrides). */
    public static boolean blockClick(BlockPos pos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.solveQuiz || !c.quizBlockWrongClicks || com.teslamaps.features.LegitMode.blocksCheats()) return false;
        if (!c.section("Puzzles & Terminals")) return false;
        if (currentAnswers == null) return false;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.player.isCrouching()) return false;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) return false;
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || !"Quiz".equals(room.getName())) return false;
        int correct = -1;
        for (int i = 0; i < 3; i++) {
            if (triviaOptions[i].worldPos == null) return false;
            if (triviaOptions[i].isCorrect) {
                if (correct >= 0) return false;
                correct = i;
            }
        }
        if (correct < 0) return false;
        if (triviaOptions[correct].worldPos.distManhattan(pos) <= 2) return false;
        for (int i = 0; i < 3; i++) {
            if (i != correct && triviaOptions[i].worldPos.distManhattan(pos) <= 1) return true;
        }
        return false;
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
        int rgb = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorQuizChat) & 0xFFFFFF;
        return Component.literal(stripped).withStyle(s -> s.withColor(rgb).withBold(true));
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
