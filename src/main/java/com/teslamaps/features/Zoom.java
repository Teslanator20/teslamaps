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
    private static boolean toggled = false;
    private static boolean keyWasDown = false;
    private static boolean scrolled = false;
    private static float magnification = 1f;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean canZoom = cfg.zoomEnabled
                && mc.player != null && mc.gui.screen() == null && mc.getWindow() != null;
        boolean keyDown = canZoom && com.teslamaps.utils.KeyNames.isDown(cfg.zoomKey);
        if (!canZoom) toggled = false;
        else if (cfg.zoomToggle && keyDown && !keyWasDown) toggled = !toggled;
        keyWasDown = keyDown;
        boolean held = cfg.zoomToggle ? toggled : keyDown;

        if (held && !zoomed) {
            if (!cfg.zoomRememberScroll || !scrolled) currentFov = clamp(cfg.zoomFov);
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

        if (progress <= 0f) { magnification = 1f; return baseFov; }
        float fov = baseFov + (currentFov - baseFov) * progress;
        magnification = fov > 0f ? Math.max(1f, baseFov / fov) : 1f;
        return fov;
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
        scrolled = true;
        return true;
    }

    public static double mouse(double movement) {
        if (!TeslaMapsConfig.get().zoomRelativeSensitivity || !transitioning()) return movement;
        return movement / magnification;
    }

    public static boolean hideHand() {
        return zoomed && TeslaMapsConfig.get().zoomHideHand;
    }

    private static float clamp(float fov) {
        return Math.max(1f, Math.min(110f, fov));
    }
}
