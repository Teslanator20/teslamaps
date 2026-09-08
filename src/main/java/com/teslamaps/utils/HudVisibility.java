package com.teslamaps.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/** Shared "should my overlay draw right now" test, so every HUD answers it the same way. */
public final class HudVisibility {

    private HudVisibility() {}

    /**
     * True while overlays have to stay hidden. F1 and any real screen (inventory, config, a
     * container) suppress them - the chat screen deliberately does not, you still want the
     * timers and trackers up while typing a message.
     */
    public static boolean suppressed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return true;
        return mc.screen != null && !(mc.screen instanceof ChatScreen);
    }
}
