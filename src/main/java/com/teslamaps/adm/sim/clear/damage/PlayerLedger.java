package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/PlayerLedger.kt

import com.teslamaps.adm.sim.run.PlayerRecord;

public final class PlayerLedger {
    private PlayerLedger() {}

    public static final double FULL_BAR = 40.0;
    public static final int IMMUNITY_TICKS = 10;
    public static final int REVIVE_TICKS = 120;
    public static final double NO_DEATH_FLOOR = 100.0 / FULL_BAR;
    public static final double SAVED_PERCENT = 100.0;
    public static final double REVIVED_PERCENT = 100.0;

    public enum Outcome {
        IGNORED,
        HURT,
        HELD,
        LETHAL,
    }

    public static class Hit {
        public final Outcome outcome;
        public final double applied;

        public Hit(Outcome outcome, double applied) {
            this.outcome = outcome;
            this.applied = applied;
        }
    }

    public static double vanilla(double percent) { return Math.clamp(FULL_BAR * percent / 100.0, 0.5, FULL_BAR); }

    public static boolean immune(PlayerRecord rec, int t) {
        return rec.lastHitT != Integer.MIN_VALUE && ((long) t - rec.lastHitT) < IMMUNITY_TICKS;
    }

    public static Hit hit(PlayerRecord rec, double percent, int t, boolean noDeaths) { return hit(rec, percent, t, noDeaths, IMMUNITY_TICKS); }

    public static Hit hit(PlayerRecord rec, double percent, int t, boolean noDeaths, int immunityTicks) {
        if (rec.ghost || percent <= 0.0 || Double.isNaN(percent)) return new Hit(Outcome.IGNORED, 0.0);
        if (rec.lastHitT != Integer.MIN_VALUE && ((long) t - rec.lastHitT) < immunityTicks) return new Hit(Outcome.IGNORED, 0.0);
        double after = rec.hpPercent - percent;
        if (after <= 0.0) {
            if (noDeaths) {
                double taken = Math.max(rec.hpPercent - NO_DEATH_FLOOR, 0.0);
                rec.hpPercent = Math.min(rec.hpPercent, NO_DEATH_FLOOR);
                rec.lastHitT = t;
                return new Hit(Outcome.HELD, taken);
            }
            rec.lastHitT = t;
            return new Hit(Outcome.LETHAL, rec.hpPercent);
        }
        rec.hpPercent = after;
        rec.lastHitT = t;
        return new Hit(Outcome.HURT, percent);
    }

    public static void saved(PlayerRecord rec) { rec.hpPercent = SAVED_PERCENT; }

    public static void die(PlayerRecord rec, int t) {
        rec.hpPercent = 0.0;
        rec.ghost = true;
        rec.deaths++;
        rec.reviveAtT = t + REVIVE_TICKS;
    }

    public static boolean reviveDue(PlayerRecord rec, int t) {
        return rec.ghost && (rec.reviveAtT != null && t >= rec.reviveAtT);
    }

    public static void revive(PlayerRecord rec) {
        rec.ghost = false;
        rec.hpPercent = REVIVED_PERCENT;
        rec.reviveAtT = null;
    }
}
