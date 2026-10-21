// Ported from ADM's p3sim/clear/puzzles/PuzzleDraw.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Which puzzles a dungeon gets, when the sim has to decide it. Measured on 579 full maps. Pure; the random source is a parameter. */
public final class PuzzleDraw {
    private PuzzleDraw() {}

    /** The measured counts out of 579 maps. */
    public static final Map<Integer, Integer> DEFAULT_COUNTS;

    static {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        m.put(2, 166);
        m.put(3, 181);
        m.put(4, 146);
        m.put(5, 86);
        DEFAULT_COUNTS = m;
    }

    /** One room design a puzzle can be built in. */
    public static class Design {
        public final String code;
        public final String room;

        public Design(String code, String room) {
            this.code = code;
            this.room = room;
        }
    }

    /** The ten F7 designs (11 with the two Blaze rooms; Bomb Defuse never occurs). */
    public static final List<Design> DEFAULT_DESIGNS = List.of(
            new Design(PuzzleKinds.BOULDER, "Boulder"), new Design(PuzzleKinds.BEAMS, "Creeper Beams"), new Design(PuzzleKinds.TTT, "Tic Tac Toe"),
            new Design(PuzzleKinds.BLAZE, "Higher Blaze"), new Design(PuzzleKinds.BLAZE, "Lower Blaze"), new Design(PuzzleKinds.WATER_BOARD, "Water Board"),
            new Design(PuzzleKinds.WEIRDOS, "Three Weirdos"), new Design(PuzzleKinds.TP_MAZE, "Teleport Maze"), new Design(PuzzleKinds.QUIZ, "Quiz"),
            new Design(PuzzleKinds.ICE_FILL, "Ice Fill"), new Design(PuzzleKinds.ICE_PATH, "Ice Path"));

    /** Draws the number of puzzle rooms by the weights in counts. */
    public static int drawCount(KRandom rnd, Map<Integer, Integer> counts) {
        TreeMap<Integer, Integer> usable = new TreeMap<>();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) if (e.getValue() > 0) usable.put(e.getKey(), e.getValue());
        if (usable.isEmpty()) return DEFAULT_COUNTS.keySet().iterator().next();
        int sum = 0;
        for (int w : usable.values()) sum += w;
        int roll = rnd.nextInt(sum);
        for (Map.Entry<Integer, Integer> e : usable.entrySet()) {
            int w = e.getValue();
            if (roll < w) return e.getKey();
            roll -= w;
        }
        return usable.lastKey();
    }

    public static int drawCount(KRandom rnd) { return drawCount(rnd, DEFAULT_COUNTS); }

    /** Draws n distinct designs; the two Blaze rooms are never both taken. */
    public static List<Design> drawDesigns(KRandom rnd, int n, List<Design> designs) {
        List<Design> picked = new ArrayList<>();
        List<Design> shuffled = new ArrayList<>(designs);
        KRandom.shuffle(shuffled, rnd);
        for (Design d : shuffled) {
            if (picked.size() >= n) break;
            boolean taken = false;
            for (Design p : picked) if (p.code.equals(d.code)) { taken = true; break; }
            if (taken) continue;
            picked.add(d);
        }
        return picked;
    }

    public static List<Design> drawDesigns(KRandom rnd, int n) { return drawDesigns(rnd, n, DEFAULT_DESIGNS); }

    /** The whole draw: a count, then that many designs. */
    public static List<Design> draw(KRandom rnd, Map<Integer, Integer> counts, List<Design> designs) {
        return drawDesigns(rnd, drawCount(rnd, counts), designs);
    }

    public static List<Design> draw(KRandom rnd, Map<Integer, Integer> counts) { return draw(rnd, counts, DEFAULT_DESIGNS); }

    public static List<Design> draw(KRandom rnd) { return draw(rnd, DEFAULT_COUNTS, DEFAULT_DESIGNS); }
}
