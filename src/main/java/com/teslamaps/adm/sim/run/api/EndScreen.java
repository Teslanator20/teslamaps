// Ported from ADM's p3sim/run/api/SecretsApi.kt
package com.teslamaps.adm.sim.run.api;

import java.util.Objects;

public final class EndScreen {
    public final int score;
    public final String letter;
    public final int clearedPct;
    public final int secretsFound;
    public final int secretsTotal;
    public final int crypts;
    public final int deaths;
    public final int ticks;

    public EndScreen(int score, String letter, int clearedPct, int secretsFound, int secretsTotal, int crypts, int deaths, int ticks) {
        this.score = score;
        this.letter = letter;
        this.clearedPct = clearedPct;
        this.secretsFound = secretsFound;
        this.secretsTotal = secretsTotal;
        this.crypts = crypts;
        this.deaths = deaths;
        this.ticks = ticks;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof EndScreen x && Objects.equals(score, x.score) && Objects.equals(letter, x.letter) && Objects.equals(clearedPct, x.clearedPct) && Objects.equals(secretsFound, x.secretsFound) && Objects.equals(secretsTotal, x.secretsTotal) && Objects.equals(crypts, x.crypts) && Objects.equals(deaths, x.deaths) && Objects.equals(ticks, x.ticks);
    }
    @Override public int hashCode() { return Objects.hash(score, letter, clearedPct, secretsFound, secretsTotal, crypts, deaths, ticks); }
    @Override public String toString() { return "EndScreen(" + "score=" + score + ", " + "letter=" + letter + ", " + "clearedPct=" + clearedPct + ", " + "secretsFound=" + secretsFound + ", " + "secretsTotal=" + secretsTotal + ", " + "crypts=" + crypts + ", " + "deaths=" + deaths + ", " + "ticks=" + ticks + ")"; }
}
