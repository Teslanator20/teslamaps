package com.teslamaps.features.theend;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;

public final class EndermanQol {

    private EndermanQol() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EndermanQol").section("The End")
                .config("The End", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Endermen");
                    b.toggle("Hide Enderman Hurt Tint", () -> c.endermanNoHurtTint, v -> c.endermanNoHurtTint = v)
                            .desc("No red flash when you hit an enderman, Zealot or Voidgloom.")
                            .tags("enderman", "zealot", "voidgloom", "hurt", "red", "overlay");
                    b.info("Enderman sounds: Sounds > Mute Endermen In The End");
                });
    }

    private static boolean active() {
        return SkyblockUtils.isInSkyblock() && cfg().section("The End");
    }

    /** From EndermanTintMixin. */
    public static boolean hideHurtTint() {
        return cfg().endermanNoHurtTint && active();
    }
}
