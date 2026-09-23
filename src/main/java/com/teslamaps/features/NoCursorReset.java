package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

// Ported from Bax's NoCursorReset: save on container close, restore instead of centring on open
public class NoCursorReset {

    private static double savedX, savedY;
    private static boolean savedValid = false;
    private static long savedTime = 0L;

    public static void onContainerRemoved() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noCursorReset) return;
        Minecraft mc = Minecraft.getInstance();
        savedX = mc.mouseHandler.xpos();
        savedY = mc.mouseHandler.ypos();
        savedValid = true;
        savedTime = System.currentTimeMillis();
    }

    /** Position to use instead of the window centre, or null to let vanilla centre the cursor. */
    public static double[] restorePos() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.noCursorReset || !savedValid) return null;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return null;
        if (!c.noCursorResetForever && System.currentTimeMillis() - savedTime > (long) (c.noCursorResetHold * 1000f)) {
            savedValid = false;
            return null;
        }
        double x = Math.max(0, Math.min(savedX, mc.getWindow().getScreenWidth()));
        double y = Math.max(0, Math.min(savedY, mc.getWindow().getScreenHeight()));
        return new double[]{x, y};
    }
}
