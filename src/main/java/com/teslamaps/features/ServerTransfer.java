package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;

// Ported from Skyblocker's ServerTransferHelper / ScreenMixin / MouseHandlerMixin
public final class ServerTransfer {

    private static boolean interrupted = false;

    private ServerTransfer() {}

    public static boolean isTransferScreen(Screen s) {
        return s instanceof ServerReconfigScreen || s instanceof LevelLoadingScreen;
    }

    public static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.disableWorldLoadingScreen && c.seamlessServerSwitch && SkyblockUtils.isOnHypixel();
    }

    public static boolean keepGrabbed() {
        Minecraft mc = Minecraft.getInstance();
        return isTransferScreen(mc.gui.screen()) && mc.isWindowActive() && enabled();
    }

    public static boolean hideReconfig(Screen s) {
        return s instanceof ServerReconfigScreen && !interrupted && enabled();
    }

    public static void onScreenTick(Screen s) {
        if (!isTransferScreen(s)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isWindowActive() || !enabled()) return;
        interrupted = true;
        if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
    }

    public static void onGrab() {
        interrupted = false;
    }
}
