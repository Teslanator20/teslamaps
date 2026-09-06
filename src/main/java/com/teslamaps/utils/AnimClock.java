package com.teslamaps.utils;

/**
 * Frame-rate independent lerp factors. Lerp factors are written for 60 fps;
 * {@link #lerpFactor(float)} rescales them by the real frame delta so an
 * animation takes the same wall-clock time at any frame rate.
 */
public class AnimClock {

    private static final double TARGET_MS = 1000.0 / 60.0;
    private static final long MIN_FRAME_NS = 500_000L;
    private static final double MAX_DELTA = 3.0;

    private static long lastFrameNs = -1L;
    private static double deltaFactor = 1.0;

    public static void update() {
        long now = System.nanoTime();
        if (lastFrameNs == -1L) {
            lastFrameNs = now;
            return;
        }
        long elapsed = now - lastFrameNs;
        if (elapsed < MIN_FRAME_NS) return;
        lastFrameNs = now;
        deltaFactor = Math.min(elapsed / 1_000_000.0 / TARGET_MS, MAX_DELTA);
    }

    public static float lerpFactor(float k) {
        if (deltaFactor == 1.0) return k;
        return (float) (1.0 - Math.pow(1.0 - k, deltaFactor));
    }
}
