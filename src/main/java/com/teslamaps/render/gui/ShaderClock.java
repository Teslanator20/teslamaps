package com.teslamaps.render.gui;

/**
 * Shader time source. Accumulates scaled real time instead of multiplying a
 * running clock, so changing the speed slider bends the animation from where it
 * is rather than jumping it to a different phase.
 */
public class ShaderClock {

    // Wrapped so the float keeps sub-millisecond precision indefinitely.
    private static final float CYCLE_SECONDS = 3600.0f;

    private float time;
    private long lastNanos = System.nanoTime();

    public float advance(float speed) {
        long now = System.nanoTime();
        float delta = (now - lastNanos) / 1_000_000_000.0f;
        lastNanos = now;
        // A pause (loading, alt-tab) should not fast-forward the animation.
        delta = Math.min(delta, 0.25f);
        time = (time + delta * Math.max(speed, 0.0f)) % CYCLE_SECONDS;
        return time;
    }
}
