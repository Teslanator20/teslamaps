package com.teslamaps.features;

// Ported from FloydAddons' FloydRender.kt (borderless)
import com.mojang.blaze3d.platform.Window;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

public final class WindowTweaks {

    private static boolean applied;
    private static Boolean lastFullscreen;
    private static boolean suppressNextWindowed;

    private static int savedX, savedY, savedW = 854, savedH = 480;
    private static boolean hasSaved, savedMaximized;

    private WindowTweaks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("WindowTweaks").tickAlways(WindowTweaks::tick)
                .config("QoL & Misc", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Window");
                    b.toggle("Borderless Window", () -> c.borderlessWindow, v -> c.borderlessWindow = v)
                            .desc("Borderless fullscreen on the monitor the window is on; off restores the old size");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;
        reconcileFullscreen(mc.getWindow());
        ensureBorderless(mc.getWindow());
    }

    private static void reconcileFullscreen(Window w) {
        boolean fs = w.isFullscreen();
        Boolean prev = lastFullscreen;
        lastFullscreen = fs;
        if (prev == null || prev == fs) return;
        if (!fs && prev && cfg().borderlessWindow) {
            if (suppressNextWindowed) {
                suppressNextWindowed = false;
                applied = false;
                return;
            }
            cfg().borderlessWindow = false;
            TeslaMapsConfig.save();
        }
    }

    private static void ensureBorderless(Window w) {
        boolean want = cfg().borderlessWindow;
        if (GLFW.glfwGetWindowAttrib(w.handle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) return;
        if (w.isFullscreen()) {
            if (want && !suppressNextWindowed) {
                suppressNextWindowed = true;
                w.toggleFullScreen();
            }
            return;
        }
        long h = w.handle();
        if (want) {
            if (applied && isBorderlessApplied(h)) return;
            snapshot(h);
            applyBorderless(h);
            applied = true;
        } else if (applied) {
            restore(h);
            applied = false;
        }
    }

    private static boolean decorated(long h) {
        return GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE;
    }

    private static void snapshot(long h) {
        if (!decorated(h)) return;
        if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            savedMaximized = true;
            return;
        }
        int[] x = new int[1], y = new int[1], ww = new int[1], wh = new int[1];
        GLFW.glfwGetWindowPos(h, x, y);
        GLFW.glfwGetWindowSize(h, ww, wh);
        if (ww[0] <= 0 || wh[0] <= 0) return;
        savedX = x[0];
        savedY = y[0];
        savedW = Math.max(640, ww[0]);
        savedH = Math.max(480, wh[0]);
        savedMaximized = false;
        hasSaved = true;
    }

    private static long monitorUnder(long h) {
        long primary = GLFW.glfwGetPrimaryMonitor();
        var monitors = GLFW.glfwGetMonitors();
        if (monitors == null || monitors.limit() <= 1) return primary;
        int[] wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1], mx = new int[1], my = new int[1];
        GLFW.glfwGetWindowPos(h, wx, wy);
        GLFW.glfwGetWindowSize(h, ww, wh);
        long best = 0, bestArea = 0;
        for (int i = 0; i < monitors.limit(); i++) {
            long m = monitors.get(i);
            GLFWVidMode mode = GLFW.glfwGetVideoMode(m);
            if (mode == null) continue;
            GLFW.glfwGetMonitorPos(m, mx, my);
            long ow = Math.max(0, Math.min(wx[0] + ww[0], mx[0] + mode.width()) - Math.max(wx[0], mx[0]));
            long oh = Math.max(0, Math.min(wy[0] + wh[0], my[0] + mode.height()) - Math.max(wy[0], my[0]));
            if (ow * oh > bestArea) {
                bestArea = ow * oh;
                best = m;
            }
        }
        return best != 0 ? best : primary;
    }

    private static void applyBorderless(long h) {
        long m = monitorUnder(h);
        GLFWVidMode mode = m != 0 ? GLFW.glfwGetVideoMode(m) : null;
        int[] mx = new int[1], my = new int[1];
        if (m != 0) GLFW.glfwGetMonitorPos(m, mx, my);
        if (GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) GLFW.glfwRestoreWindow(h);
        GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(h, 0L, mx[0], my[0],
                mode != null ? mode.width() : savedW, mode != null ? mode.height() : savedH, GLFW.GLFW_DONT_CARE);
    }

    private static void restore(long h) {
        long m = monitorUnder(h);
        GLFWVidMode mode = m != 0 ? GLFW.glfwGetVideoMode(m) : null;
        int maxW = mode != null ? Math.max(640, mode.width()) : Integer.MAX_VALUE;
        int maxH = mode != null ? Math.max(480, mode.height()) : Integer.MAX_VALUE;
        int w = Math.min(maxW, Math.max(640, savedW));
        int hh = Math.min(maxH, Math.max(480, savedH));
        int[] mx = new int[1], my = new int[1];
        if (m != 0) GLFW.glfwGetMonitorPos(m, mx, my);
        int x = hasSaved ? savedX : mx[0] + 50;
        int y = hasSaved ? savedY : my[0] + 50;
        GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowMonitor(h, 0L, x, y, w, hh, GLFW.GLFW_DONT_CARE);
        if (savedMaximized) {
            GLFW.glfwMaximizeWindow(h);
            savedMaximized = false;
        }
    }

    private static boolean isBorderlessApplied(long h) {
        if (decorated(h)) return false;
        long m = monitorUnder(h);
        GLFWVidMode mode = m != 0 ? GLFW.glfwGetVideoMode(m) : null;
        if (mode == null) return true;
        int[] wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1], mx = new int[1], my = new int[1];
        GLFW.glfwGetWindowPos(h, wx, wy);
        GLFW.glfwGetWindowSize(h, ww, wh);
        GLFW.glfwGetMonitorPos(m, mx, my);
        return wx[0] == mx[0] && wy[0] == my[0] && ww[0] == mode.width() && wh[0] == mode.height();
    }
}
