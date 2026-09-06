package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class NoCursorReset {

    private static double savedX, savedY;
    private static boolean savedValid = false;
    private static long savedTime = 0L;
    private static int restoreFrames = 0;

    public static void onContainerRemoved() {
    }

    public static void onContainerInit() {
        if (!TeslaMapsConfig.get().noCursorReset || !savedValid) return;
        if (System.currentTimeMillis() - savedTime > 2000L) { savedValid = false; return; }
        restoreFrames = 2;
    }

    public static void onRender() {
        if (!TeslaMapsConfig.get().noCursorReset) { restoreFrames = 0; return; }
        Minecraft mc = Minecraft.getInstance();
        if (restoreFrames > 0) {
            GLFW.glfwSetCursorPos(mc.getWindow().handle(), savedX, savedY);
            restoreFrames--;
            return;
        }
        double x = mc.mouseHandler.xpos(), y = mc.mouseHandler.ypos();
        double cx = mc.getWindow().getWidth() / 2.0, cy = mc.getWindow().getHeight() / 2.0;
        if (Math.abs(x - cx) > 5 || Math.abs(y - cy) > 5) {
            savedX = x; savedY = y; savedValid = true; savedTime = System.currentTimeMillis();
        }
    }
}
