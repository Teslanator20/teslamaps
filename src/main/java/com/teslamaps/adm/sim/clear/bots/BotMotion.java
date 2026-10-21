// Ported from ADM's p3sim/clear/bots/BotMotion.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** The small life of a teammate's body: swings, the main hand, sprint, crouch, the head. Pure; one instance per run, state per seat. */
public class BotMotion {
    private final KRandom random;

    private static final class State {
        String held;
        int nextItemT = Integer.MIN_VALUE;
        int standStretchEnd = Integer.MIN_VALUE;
        int crouchUntil = Integer.MIN_VALUE;
        int nextLookT = Integer.MIN_VALUE;
        float lookTarget = 0f;
        float head = Float.NaN;
        float body = Float.NaN;
        float lead = 0f;
        boolean swingPending = false;

        State(String held) { this.held = held; }
    }

    private final HashMap<Integer, State> states = new HashMap<>();

    public BotMotion(KRandom random) { this.random = random; }

    /** Starts a seat holding [item]. */
    public void start(int seat, String item) { states.put(seat, new State(item)); }

    /** A swing this tick regardless of the dice (a kill). */
    public void swingNow(int seat) { states.computeIfAbsent(seat, k -> new State(DEFAULT_ITEM)).swingPending = true; }

    /** The item the seat holds now. */
    public String held(int seat) {
        State s = states.get(seat);
        return s != null ? s.held : DEFAULT_ITEM;
    }

    /** One tick for [seat] at run tick [t]. */
    public BodyFrame step(int seat, int t, double speed, float heading, BotActivity activity) {
        State s = states.computeIfAbsent(seat, k -> new State(DEFAULT_ITEM));
        boolean moving = speed > MOVING_ABOVE;
        if (Float.isNaN(s.head)) { s.head = heading; s.body = heading; }

        // the main hand
        String changed = null;
        if (s.nextItemT == Integer.MIN_VALUE) s.nextItemT = t + gap();
        if (t >= s.nextItemT) {
            s.nextItemT = t + gap();
            String pick = pick(activity, random.nextDouble());
            if (!pick.equals(s.held)) { s.held = pick; changed = pick; }
        }

        // swing, sprint, crouch
        boolean swing = s.swingPending || (activity.working() && random.nextDouble() < SWINGS_PER_MINUTE / 1200.0);
        s.swingPending = false;
        boolean sprint = speed > SPRINT_ABOVE;
        boolean crouch = false;
        if (moving) { s.standStretchEnd = Integer.MIN_VALUE; s.crouchUntil = Integer.MIN_VALUE; }
        else {
            if (t >= s.standStretchEnd) {
                s.standStretchEnd = t + random.nextInt(STRETCH_TICKS[0], STRETCH_TICKS[1] + 1);
                s.crouchUntil = random.nextDouble() < CROUCH_SHARE ? s.standStretchEnd : Integer.MIN_VALUE;
            }
            crouch = t < s.crouchUntil;
        }

        // head and body
        if (moving) {
            s.body = heading;
            s.lead *= LEAD_DECAY;
            if (random.nextDouble() < GLANCE_CHANCE) s.lead = Math.clamp(laplace(GLANCE_SCALE), -LEAD_MAX, LEAD_MAX);
            s.head = s.body + s.lead;
            s.nextLookT = Integer.MIN_VALUE;
        } else {
            if (s.nextLookT == Integer.MIN_VALUE) { s.lookTarget = s.body; s.nextLookT = t + random.nextInt(LOOK_GAP[0], LOOK_GAP[1] + 1); }
            if (t >= s.nextLookT) {
                s.lookTarget = s.body + (float) ((random.nextDouble() * 2.0 - 1.0) * LOOK_RANGE);
                s.nextLookT = t + random.nextInt(LOOK_GAP[0], LOOK_GAP[1] + 1);
            }
            s.head += Math.clamp(s.lookTarget - s.head, -TURN_PER_TICK, TURN_PER_TICK);
            s.body += (s.head - s.body) * 0.6f;
            s.lead = Math.clamp(s.head - s.body, -LEAD_MAX, LEAD_MAX);
        }
        return new BodyFrame(sprint, crouch, s.body, s.head, swing, changed);
    }

    private float laplace(double scale) {
        double u = random.nextDouble() - 0.5;
        return (float) (-scale * Math.signum(u) * Math.log(1.0 - 2.0 * Math.abs(u)));
    }

    private int gap() { return Math.max(MIN_ITEM_GAP, (int) (-Math.log(1.0 - random.nextDouble()) * MEAN_ITEM_GAP)); }

    public static final String DEFAULT_ITEM = "hyperion";

    /** Arm swings a minute while a bot works in a room (measured). */
    public static final double SWINGS_PER_MINUTE = 18.0;

    /** The mean ticks between draws of the main hand. */
    public static final double MEAN_ITEM_GAP = 76.0;
    public static final int MIN_ITEM_GAP = 10;

    /** Faster than this, blocks a tick, a bot is sprinting. */
    public static final double SPRINT_ABOVE = 0.6;

    /** A bot that moved less than this since the last tick is standing. */
    public static final double MOVING_ABOVE = 0.02;

    /** A fifth of the standing stretches of one to three seconds are spent crouching. */
    public static final double CROUCH_SHARE = 0.2;
    /** (first, last) inclusive. */
    public static final int[] STRETCH_TICKS = new int[]{20, 60};

    /** How the head strays from the body while walking. */
    public static final float LEAD_MAX = 12f;
    public static final float LEAD_DECAY = 0.45f;
    public static final double GLANCE_CHANCE = 0.35;
    public static final double GLANCE_SCALE = 2.0;

    /** Standing, the head turns to a new target every 20 to 60 ticks, within 60 degrees, at up to 3 degrees a tick. (first, last) inclusive. */
    public static final int[] LOOK_GAP = new int[]{20, 60};
    public static final double LOOK_RANGE = 60.0;
    public static final float TURN_PER_TICK = 3f;

    /** The shares of the main hand over a clear (measured), before the context is applied. */
    public static final List<Pair<String, Double>> ITEM_SHARES = List.of(
            Pair.of("aotv", 0.33), Pair.of("hyperion", 0.32), Pair.of("breaker", 0.15), Pair.of("batwand", 0.09), Pair.of("terminator", 0.05), Pair.of("leap", 0.04),
            Pair.of("superboom", 0.02));

    /** What the work makes more likely (unmeasured). */
    private static double boost(BotActivity activity, String item) {
        return switch (activity) {
            case WALK -> switch (item) { case "aotv" -> 2.0; case "hyperion" -> 1.3; case "breaker" -> 0.6; default -> 1.0; };
            case FIGHT -> switch (item) { case "hyperion" -> 2.0; case "terminator" -> 2.0; case "batwand" -> 1.5; case "aotv" -> 0.5; case "breaker" -> 0.5; default -> 1.0; };
            case SEARCH -> switch (item) { case "breaker" -> 3.0; case "batwand" -> 1.5; case "hyperion" -> 0.5; case "aotv" -> 0.7; default -> 1.0; };
            case PUZZLE, STAND -> 1.0;
        };
    }

    public static String pick(BotActivity activity, double u) {
        ArrayList<Pair<String, Double>> weights = new ArrayList<>();
        double sum = 0.0;
        for (Pair<String, Double> p : ITEM_SHARES) {
            double w = p.second * boost(activity, p.first);
            weights.add(Pair.of(p.first, w));
            sum += w;
        }
        double x = u * sum;
        for (Pair<String, Double> p : weights) { if (x < p.second) return p.first; x -= p.second; }
        return DEFAULT_ITEM;
    }
}
