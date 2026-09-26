package com.teslamaps.features.fishing;

// Ported from Skyblocker's skyblock/special/TrophySpecialEffects.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.RareDropEffects;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TrophyDiamondEffect {

    private static final Pattern DIAMOND = Pattern.compile("^NEW DISCOVERY:\\s+(.+?)\\s+DIAMOND$");
    private static final Map<String, String> SPECIAL = Map.of(
            "Obfuscated-1", "OBFUSCATED_FISH_1",
            "Obfuscated-2", "OBFUSCATED_FISH_2",
            "Obfuscated-3", "OBFUSCATED_FISH_3");

    private TrophyDiamondEffect() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TrophyDiamondEffect").section("Fishing").when(() -> cfg().trophyDiamondEffect)
                .serverChat(TrophyDiamondEffect::onChat)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Diamond Trophy Discovery Effect", () -> c.trophyDiamondEffect, v -> c.trophyDiamondEffect = v)
                            .desc("Totem-style item pop with portal particles and a sound on a NEW DISCOVERY of a DIAMOND trophy fish or frog")
                            .tags("trophy", "diamond", "discovery", "frog", "effect");
                });
    }

    /** "Steaming Hot Flounder" -> STEAMING_HOT_FLOUNDER_DIAMOND. */
    static String idFor(String name) {
        String base = SPECIAL.get(name);
        if (base == null) base = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        return base + "_DIAMOND";
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("NEW DISCOVERY:")) return;
        Matcher m = DIAMOND.matcher(t);
        if (!m.matches()) return;
        String name = m.group(1).trim();
        RareDropEffects.playPortal(idFor(name), "NEW DISCOVERY!", name + " DIAMOND");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }
}
