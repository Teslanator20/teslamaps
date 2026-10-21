// Ported from ADM's p3sim/clear/score/ScoreFormula.kt
package com.teslamaps.adm.sim.clear.score;

public final class Grades {
    private Grades() {}

    public static String letter(int score) {
        if (score >= 300) return "S+";
        if (score >= 270) return "S";
        if (score >= 230) return "A";
        if (score >= 160) return "B";
        if (score >= 100) return "C";
        return "D";
    }
}
