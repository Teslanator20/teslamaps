package com.teslamaps.features;

// Ported from Devonian's AccurateAbsorption.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AccurateAbsorption {

    private static final Pattern HEALTH = Pattern.compile("([\\d,.]+)/([\\d,.]+)❤");
    private static final long STALE_MS = 3000;

    private static float absorption = -1;
    private static long seenAt;

    private AccurateAbsorption() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AccurateAbsorption").when(() -> cfg().accurateAbsorption)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Absorption Hearts");
                    b.toggle("Accurate Absorption Hearts", () -> c.accurateAbsorption, v -> c.accurateAbsorption = v)
                            .desc("Yellow hearts show your real absorption, worked out from the HP in the action bar.");
                    b.slider("  Max Absorption", 0f, 100f, () -> c.accurateAbsorptionMax, v -> c.accurateAbsorptionMax = v, 0)
                            .desc("Cap in half hearts, for Mastiff or Wish.");
                });
    }

    public static void onActionBar(Component message) {
        if (!cfg().accurateAbsorption) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String text = StringUtil.strip(message.getString());
        if (text.indexOf('❤') < 0) return;
        Matcher m = HEALTH.matcher(text);
        if (!m.find()) return;
        double cur = parse(m.group(1)), max = parse(m.group(2));
        float maxHealth = mc.player.getMaxHealth();
        if (cur < 0 || max <= 0 || maxHealth <= 0) return;
        double hpPerHalf = max / Math.ceil(maxHealth);
        int hearts = (int) Math.ceil(mc.player.getHealth());
        int abs = Math.max((int) Math.ceil(cur / hpPerHalf) - hearts, 0);
        absorption = Math.min(abs, (int) cfg().accurateAbsorptionMax);
        seenAt = System.currentTimeMillis();
    }

    public static float absorption(float original) {
        TeslaMapsConfig c = cfg();
        if (!c.accurateAbsorption || absorption < 0 || !SkyblockUtils.isInSkyblock()) return original;
        if (System.currentTimeMillis() - seenAt > STALE_MS) return original;
        return absorption;
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
