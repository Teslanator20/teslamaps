package com.teslamaps.features;

// Ported from Devonian's Misc.kt RemoveGlowEffect
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

public final class VisualFixes {

    private VisualFixes() {}

    public static void register() {
        Feature.of("RemoveGlowEffect")
                .config("Hide", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.toggle("Remove Server Glow", () -> c.removeGlowEffect, v -> c.removeGlowEffect = v)
                            .desc("Removes the glowing effect the server puts on entities. TeslaMaps' own glow (ESP, Mob Visuals) stays.");
                });
    }
}
