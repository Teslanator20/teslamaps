// Ported from ADM's p3sim/clear/score/ScoreFormula.kt
package com.teslamaps.adm.sim.clear.score;

import java.util.Objects;

public final class ScoreInputs {
    public final int completedTiles;
    public final int secretsFound;
    public final int secretsTotal;
    public final int crypts;
    public final boolean mimic;
    public final boolean prince;
    public final boolean bat;
    public final int deaths;
    public final int unsolvedPuzzles;
    public final double elapsedSec;

    public ScoreInputs(int completedTiles, int secretsFound, int secretsTotal, int crypts, boolean mimic, boolean prince, boolean bat,
                       int deaths, int unsolvedPuzzles, double elapsedSec) {
        this.completedTiles = completedTiles;
        this.secretsFound = secretsFound;
        this.secretsTotal = secretsTotal;
        this.crypts = crypts;
        this.mimic = mimic;
        this.prince = prince;
        this.bat = bat;
        this.deaths = deaths;
        this.unsolvedPuzzles = unsolvedPuzzles;
        this.elapsedSec = elapsedSec;
    }

    @Override public boolean equals(Object o) {
        return o instanceof ScoreInputs i && completedTiles == i.completedTiles && secretsFound == i.secretsFound && secretsTotal == i.secretsTotal
                && crypts == i.crypts && mimic == i.mimic && prince == i.prince && bat == i.bat && deaths == i.deaths
                && unsolvedPuzzles == i.unsolvedPuzzles && Double.compare(elapsedSec, i.elapsedSec) == 0;
    }
    @Override public int hashCode() { return Objects.hash(completedTiles, secretsFound, secretsTotal, crypts, mimic, prince, bat, deaths, unsolvedPuzzles, elapsedSec); }
    @Override public String toString() {
        return "ScoreInputs(completedTiles=" + completedTiles + ", secretsFound=" + secretsFound + ", secretsTotal=" + secretsTotal + ", crypts=" + crypts
                + ", mimic=" + mimic + ", prince=" + prince + ", bat=" + bat + ", deaths=" + deaths + ", unsolvedPuzzles=" + unsolvedPuzzles
                + ", elapsedSec=" + elapsedSec + ")";
    }
}
