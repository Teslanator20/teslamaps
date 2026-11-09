package com.teslamaps.dungeon.puzzle;

// Timer ported from Odin's QuizSolver.kt / PuzzleSolvers.kt, splits from Devonian's TriviaSplits.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuizExtras {

    private static final String SECTION = "Puzzles & Terminals";
    public static final String[] FORMATS = {"Real Time", "Server Ticks", "Both"};
    private static final String ORUO = "[STATUE] Oruo the Omniscient: ";
    private static final int START_DURATION = 220;
    private static final int QUESTION_DURATION = 100;
    private static final String TIMER_SAMPLE = "§5Quiz §8(§f1§8/§f3§8): §a11.0s";
    private static final List<String> SPLITS_SAMPLE = List.of("§dQ1§f: §a3.25s", "§dQ2§f: §a4.10s", "§dQ3§f: §e1.85s");

    private static final ServerTickTimer TIMER = new ServerTickTimer();
    private static int timerMax = 0;
    private static int timerStage = 0;
    private static volatile String timerLine = null;

    private static final long[] startMs = new long[3], endMs = new long[3];
    private static final long[] startTick = new long[3], endTick = new long[3];
    private static boolean splitsSent = false;
    private static volatile List<String> splitLines = List.of();

    private static Object lastLevel = null;
    private static HudEditElement timerHud, splitsHud;

    private QuizExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        timerHud = HudEditElement.of("quizTimerHud", "Quiz Timer").ctx(HudEditElement.HudCtx.DUNGEON).section(SECTION)
                .enabled(() -> cfg().solveQuiz && cfg().quizTimer)
                .size(() -> Minecraft.getInstance().font.width(TIMER_SAMPLE), () -> 9)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of(TIMER_SAMPLE)));
        splitsHud = HudEditElement.of("quizSplitsHud", "Quiz Splits").ctx(HudEditElement.HudCtx.DUNGEON).section(SECTION)
                .enabled(() -> cfg().solveQuiz && cfg().quizSplits)
                .size(() -> Minecraft.getInstance().font.width("§dQ1§f: §a3.25s §7(3.30s)"), () -> 30)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SPLITS_SAMPLE));
        Feature.of("QuizExtras").section(SECTION).when(() -> cfg().solveQuiz && (cfg().quizTimer || cfg().quizSplits))
                .tick(QuizExtras::tick)
                .serverChat(QuizExtras::onChat);
        Feature.of("QuizTimerHud").section(SECTION).when(() -> cfg().solveQuiz && cfg().quizTimer)
                .hud("quiztimerhud", QuizExtras::renderTimer);
        Feature.of("QuizSplitsHud").section(SECTION).when(() -> cfg().solveQuiz && cfg().quizSplits)
                .hud("quizsplitshud", QuizExtras::renderSplits);
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String msg = StringUtil.stripTrim(raw);
        TeslaMapsConfig c = cfg();

        if (c.quizTimer && msg.startsWith(ORUO)) {
            String rest = msg.substring(ORUO.length());
            if (rest.equals("I am Oruo the Omniscient. I have lived many lives. I have learned all there is to know.")) startTimer(START_DURATION, 1);
            else if (rest.equals("2 questions left... Then you will have proven your worth to me!")) startTimer(QUESTION_DURATION, 2);
            else if (rest.equals("One more question!")) startTimer(QUESTION_DURATION, 3);
            else if (rest.contains("answered the final question")) TIMER.stop();
        }

        if (!c.quizSplits) return;
        if (msg.startsWith("Question #") && msg.length() == 11) {
            int q = msg.charAt(10) - '1';
            if (q >= 0 && q < 3 && startMs[q] == 0) {
                startMs[q] = System.currentTimeMillis();
                startTick[q] = ServerTickTimer.now();
            }
            return;
        }
        if (!msg.startsWith(ORUO) || !msg.endsWith("correctly!")) return;
        int q = msg.contains("answered Question #1") ? 0 : msg.contains("answered Question #2") ? 1
                : msg.contains("answered the final question") ? 2 : -1;
        if (q < 0 || startMs[q] == 0 || endMs[q] != 0) return;
        endMs[q] = System.currentTimeMillis();
        endTick[q] = ServerTickTimer.now();
        if (q == 2 && !splitsSent) {
            splitsSent = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §5Quiz§f: " + String.join(" §f| ", buildSplits())));
        }
    }

    private static void startTimer(int ticks, int stage) {
        timerMax = ticks;
        timerStage = stage;
        TIMER.start(ticks);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel || !DungeonManager.isInDungeon()) {
            lastLevel = mc.level;
            reset();
            return;
        }
        TeslaMapsConfig c = cfg();

        if (c.quizTimer && TIMER.active() && !DungeonManager.isInBossArea()) {
            long rem = TIMER.remaining();
            String color = rem >= timerMax * 0.66 ? "§a" : rem >= timerMax * 0.33 ? "§6" : "§c";
            timerLine = "§5Quiz §8(§f" + timerStage + "§8/§f3§8): " + color + String.format(Locale.ROOT, "%.1fs", rem / 20.0);
        } else {
            timerLine = null;
        }

        splitLines = c.quizSplits && startMs[0] != 0 && !DungeonManager.isInBossArea() ? buildSplits() : List.of();
    }

    private static List<String> buildSplits() {
        String fmt = cfg().quizSplitsFormat;
        long nowMs = System.currentTimeMillis(), nowTick = ServerTickTimer.now();
        List<String> out = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            if (startMs[i] == 0) break;
            boolean done = endMs[i] != 0;
            double real = ((done ? endMs[i] : nowMs) - startMs[i]) / 1000.0;
            double ticks = ((done ? endTick[i] : nowTick) - startTick[i]) / 20.0;
            String col = done ? "§a" : "§e";
            String t = switch (fmt) {
                case "Server Ticks" -> String.format(Locale.ROOT, "%.2fs", ticks);
                case "Both" -> String.format(Locale.ROOT, "%.2fs §7(%.2fs)", real, ticks);
                default -> String.format(Locale.ROOT, "%.2fs", real);
            };
            out.add("§dQ" + (i + 1) + "§f: " + col + t);
        }
        return out;
    }

    private static void renderTimer(GuiGraphicsExtractor g, DeltaTracker d) {
        String line = timerLine;
        if (line == null || timerHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(timerHud.x(), timerHud.y());
        pose.scale(timerHud.scale(), timerHud.scale());
        g.text(mc.font, line, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static void renderSplits(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = splitLines;
        if (l.isEmpty() || splitsHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, splitsHud.x(), splitsHud.y(), splitsHud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static void reset() {
        TIMER.stop();
        timerLine = null;
        timerStage = 0;
        for (int i = 0; i < 3; i++) startMs[i] = endMs[i] = startTick[i] = endTick[i] = 0;
        splitsSent = false;
        splitLines = List.of();
    }
}
