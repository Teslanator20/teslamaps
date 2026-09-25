package com.teslamaps.features;

// Ported from Devonian's Misc.kt RemoveGlowEffect / FixRidingCamera
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
                    b.toggle("Fix Riding Camera", () -> c.fixRidingCamera, v -> c.fixRidingCamera = v)
                            .desc("The camera no longer lags behind while riding a mount or minecart (MC-259512). Visual only.");
                });
    }
}
