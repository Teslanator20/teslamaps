// Ported from ADM's p3sim/clear/bots/BotEffects.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;

/** One teammate death, from the first hit to the ghost. Nothing in the world is touched here; World is the module's hands. */
public class TeamDeath {
    /** What the module lets a death do. */
    public interface World {
        /** A living teammate in a room with a living required mob, or null. */
        Integer inFight(KRandom random);

        /** Any living teammate, or null. */
        Integer anyone(KRandom random);

        boolean alive(int seat);

        double hpPercent(int seat);

        /** A hit by a living mob of the seat's room. */
        void hit(int seat, double percent);

        /** The death, blamed on a living mob of the seat's room. */
        void kill(int seat);

        /** The death of a teammate whose connection dropped. Default: an ordinary death. */
        default void disconnect(int seat) { kill(seat); }
    }

    private final int dueT;
    private final KRandom random;

    /** One death in DISCONNECT_SHARE is a dropped connection. */
    private final boolean disconnects;

    private Integer seat = null;
    private int deathAt = Integer.MAX_VALUE;
    private final ArrayList<Integer> hitAt = new ArrayList<>();

    public boolean finished = false;

    public TeamDeath(int dueT, KRandom random, double disconnectShare) {
        this.dueT = dueT;
        this.random = random;
        this.disconnects = disconnectShare > 0.0 && random.nextDouble() < disconnectShare;
    }

    public TeamDeath(int dueT, KRandom random) { this(dueT, random, BotHealth.DISCONNECT_SHARE); }

    /** The tick the death will happen, once the victim is chosen. */
    public Integer diesAt() { return seat == null ? null : deathAt; }

    public void step(int t, World world) {
        if (finished) return;
        if (disconnects) {
            if (t >= dueT) {
                Integer a = world.anyone(random);
                if (a != null) world.disconnect(a);
                finished = true;
            }
            return;
        }
        Integer victim = seat;
        if (victim == null) {
            if (t < dueT - BotHealth.RUN_UP_WINDOW) return;
            Integer pick = world.inFight(random);
            if (pick != null) {
                seat = pick;
                int hits = random.nextInt(2, 5);
                deathAt = Math.max(dueT, t + BotHealth.RUN_UP_GAP * hits + 1);
                for (int j = 1; j <= hits; j++) hitAt.add(deathAt - BotHealth.RUN_UP_GAP * j);
            } else if (t >= dueT + BotHealth.VICTIM_PATIENCE) {
                Integer a = world.anyone(random);
                if (a != null) world.kill(a);
                finished = true;
            }
            return;
        }
        if (!world.alive(victim)) { finished = true; return; }
        boolean any = false;
        for (int h : hitAt) if (h <= t) { any = true; break; }
        if (any) {
            hitAt.removeIf(h -> h <= t);
            double share = BotHealth.RUN_UP_SHARE[0] + random.nextDouble() * (BotHealth.RUN_UP_SHARE[1] - BotHealth.RUN_UP_SHARE[0]);
            world.hit(victim, Math.max(4.0, world.hpPercent(victim) * share));
        }
        if (t >= deathAt) { world.kill(victim); finished = true; }
    }
}
