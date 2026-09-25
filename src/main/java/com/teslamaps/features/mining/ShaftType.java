package com.teslamaps.features.mining;

import com.teslamaps.utils.ScoreboardUtils;
import java.util.Set;
import net.minecraft.client.Minecraft;

/** Glacite Mineshaft type off the scoreboard server line, e.g. "TOPA_1" or "PERI_C". */
public final class ShaftType {

    // Ported from SkyHanni's MineshaftDetection.MineshaftType
    public static final Set<String> TYPES = Set.of(
            "TOPA_1", "TOPA_2", "SAPP_1", "SAPP_2", "AMET_1", "AMET_2", "AMBE_1", "AMBE_2", "JADE_1", "JADE_2",
            "TITA_1", "UMBE_1", "TUNG_1", "FAIR_1", "RUBY_1", "RUBY_2", "RUBY_C", "ONYX_1", "ONYX_2", "ONYX_C",
            "AQUA_1", "AQUA_2", "AQUA_C", "CITR_1", "CITR_2", "CITR_C", "PERI_1", "PERI_2", "PERI_C",
            "JASP_1", "JASP_C", "OPAL_1", "OPAL_C", "LITT_L");

    private static Object lastLevel;
    private static String current;
    private static long checkedAt;

    private ShaftType() {}

    /** Null outside a mineshaft or while the scoreboard has not shown the type yet. */
    public static String current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            current = null;
            checkedAt = 0;
        }
        if (current != null) return current;
        long now = System.currentTimeMillis();
        if (now - checkedAt < 1000) return null;
        checkedAt = now;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String clean = ScoreboardUtils.cleanLine(line);
            if (clean.indexOf('_') < 0) continue;
            for (String token : clean.split(" ")) {
                if (TYPES.contains(token)) return current = token;
            }
        }
        return null;
    }
}
