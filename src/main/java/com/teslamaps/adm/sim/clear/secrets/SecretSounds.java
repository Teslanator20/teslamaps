// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.util.KRandom;

public final class SecretSounds {
    private SecretSounds() {}

    private static final float STEPS = 63f;

    public static float quantise(float pitch) { return (float) Math.floor((double) (pitch * STEPS)) / STEPS; }

    public static float chestOpen(KRandom random) { return (56 + random.nextInt(7)) / STEPS; }

    public static final float[] HARP = {50f / 63f, 56f / 63f, 63f / 63f, 69f / 63f, 75f / 63f};

    public static final float ANVIL_BREAK_PITCH = 107f / 63f;
    public static final float LEVER_CLICK_PITCH = 37f / 63f;
    public static final float LEVER_CLICK_VOLUME = 0.3f;
    public static final float WALL_TICK_PITCH = 31f / 63f;
    public static final float SKULL_PLING_PITCH = 255f / 63f;
    public static final float SKULL_PLING_VOLUME = 8f;
    public static final float ORB_PITCH = 94f / 63f;
    public static final float DENIED_VOLUME = 8f;

    public static float pickup(KRandom random) { return (random.nextFloat() - random.nextFloat()) * 1.4f + 2.0f; }

    public static float bonusPing(KRandom random) { return 1.70f + random.nextFloat() * 0.98f; }

    public static float batDeath(KRandom random) { return quantise(((random.nextFloat() - random.nextFloat()) * 0.2f + 1f) * 0.95f); }
}
