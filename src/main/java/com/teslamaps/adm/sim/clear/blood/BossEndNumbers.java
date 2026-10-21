// Ported from ADM's p3sim/clear/blood/BossEndStats.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.clear.score.EndScreenText;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.Runs;
import com.teslamaps.adm.sim.run.api.EndScreen;
import com.teslamaps.adm.sim.run.api.Services;
import java.util.Locale;
import java.util.Objects;

/** The numbers the boss's end-of-run chat shows when the boss was reached through a full run. */
public final class BossEndNumbers {
    public final int score;
    /** `S+`, `S`, `A`, `B`, `C`, `D`. */
    public final String letter;
    /** Run ticks from T0 to the end. */
    public final int ticks;
    public final int secretsFound;
    public final int secretsTotal;
    public final int deaths;
    public final int crypts;

    public BossEndNumbers(int score, String letter, int ticks, int secretsFound, int secretsTotal, int deaths, int crypts) {
        this.score = score;
        this.letter = letter;
        this.ticks = ticks;
        this.secretsFound = secretsFound;
        this.secretsTotal = secretsTotal;
        this.deaths = deaths;
        this.crypts = crypts;
    }

    public String scoreLine() { return "Team Score: §r§a" + score + " §r§f(§r" + gradeColour(letter) + letter + "§r§f)"; }

    /** Null if no time is known. */
    public String defeatedLine() {
        return ticks > 0 ? "§r§c☠ §r§eDefeated §r§cMaxor, Storm, Goldor, and Necron §r§ein §r§a" + timeText(ticks) : null;
    }

    public static String gradeColour(String letter) { return EndScreenText.letterColour(letter); }

    /** `05m 12s`. */
    public static String timeText(int ticks) { return String.format(Locale.ROOT, "%02dm %02ds", ticks / 1200, ticks / 20 % 60); }

    public static BossEndNumbers from(EndScreen e) { return new BossEndNumbers(e.score, e.letter, e.ticks, e.secretsFound, e.secretsTotal, e.deaths, e.crypts); }

    /** The numbers of run as the score module has them now, or null with no run. */
    public static BossEndNumbers of(Run run) { return run != null ? from(Services.score().endScreen(run)) : null; }

    public static BossEndNumbers ofCurrentRun() { return of(Runs.current); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BossEndNumbers x && score == x.score && Objects.equals(letter, x.letter) && ticks == x.ticks && secretsFound == x.secretsFound
                && secretsTotal == x.secretsTotal && deaths == x.deaths && crypts == x.crypts;
    }
    @Override public int hashCode() { return Objects.hash(score, letter, ticks, secretsFound, secretsTotal, deaths, crypts); }
    @Override public String toString() {
        return "BossEndNumbers(score=" + score + ", letter=" + letter + ", ticks=" + ticks + ", secretsFound=" + secretsFound + ", secretsTotal=" + secretsTotal
                + ", deaths=" + deaths + ", crypts=" + crypts + ")";
    }
}
