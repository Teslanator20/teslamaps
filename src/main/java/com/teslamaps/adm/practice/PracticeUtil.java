package com.teslamaps.adm.practice;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

final class PracticeUtil {
    private PracticeUtil() {}

    static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static void msg(String s) {
        msg(Component.literal(s));
    }

    static void msg(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§d[ADM] §r").append(c));
    }

    static void correct() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    static void wrong() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.BLAZE_HURT, 1f, 1f);
    }

    /** F7/M7 boss, Goldor's floor (Odin's getF7Phase() == P3). */
    static boolean inP3() {
        if (!DungeonManager.isInBossArea()) return false;
        String f = DungeonManager.getFloorName();
        if (f == null || !f.endsWith("7")) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double y = mc.player.getY();
        return y > 100 && y <= 155;
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static void safely(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("[adm] {} failed", what, t);
        }
    }
}
