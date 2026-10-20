package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Ported from ADM's splits/StormDetail.kt
public final class StormDetail {

    public record Moment(int tick, String text) {}

    public static final String STORM_START = "[BOSS] Storm: Pathetic Maxor, just like expected.";
    public static final String HAPPY = "[BOSS] Storm: I'd be happy to show you what that's like!";
    public static final String ENRAGED = "⚠ Storm is enraged! ⚠";
    public static final String STORM_DEAD = "[BOSS] Storm: I should have known that I stood no chance.";
    public static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";
    public static final Set<String> LIGHTNING = Set.of("[BOSS] Storm: ENERGY HEED MY CALL!", "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!");
    public static final Set<String> CRUSHED = Set.of("[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!");

    private static final int LIGHTNING_STRIKE = 520;
    private static final int STRIKE_AFTER_HAPPY = 33;
    private static final int PURPLE_AFTER_LIGHTNING = 109;
    private static final int YELLOW_AFTER_ENRAGE = 87;
    private static final int YELLOW_AFTER_ENRAGE_M7 = 84;
    private static final int CHECK_SLACK = 5;
    private static final int STEP_SPAN = 18;
    private static final double SPOT_X = 102.375, SPOT_Z = 52.375;
    private static final double YELLOW_X = 46.0, YELLOW_Z = 65.0, YELLOW_REACHED = 2.4;

    private final List<Moment> moments = new ArrayList<>();
    private Integer start, happy, lightning, enraged, crushAt;
    private boolean strikeShown, leftSpot, reachedYellow, m7, ended, dead;
    private int crushes;
    private double[] last;
    private final Map<StormCrush.Pillar, Integer> stepStart = new HashMap<>();
    private final Map<StormCrush.Pillar, Integer> reads = new HashMap<>();

    public void reset() {
        moments.clear(); start = null; happy = null; strikeShown = false; lightning = null; enraged = null;
        leftSpot = false; reachedYellow = false; crushes = 0; ended = false; dead = false; crushAt = null; last = null;
        stepStart.clear(); reads.clear();
    }

    public Integer start() { return start; }
    public boolean active() { return start != null && !ended; }
    public boolean checking() { return active() && lightning != null && !dead; }
    public Integer phase(int tick) { return start == null ? null : tick - start; }

    public List<Moment> moments() {
        List<Moment> out = new ArrayList<>(moments);
        out.sort(Comparator.comparingInt(Moment::tick));
        return out;
    }

    public void onChat(String msg, int tick, boolean isM7) {
        if (msg.equals(STORM_START)) { reset(); start = tick - 1; m7 = isM7; return; }
        Integer t = phase(tick);
        if (t == null || ended) return;
        if (msg.equals(HAPPY)) { if (happy == null) happy = tick; }
        else if (LIGHTNING.contains(msg)) {
            if (lightning == null) { lightning = t; add(t, "§eLightning line"); }
        } else if (CRUSHED.contains(msg)) onCrush(t);
        else if (msg.equals(ENRAGED)) {
            if (enraged == null) { enraged = t; add(t, "§bBroke free §8(pinned " + secs(t - (crushAt != null ? crushAt : t)) + ")"); }
        } else if (msg.equals(STORM_DEAD)) { dead = true; add(t, "§aDead"); }
        else if (msg.equals(GOLDOR_START)) ended = true;
    }

    private void onCrush(int t) {
        crushes++;
        crushAt = t;
        StormCrush.Pillar pillar = last == null ? null : StormCrush.nearest(last[0], last[2]);
        int check = Math.floorDiv(t + CHECK_SLACK, StormCrush.CHECK_PERIOD) * StormCrush.CHECK_PERIOD;
        Integer earliest = null;
        if (crushes == 1 && lightning != null) earliest = firstCheck(lightning + PURPLE_AFTER_LIGHTNING);
        else if (crushes == 2 && enraged != null) earliest = firstCheck(enraged + (m7 ? YELLOW_AFTER_ENRAGE_M7 : YELLOW_AFTER_ENRAGE));
        String timing = "";
        if (earliest != null) {
            int late = (check - earliest) / StormCrush.CHECK_PERIOD;
            if (late <= 0) timing = " §a(first check)";
            else if (late == 1) timing = " §e(1 check late)";
            else timing = " §c(" + late + " checks late)";
        }
        String by = pillar != null ? " by " + pillar.colour() + pillar.name() : "";
        add(t, "§6Crushed" + by + " §8t" + check + timing);
    }

    public void onServerTick(int tick) {
        Integer t = phase(tick);
        if (t == null || ended || strikeShown) return;
        Integer strike = happy != null ? happy - start + STRIKE_AFTER_HAPPY : null;
        int at = strike != null ? strike : LIGHTNING_STRIKE;
        if (t >= at) {
            strikeShown = true;
            add(at, "§eGiga Lightning" + (strike == null ? " §8(~t" + LIGHTNING_STRIKE + ")" : ""));
        }
    }

    public void onPosition(int tick, double x, double y, double z) {
        Integer t = phase(tick);
        if (t == null) return;
        last = new double[]{x, y, z};
        if (ended) return;
        if (!leftSpot && lightning != null && Math.hypot(x - SPOT_X, z - SPOT_Z) > 0.5) {
            leftSpot = true;
            add(t, "§bLeft his spot §8(" + secs(t - lightning) + " after the line)");
        }
        if (!reachedYellow && enraged != null && Math.hypot(x - YELLOW_X, z - YELLOW_Z) <= YELLOW_REACHED) {
            reachedYellow = true;
            add(t, "§eReached Yellow");
        }
    }

    public void onPillarStep(StormCrush.Pillar pillar, int tick) {
        Integer t = phase(tick);
        if (t == null || ended) return;
        Integer prev = stepStart.get(pillar);
        if (prev != null && tick - prev < STEP_SPAN) return;
        stepStart.put(pillar, tick);
        int n = reads.getOrDefault(pillar, 0) + 1;
        reads.put(pillar, n);
        add(t, pillar.colour() + pillar.name() + " pad §7read " + n);
    }

    public void onCheck(int tick, StormCrush.Verdict v, boolean armed) {
        Integer t = phase(tick);
        if (t == null || ended) return;
        String side = (v.inset() >= 0 ? "§a" : "§c") + fmt(Math.abs(v.inset())) + (v.inset() >= 0 ? " in" : " out");
        String head = v.head() == null ? "§7no pillar"
                : (v.head() >= 0 ? "§a" : "§c") + "head " + (v.head() >= 0 ? "+" : "-") + fmt(Math.abs(v.head()));
        add(t, "§8check " + v.pillar().colour() + v.pillar().name() + " " + side + " §7· " + head + (armed ? "" : " §8(not lowered)"));
    }

    private void add(int t, String text) { moments.add(new Moment(t, text)); }

    private static int firstCheck(int t) {
        return Math.floorDiv(t + StormCrush.CHECK_PERIOD - 1, StormCrush.CHECK_PERIOD) * StormCrush.CHECK_PERIOD;
    }

    public static String secs(int ticks) { return String.format(Locale.ROOT, "%.2fs", ticks / 20.0); }

    static String fmt(double d) { return String.format(Locale.ROOT, "%.2f", d); }
}
