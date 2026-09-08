package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

// FOV is overridden via CameraZoomMixin while zoomed; the FOV option itself is
// never touched (OptionInstance validates 30-110 and resets on out-of-range values).
public class Zoom {
    private static boolean zoomed = false;
    private static float currentFov = 30f;   // live FOV, adjusted by scroll while held
    private static float progress = 0f;      // 0 = normal fov, 1 = fully zoomed
    private static long lastNanos = 0L;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean held = cfg.zoomEnabled && cfg.zoomKey >= 0
                && mc.player != null && mc.screen == null && mc.getWindow() != null
                && com.teslamaps.utils.KeyNames.isDown(cfg.zoomKey);

        if (held && !zoomed) {
            currentFov = clamp(cfg.zoomFov);     // reset to default on every fresh zoom-in
            lastNanos = 0L;                      // first eased frame starts at dt 0
        }
        zoomed = held;
    }

    public static boolean active() {
        return zoomed;
    }

    public static float fov() {
        return currentFov;
    }

    /**
     * Blends between the vanilla FOV and the zoom FOV. Called once per frame from
     * CameraZoomMixin, so the ease runs on frame time rather than on ticks.
     */
    public static float apply(float baseFov) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        float target = zoomed ? 1f : 0f;
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : (now - lastNanos) / 1_000_000_000f;
        lastNanos = now;

        if (!cfg.zoomSmooth || cfg.zoomSmoothSpeed <= 0f) {
            progress = target;
        } else if (progress != target) {
            // Framerate independent exponential ease; clamp dt so an alt-tab
            // doesn't snap the whole transition in one frame.
            float f = 1f - (float) Math.exp(-cfg.zoomSmoothSpeed * Math.min(dt, 0.25f));
            progress += (target - progress) * f;
            if (Math.abs(target - progress) < 0.001f) progress = target;
        }

        if (progress <= 0f) return baseFov;
        return baseFov + (currentFov - baseFov) * progress;
    }

    /** True while the FOV is still on its way back to normal. */
    public static boolean transitioning() {
        return zoomed || progress > 0f;
    }

    /** Called from MouseScrollMixin. Returns true if the scroll was consumed for zooming. */
    public static boolean onScroll(double vertical) {
        if (!zoomed || vertical == 0) return false;
        currentFov -= (float) vertical * Math.max(1f, currentFov * 0.1f);  // scroll up = zoom in
        currentFov = clamp(currentFov);
        return true;
    }

    private static float clamp(float fov) {
        return Math.max(1f, Math.min(110f, fov));
    }
}
