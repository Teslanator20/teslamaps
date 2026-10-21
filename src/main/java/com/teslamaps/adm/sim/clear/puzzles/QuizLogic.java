// Ported from ADM's p3sim/clear/puzzles/QuizLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Quiz as a state machine: three questions, three options each, the statue speaks, the party answers by pressing a button on one
 * of three pillars. Questions and answers are Odin's QuizSolver data (see OdinTables); the wrong options are other questions' answers.
 * Words, colours and pauses measured over 118 real quizzes. Pure: no Minecraft; the glue sends the lines.
 */
public class QuizLogic {
    public final List<Question> questions;

    public QuizLogic(List<Question> questions) { this.questions = questions; }

    /** One question as asked: correct is the index of the right option. */
    public static class Question {
        public final String text;
        public final List<String> options;
        public final int correct;

        public Question(String text, List<String> options, int correct) {
            this.text = text;
            this.options = options;
            this.correct = correct;
        }
    }

    /** A chat line and the ticks to wait after the previous line before saying it. */
    public static class Line {
        public final int delay;
        public final String text;

        public Line(int delay, String text) {
            this.delay = delay;
            this.text = text;
        }
    }

    /** Pauses, in ticks (medians of the real quizzes). */
    public static final class Pace {
        private Pace() {}

        /** Between the intro lines: 40, 41 and 40. */
        public static final List<Integer> INTRO_GAPS = List.of(40, 41, 40);
        public static final int AFTER_INTRO = 40;
        /** Correct answer to the remark. */
        public static final int ANSWER_TO_REMARK = 40;
        /** Remark to the next question. */
        public static final int REMARK_TO_QUESTION = 40;
        /** Final answer to "I bestow upon you". */
        public static final int FINAL_TO_BESTOW = 19;
        /** "Yikes" to the wrong-answer line. */
        public static final int YIKES_TO_WRONG = 29;
        /** The wrong-answer line to the intro starting over. */
        public static final int WRONG_TO_RESTART = 56;
        /** "Yikes" to the tab showing Failed. */
        public static final int YIKES_TO_TAB_FAILED = 16;
    }

    public enum Phase { IDLE, ASKING, SOLVED }

    public static class Outcome {
        public final boolean correct;
        public final List<Line> lines;
        public final boolean solved;

        public Outcome(boolean correct, List<Line> lines, boolean solved) {
            this.correct = correct;
            this.lines = lines;
            this.solved = solved;
        }
    }

    public Phase phase = Phase.IDLE;

    /** Index of the question being asked (0 based). */
    public int current = 0;

    /** The intro and the first question. */
    public List<Line> start() {
        if (phase != Phase.IDLE) throw new IllegalStateException("the quiz already started");
        phase = Phase.ASKING;
        current = 0;
        List<Integer> g = Pace.INTRO_GAPS;
        List<Line> out = new ArrayList<>();
        out.add(new Line(0, STATUE + "I am §r§4Oruo the Omniscient§r§f. I have lived many lives. I have learned all there is to know."));
        out.add(new Line(g.get(0), STATUE + "Though I sit stationary in this prison that is §r§cThe Catacombs§r§f, my knowledge knows no bounds."));
        out.add(new Line(g.get(1), STATUE + "Prove your knowledge by answering 3 questions and I shall reward you in ways that transcend time!"));
        out.add(new Line(g.get(2), STATUE + "Answer incorrectly, and your moment of ineptitude will live on for generations."));
        out.addAll(ask(Pace.AFTER_INTRO));
        return out;
    }

    /** The question as Hypixel sends it: one burst of chat lines on the same tick (the first of them after delay). */
    private List<Line> ask(int delay) {
        Question q = questions.get(current);
        List<Line> out = new ArrayList<>();
        out.add(new Line(delay, ""));
        out.add(new Line(0, "§f" + " ".repeat(32) + "§r§6§lQuestion #" + (current + 1)));
        for (String row : wrap(q.text)) out.add(new Line(0, "§f" + " ".repeat(Math.max(1, CENTRE - row.length() / 2)) + "§r§6" + row));
        out.add(new Line(0, ""));
        for (int i = 0; i < q.options.size(); i++) out.add(new Line(0, "    §6 " + LETTERS.get(i) + " §a" + q.options.get(i)));
        out.add(new Line(0, ""));
        return out;
    }

    /** Someone pressed a button of option (0..2). Wrong: the quiz is over (the glue starts it again). */
    public Outcome answer(int option, String player) {
        if (phase != Phase.ASKING) return new Outcome(false, List.of(), false);
        Question q = questions.get(current);
        if (option != q.correct) {
            phase = Phase.IDLE;
            return new Outcome(false, List.of(
                    new Line(0, STATUE + "§r§4Y§r§ci§r§6k§r§ee§r§as"),
                    new Line(Pace.YIKES_TO_WRONG, STATUE + "§r§b" + player + " §r§cchose the wrong answer! I shall never forget this moment of misrememberance.")), false);
        }
        int n = current + 1;
        if (n == questions.size()) {
            phase = Phase.SOLVED;
            return new Outcome(true, List.of(
                    new Line(0, STATUE + "§r§b" + player + " §r§aanswered the final question correctly!"),
                    new Line(Pace.FINAL_TO_BESTOW, STATUE + "I bestow upon you all the power of a hundred years!")), true);
        }
        current = n;
        String remark = n == 1 ? "2 questions left... Then you will have proven your worth to me!" : "One more question!";
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(0, STATUE + "§r§b" + player + " §r§aanswered §r§6Question #" + n + " §r§acorrectly!"));
        lines.add(new Line(Pace.ANSWER_TO_REMARK, STATUE + remark));
        lines.addAll(ask(Pace.REMARK_TO_QUESTION));
        return new Outcome(true, lines, false);
    }

    /** What the statue says to a button pressed while it is busy, or after the quiz is over. */
    public String rebuke(KRandom rnd) { return STATUE + REBUKES.get(rnd.nextInt(REBUKES.size())); }

    /** Back to the start (a wrong answer, the reset). */
    public void reset() { phase = Phase.IDLE; current = 0; }

    public static final String STATUE = "§4[STATUE] Oruo the Omniscient§r§f: ";
    private static final List<String> LETTERS = List.of("ⓐ", "ⓑ", "ⓒ");

    /** The column the centred lines are centred on. */
    private static final int CENTRE = 37;

    /** A question longer than this is cut into two centred lines. */
    private static final int WRAP_AT = 60;

    private static List<String> wrap(String text) {
        if (text.length() <= WRAP_AT) return List.of(text);
        int mid = text.length() / 2;
        int cut = -1;
        for (int i = mid; i >= 1; i--) if (text.charAt(i) == ' ') { cut = i; break; }
        if (cut < 0) return List.of(text);
        return List.of(text.substring(0, cut), text.substring(cut + 1));
    }

    private static final List<String> REBUKES = List.of(
            "You've already proven enough to me! No need to press more of my buttons!",
            "Enough! My buttons are not to be pressed with such lack of grace!",
            "I've had enough of you and your party fiddling with my buttons. Scram!",
            "You have already shown your vast knowledge of SkyBlock. Come back in another life to answer more questions!");

    /** Odin's formula for the SkyBlock year question (it is time dependent). */
    public static int skyblockYear(long nowMillis) { return (int) (((nowMillis / 1000) - 1560276000L) / 446400L) + 1; }

    private static String cluster(String q) {
        String[] parts = q.split(" ", -1);
        int n = Math.min(4, parts.length);
        return String.join(" ", java.util.Arrays.copyOf(parts, n)).toLowerCase(Locale.ROOT);
    }

    /** Builds the three questions of one room from the Odin table plus the year question. */
    public static QuizLogic draw(KRandom rnd, long nowMillis, Map<String, List<String>> table) {
        // Variants of one question (Spiders Den / Spider's Den) share answers: ask each only once.
        List<Pair<String, List<String>>> unique = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        for (Map.Entry<String, List<String>> e : table.entrySet()) {
            List<String> sorted = new ArrayList<>(e.getValue());
            sorted.sort(null);
            if (!seen.add(sorted)) continue;
            unique.add(Pair.of(e.getKey(), e.getValue()));
        }
        unique.add(Pair.of(YEAR, List.of("Year " + skyblockYear(nowMillis))));
        List<Pair<String, List<String>>> shuffled = new ArrayList<>(unique);
        KRandom.shuffle(shuffled, rnd);
        List<Question> qs = new ArrayList<>();
        for (Pair<String, List<String>> p : shuffled.subList(0, Math.min(3, shuffled.size()))) qs.add(build(rnd, p.first, p.second, table, nowMillis));
        return new QuizLogic(qs);
    }

    public static QuizLogic draw(KRandom rnd, long nowMillis) { return draw(rnd, nowMillis, OdinTables.quiz()); }

    public static QuizLogic draw(KRandom rnd) { return draw(rnd, System.currentTimeMillis(), OdinTables.quiz()); }

    public static final String YEAR = "What SkyBlock year is it?";

    private static Question build(KRandom rnd, String text, List<String> answers, Map<String, List<String>> table, long nowMillis) {
        String right = answers.get(rnd.nextInt(answers.size()));
        List<String> wrong;
        if (text.equals(YEAR)) {
            int y = skyblockYear(nowMillis);
            int prev = Math.max(1, y - 1);
            if (prev == y) prev = y + 2;
            wrong = List.of("Year " + (y + 1), "Year " + prev);
        } else {
            String cl = cluster(text);
            LinkedHashSet<String> nearSet = new LinkedHashSet<>();
            LinkedHashSet<String> anySet = new LinkedHashSet<>();
            for (Map.Entry<String, List<String>> e : table.entrySet()) {
                if (e.getKey().equals(text)) continue;
                if (cluster(e.getKey()).equals(cl)) nearSet.addAll(e.getValue());
                anySet.addAll(e.getValue());
            }
            List<String> near = new ArrayList<>();
            for (String o : nearSet) if (fine(o, answers)) near.add(o);
            List<String> any = new ArrayList<>();
            for (String o : anySet) if (fine(o, answers)) any.add(o);
            KRandom.shuffle(near, rnd);
            KRandom.shuffle(any, rnd);
            LinkedHashSet<String> both = new LinkedHashSet<>(near);
            both.addAll(any);
            List<String> w = new ArrayList<>();
            for (String o : both) { if (w.size() >= 2) break; w.add(o); }
            wrong = w;
        }
        List<String> options = new ArrayList<>(wrong);
        options.add(right);
        KRandom.shuffle(options, rnd);
        return new Question(text, options, options.indexOf(right));
    }

    /** Odin marks an option right when it ENDS WITH an accepted answer, so no wrong option may end with one. */
    private static boolean fine(String o, List<String> answers) {
        for (String a : answers) if (o.endsWith(a)) return false;
        return true;
    }
}
