// Ported from ADM's p3sim/clear/puzzles/BlazeLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Higher Or Lower as a state machine: ten blazes killed in order of health ("Higher" lowest up, "Lower" highest down, as Odin's
 * BlazeSolver sorts). A kill out of order fails the puzzle; the last kill solves it. Pure: no Minecraft.
 */
public class BlazeLogic {
    public final List<Integer> hps;
    public final boolean ascending;
    private final List<Integer> alive;
    public boolean failed = false;

    public BlazeLogic(List<Integer> hps, boolean ascending) {
        this.hps = hps;
        this.ascending = ascending;
        this.alive = new ArrayList<>(hps);
    }

    /** The health of the blaze to kill next, or null when none is left. */
    public Integer next() {
        if (alive.isEmpty()) return null;
        int best = alive.get(0);
        for (int h : alive) if (ascending ? h < best : h > best) best = h;
        return best;
    }

    public boolean solved() { return !hps.isEmpty() && alive.isEmpty() && !failed; }

    public int remaining() { return alive.size(); }

    /** A blaze with hp died. True when it was the right one; false fails the puzzle. */
    public boolean kill(int hp) {
        if (failed || !alive.contains(hp)) return !failed;
        Integer n = next();
        if (n == null || hp != n) { failed = true; alive.remove(Integer.valueOf(hp)); return false; }
        alive.remove(Integer.valueOf(hp));
        return true;
    }

    public void reset() { alive.clear(); alive.addAll(hps); failed = false; }

    public static final int COUNT = 10;

    /** The lowest health of a set is between these (recorded sets: 1,002 to 1,993). */
    public static final int LOWEST_MIN = 1000;
    public static final int LOWEST_MAX = 2000;

    /** Each next health is 1 to this above the one before. */
    public static final int STEP_MAX = 450;

    /** The level in the name tag (16 of 22 recorded rooms say 11, the rest 15). */
    public static final int LEVEL = 11;

    /** Ten distinct health values like the real room's, returned in random order. */
    public static List<Integer> drawHps(KRandom rnd, int count) {
        int hp = rnd.nextInt(LOWEST_MIN, LOWEST_MAX + 1);
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) { out.add(hp); hp += rnd.nextInt(1, STEP_MAX + 1); }
        List<Integer> shuffled = new ArrayList<>(out);
        KRandom.shuffle(shuffled, rnd);
        return shuffled;
    }

    public static List<Integer> drawHps(KRandom rnd) { return drawHps(rnd, COUNT); }

    /** A replay's set: the HP of the recorded blaze standing on each cell, or null unless every cell has one and they all differ. */
    public static List<Integer> recordedHps(List<int[]> cells, List<double[]> rec) {
        List<Integer> out = new ArrayList<>();
        for (int[] c : cells) {
            double[] hit = null;
            for (double[] it : rec) {
                if (Math.abs(it[0] - c[0]) < 0.5 && Math.abs(it[1] - c[1]) < 0.5 && Math.abs(it[2] - c[2]) < 0.5) { hit = it; break; }
            }
            if (hit == null) return null;
            out.add((int) hit[3]);
        }
        return new HashSet<>(out).size() == out.size() ? out : null;
    }

    /** The text of the name tag Odin reads from a blaze's armor stand: `[Lv11]` + icon + ` Blaze 3,261/3,261❤`. */
    public static String nameOf(int hp) {
        String s = String.format(Locale.ROOT, "%,d", hp);
        return "§8[§7Lv" + LEVEL + "§8] §4" + ICON + " §cBlaze §a" + s + "§f/§a" + s + "§c❤";
    }

    /** The blaze icon in the name tag (U+E07C, drawn by Hypixel's resource pack). */
    public static final String ICON = "";
}
