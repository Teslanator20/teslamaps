package com.teslamaps.features.crimson;

import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

/** Small helpers shared by the Crimson Isle features. */
public final class Crimson {

    private Crimson() {}

    public static boolean here() {
        return SkyblockUtils.isInArea("Crimson Isle");
    }

    public static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §r" + msg));
    }

    public static void title(String title, String sub, SoundEvent sound) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(5, 50, 10);
        mc.gui.hud.setTitle(Component.literal(title));
        mc.gui.hud.setSubtitle(Component.literal(sub == null ? "" : sub));
        if (sound != null) LoudSound.play(sound, 1f, 1f);
    }

    static String mmss(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    static String tenths(long ms) {
        long t = Math.max(0, ms) / 100;
        return t / 10 + "." + t % 10 + "s";
    }
}
