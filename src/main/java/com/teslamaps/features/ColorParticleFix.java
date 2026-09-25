package com.teslamaps.features;

// Ported from SkyHanni's features/misc/ColorParticleFix.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.ARGB;

public final class ColorParticleFix {

    private ColorParticleFix() {}

    public static void register() {
        Feature.of("ColorParticleFix").section("Render")
                .config("Render", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Particles");
                    b.toggle("Fix Colored Particles", () -> c.colorParticleFix, v -> c.colorParticleFix = v)
                            .desc("Hypixel sends 1.8-style colored particles (color in the offset); this restores their real color, e.g. slayer spawn particles.");
                });
    }

    /** From ClientPlayNetworkHandlerMixin, main thread. 1.8 packs RGB into the XYZ offset, modern reads it as XZY. */
    public static ParticleOptions fix(ParticleOptions opt, ClientboundLevelParticlesPacket p) {
        if (!(opt instanceof ColorParticleOption c)) return opt;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.colorParticleFix || !cfg.section("Render") || !SkyblockUtils.isInSkyblock()) return opt;
        int color = ARGB.colorFromFloat(c.getAlpha(), clamp(1 - p.getXDist()), clamp(1 - p.getZDist()), clamp(1 - p.getYDist()));
        return ColorParticleOption.create(c.getType(), color);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
