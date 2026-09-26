package com.teslamaps.features.rift;

import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Rift island + scoreboard sub-area ("ф Mirrorverse"), refreshed once a second. */
public final class RiftZone {

    private static boolean inRift;
    private static String location = "";
    private static int ticks;
    private static Object lastLevel;

    private RiftZone() {}

    public static void register() {
        Feature.of("RiftZone").tick(RiftZone::tick);
    }

    public static boolean inRift() { return inRift; }
    public static String location() { return location; }
    public static boolean inMirrorverse() { return inRift && location.equals("Mirrorverse"); }
    public static boolean inStillgore() { return inRift && location.startsWith("Stillgore Ch"); }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            ticks = 0;
        }
        if (ticks++ % 20 != 0) return;
        inRift = SkyblockUtils.getArea().toLowerCase(Locale.ROOT).contains("rift");
        String loc = "";
        if (inRift) {
            for (String line : ScoreboardUtils.getScoreboardLines()) {
                String s = StringUtil.stripTrim(line);
                if (s.length() > 2 && (s.charAt(0) == 'ф' || s.charAt(0) == '⏣') && s.charAt(1) == ' ') {
                    loc = s.substring(2).trim();
                    break;
                }
            }
        }
        location = loc;
    }
}
