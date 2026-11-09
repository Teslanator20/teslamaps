package com.teslamaps.render.gui;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

/**
 * Decides whether the glass restyle applies right now. Gated to the screens we
 * actually redesigned so the vanilla widget mixins stay inert everywhere else.
 */
public class TeslaGuiStyle {

    public static boolean active() {
        if (!TeslaMapsConfig.get().customTitleScreen) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen;
    }
}
