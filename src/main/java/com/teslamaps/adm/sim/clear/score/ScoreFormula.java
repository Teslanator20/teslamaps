// Ported from ADM's p3sim/clear/score/ScoreFormula.kt
package com.teslamaps.adm.sim.clear.score;

import com.teslamaps.adm.sim.pack.runstate.ScoreModel;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

public final class ScoreFormula {
    private ScoreFormula() {}

    public static final double UNMEASURED_PUZZLE_PENALTY = 10.0;

    public static double secretsPct(int found, int total) { return total <= 0 ? 0.0 : Math.round(1000.0 * found / total) / 10.0; }

    public static int deathPenalty(int deaths) { return Math.max(0, 2 * deaths - 1); }

    public static double interp(double[] table, double c) {
        if (table.length == 0) return 0.0;
        if (c <= 0) return table[0];
        if (c >= table.length - 1) return table[table.length - 1];
        int i = (int) Math.floor(c);
        return table[i] + (table[i + 1] - table[i]) * (c - i);
    }

    public static double speed(List<Pair<Double, Double>> points, double sec) {
        if (points.isEmpty() || sec <= points.get(0).first) return 0.0;
        for (int i = 1; i < points.size(); i++) {
            double s0 = points.get(i - 1).first; double p0 = points.get(i - 1).second;
            double s1 = points.get(i).first; double p1 = points.get(i).second;
            if (sec <= s1) return s1 <= s0 ? p1 : p0 + (p1 - p0) * (sec - s0) / (s1 - s0);
        }
        return points.get(points.size() - 1).second;
    }

    public static ScoreBreakdown fitted(ScoreModel m, ScoreInputs i) {
        double pct = secretsPct(i.secretsFound, i.secretsTotal);
        double secrets = m.secretWeight * Math.floor(m.maxSecretPoints * pct / 100.0);
        double rooms = interp(m.roomsTable, i.completedTiles);
        double crypts = m.cryptWeight * Math.min(m.cryptCap, i.crypts);
        double bonus = m.mimicWeight * (i.mimic ? 1 : 0) + m.princeWeight * (i.prince ? 1 : 0) + m.batWeight * (i.bat ? 1 : 0);
        double puzzles = (m.puzzlePenaltyPerUnsolved != null ? m.puzzlePenaltyPerUnsolved : -UNMEASURED_PUZZLE_PENALTY) * i.unsolvedPuzzles;
        double deaths = -(double) deathPenalty(i.deaths);
        double speed = speed(m.speedPointsSec, i.elapsedSec);
        double sum = rooms + secrets + crypts + bonus + puzzles + deaths + speed + m.constant;
        return new ScoreBreakdown(rooms, secrets, crypts, bonus, puzzles, deaths, speed, m.constant, Math.max(0, (int) Math.floor(sum)));
    }

    public static ScoreBreakdown published(ScoreInputs i) {
        double pct = secretsPct(i.secretsFound, i.secretsTotal);
        double secrets = Math.floor(40.0 * pct / 100.0);
        int c = i.completedTiles;
        double speedPart = Math.floor(60.0 * c / 36.0);
        double skill = Math.clamp(20 + Math.floor(80.0 * c / 36.0) - UNMEASURED_PUZZLE_PENALTY * i.unsolvedPuzzles - deathPenalty(i.deaths), 20.0, 100.0);
        double crypts = Math.min(5, i.crypts);
        double bonus = (i.mimic ? 2.0 : 0.0) + (i.prince ? 1.0 : 0.0) + (i.bat ? 1.0 : 0.0);
        double total = secrets + speedPart + skill + crypts + bonus + 100.0;
        return new ScoreBreakdown(speedPart + skill + 100.0, secrets, crypts, bonus, 0.0, 0.0, 0.0, 0.0, Math.max(0, (int) Math.floor(total)));
    }

    public static ScoreBreakdown evaluate(ScoreModel m, ScoreInputs i) {
        return m != null && m.roomsTable.length >= 2 ? fitted(m, i) : published(i);
    }
}
