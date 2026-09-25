package com.teslamaps.features;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Temporary kill switch for every TeslaMaps HUD, world render, glow, entity hider and slot overlay. Not saved. */
public final class GlobalRender {

    private static boolean off;

    private GlobalRender() {}

    public static boolean off() {
        return off;
    }

    public static void toggle() {
        off = !off;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(off
                ? "§b[TeslaMaps] §cAll rendering off. §7Run /tmap rendertoggle again to turn it back on."
                : "§b[TeslaMaps] §aAll rendering back on."));
    }
}
