package com.teslamaps.features;

// Ported from FloydAddons' FloydRender.kt (borderless)
import com.mojang.blaze3d.platform.Window;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

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
        ensureBorderless(mc, mc.getWindow());
    }

    public static boolean isFullscreen(Window w) {
        return flag(w.handle(), SDLVideo.SDL_WINDOW_FULLSCREEN);
    }

    private static boolean flag(long h, long f) {
        return h != 0L && (SDLVideo.SDL_GetWindowFlags(h) & f) != 0;
    }

    private static void reconcileFullscreen(Window w) {
        boolean fs = isFullscreen(w);
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

    private static void ensureBorderless(Minecraft mc, Window w) {
        boolean want = cfg().borderlessWindow;
        long h = w.handle();
        if (flag(h, SDLVideo.SDL_WINDOW_MINIMIZED)) return;
        if (isFullscreen(w)) {
            if (want && !suppressNextWindowed) {
                suppressNextWindowed = true;
                mc.options.fullscreen().set(false);
            }
            return;
        }
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
        return !flag(h, SDLVideo.SDL_WINDOW_BORDERLESS);
    }

    private static void snapshot(long h) {
        if (!decorated(h)) return;
        if (flag(h, SDLVideo.SDL_WINDOW_MAXIMIZED)) {
            savedMaximized = true;
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1), y = stack.mallocInt(1), ww = stack.mallocInt(1), wh = stack.mallocInt(1);
            SDLVideo.SDL_GetWindowPosition(h, x, y);
            SDLVideo.SDL_GetWindowSize(h, ww, wh);
            if (ww.get(0) <= 0 || wh.get(0) <= 0) return;
            savedX = x.get(0);
            savedY = y.get(0);
            savedW = Math.max(640, ww.get(0));
            savedH = Math.max(480, wh.get(0));
        }
        savedMaximized = false;
        hasSaved = true;
    }

    /** Bounds of the display the window is mostly on: x, y, w, h; null when unknown. */
    private static int[] displayUnder(long h) {
        int d = SDLVideo.SDL_GetDisplayForWindow(h);
        if (d == 0) d = SDLVideo.SDL_GetPrimaryDisplay();
        if (d == 0) return null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_Rect r = SDL_Rect.calloc(stack);
            if (!SDLVideo.SDL_GetDisplayBounds(d, r) || r.w() <= 0 || r.h() <= 0) return null;
            return new int[]{r.x(), r.y(), r.w(), r.h()};
        }
    }

    private static void applyBorderless(long h) {
        int[] d = displayUnder(h);
        if (flag(h, SDLVideo.SDL_WINDOW_MAXIMIZED)) SDLVideo.SDL_RestoreWindow(h);
        SDLVideo.SDL_SetWindowBordered(h, false);
        SDLVideo.SDL_SetWindowPosition(h, d != null ? d[0] : 0, d != null ? d[1] : 0);
        SDLVideo.SDL_SetWindowSize(h, d != null ? d[2] : savedW, d != null ? d[3] : savedH);
    }

    private static void restore(long h) {
        int[] d = displayUnder(h);
        int maxW = d != null ? Math.max(640, d[2]) : Integer.MAX_VALUE;
        int maxH = d != null ? Math.max(480, d[3]) : Integer.MAX_VALUE;
        int w = Math.min(maxW, Math.max(640, savedW));
        int hh = Math.min(maxH, Math.max(480, savedH));
        int x = hasSaved ? savedX : (d != null ? d[0] : 0) + 50;
        int y = hasSaved ? savedY : (d != null ? d[1] : 0) + 50;
        SDLVideo.SDL_SetWindowBordered(h, true);
        SDLVideo.SDL_SetWindowSize(h, w, hh);
        SDLVideo.SDL_SetWindowPosition(h, x, y);
        if (savedMaximized) {
            SDLVideo.SDL_MaximizeWindow(h);
            savedMaximized = false;
        }
    }

    private static boolean isBorderlessApplied(long h) {
        if (decorated(h)) return false;
        int[] d = displayUnder(h);
        if (d == null) return true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer wx = stack.mallocInt(1), wy = stack.mallocInt(1), ww = stack.mallocInt(1), wh = stack.mallocInt(1);
            SDLVideo.SDL_GetWindowPosition(h, wx, wy);
            SDLVideo.SDL_GetWindowSize(h, ww, wh);
            return wx.get(0) == d[0] && wy.get(0) == d[1] && ww.get(0) == d[2] && wh.get(0) == d[3];
        }
    }
}
