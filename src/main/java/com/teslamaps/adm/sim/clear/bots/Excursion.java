// Ported from ADM's p3sim/clear/bots/BotExcursion.kt
package com.teslamaps.adm.sim.clear.bots;

/** A short trip off the plan: a bot walks to a key stand, waits there for the key to be taken, and walks back. Pure. */
public class Excursion {
    private enum Phase { OUT, HOLD, BACK }

    public final Pt stand;
    private final int holdTicks;
    private final double speed;

    private Phase phase = Phase.OUT;
    private int held = 0;

    public Excursion(Pt stand, int holdTicks, double speed) {
        this.stand = stand;
        this.holdTicks = holdTicks;
        this.speed = speed;
    }

    public Excursion(Pt stand, int holdTicks) { this(stand, holdTicks, BotTuning.DEFAULT_PLAN_BPT); }

    /** Ends the wait now (the key has been taken); the bot starts back. */
    public void release() { if (phase != Phase.BACK) phase = Phase.BACK; }

    /** The bot is at the stand and waiting. */
    public boolean waiting() { return phase == Phase.HOLD; }

    /** Where the bot is after one more tick, from [cur]; [home] is where the plan has it now. Null when it is back. */
    public Pt step(Pt cur, Pt home) {
        if (phase == Phase.OUT) {
            Pt next = toward(cur, stand);
            if (next.dist(stand) < 0.05) phase = Phase.HOLD;
            return next;
        }
        if (phase == Phase.HOLD) {
            if (++held > holdTicks) phase = Phase.BACK;
            return cur;
        }
        return cur.dist(home) <= speed ? null : toward(cur, home);
    }

    private Pt toward(Pt a, Pt b) {
        double d = a.dist(b);
        return d <= speed ? b : a.lerp(b, speed / d);
    }
}
