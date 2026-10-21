// Ported from ADM's p3sim/clear/blood/BloodExit.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.util.KRandom;

/** The Watcher's health and what hangs on it (pure). */
public final class WatcherStats {
    private WatcherStats() {}

    public static final int KILLS_TO_DEFEAT = 15;

    public static final float MAX_HEALTH = 3000f;
    public static final float HEALTH_PER_KILL = 200f;
    public static final float DEFEATED_HEALTH = 0.1f;

    public static final float EYE_HEALTH = 1000f;

    public static final double MOVEMENT_SPEED = 0.26;

    /** How high above the floor he hangs when the room opens. */
    public static final double RISE = 4.0;

    /** Name tag stand height over its body's feet. */
    public static final double TAG_DY = 1.96875;

    public static int clamp(int kills) { return Math.clamp(kills, 0, KILLS_TO_DEFEAT); }

    public static float health(int kills) {
        int k = clamp(kills);
        return k >= KILLS_TO_DEFEAT ? DEFEATED_HEALTH : MAX_HEALTH - HEALTH_PER_KILL * k;
    }

    /** Boss bar fill, rounded to hundredths. */
    public static float barProgress(int kills) { return Math.round(health(kills) / MAX_HEALTH * 100f) / 100f; }

    /** Yaw toward the blood door (SOUTH 180, WEST 270, NORTH 0, EAST 90). */
    public static float yawTowardDoor(int quarterTurns) { return (float) ((180 + 90 * Math.floorMod(quarterTurns, 4)) % 360); }

    /** Where a Watchful Eye hangs relative to the Watcher's feet (world axes). */
    public static double[] eyeOffset(KRandom random) {
        double a = side(random);
        double c = side(random);
        return new double[]{a, EYE_RISE, c};
    }

    private static double side(KRandom random) { return random.nextBoolean() ? -(0.5 + random.nextDouble()) : 1.5 + random.nextDouble(); }

    public static final double EYE_RISE = 2.5;
}
