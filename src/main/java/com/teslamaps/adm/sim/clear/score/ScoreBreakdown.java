// Ported from ADM's p3sim/clear/score/ScoreFormula.kt
package com.teslamaps.adm.sim.clear.score;

import java.util.Objects;

public final class ScoreBreakdown {
    public final double rooms;
    public final double secrets;
    public final double crypts;
    public final double bonus;
    public final double puzzles;
    public final double deaths;
    public final double speed;
    public final double constant;
    public final int total;

    public ScoreBreakdown(double rooms, double secrets, double crypts, double bonus, double puzzles, double deaths, double speed, double constant, int total) {
        this.rooms = rooms;
        this.secrets = secrets;
        this.crypts = crypts;
        this.bonus = bonus;
        this.puzzles = puzzles;
        this.deaths = deaths;
        this.speed = speed;
        this.constant = constant;
        this.total = total;
    }

    @Override public boolean equals(Object o) {
        return o instanceof ScoreBreakdown b && Double.compare(rooms, b.rooms) == 0 && Double.compare(secrets, b.secrets) == 0
                && Double.compare(crypts, b.crypts) == 0 && Double.compare(bonus, b.bonus) == 0 && Double.compare(puzzles, b.puzzles) == 0
                && Double.compare(deaths, b.deaths) == 0 && Double.compare(speed, b.speed) == 0 && Double.compare(constant, b.constant) == 0
                && total == b.total;
    }
    @Override public int hashCode() { return Objects.hash(rooms, secrets, crypts, bonus, puzzles, deaths, speed, constant, total); }
    @Override public String toString() {
        return "ScoreBreakdown(rooms=" + rooms + ", secrets=" + secrets + ", crypts=" + crypts + ", bonus=" + bonus + ", puzzles=" + puzzles
                + ", deaths=" + deaths + ", speed=" + speed + ", constant=" + constant + ", total=" + total + ")";
    }
}
