package com.teslamaps.features;

// Hype hearts: Devonian's HideHypeHearts.kt, flay/whip: SkyHanni's FlamingFlayHider.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

public final class ParticleHiders {

    private static final float WHIP = (float) 0.003921568859368563;

    private ParticleHiders() {}

    public static void register() {
        Feature.of("FlamingFlayHider").section("Fishing")
                .config("Fishing", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.toggle("Hide Flaming Flay / Soul Whip Particles", () -> c.flamingFlayHider, v -> c.flamingFlayHider = v)
                            .desc("Drops the dust particles of Flaming Flay and Soul Whip near you.");
                    b.slider("  Distance", 1f, 30f, () -> (float) c.flamingFlayHideDistance, v -> c.flamingFlayHideDistance = Math.round(v), 0);
                });
    }

    /** Main thread, from ClientPlayNetworkHandlerMixin. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hideHypeHearts && !c.hideEnchantRunes && !c.flamingFlayHider) return false;
        ParticleType<?> type = p.getParticle().getType();
        if (type == ParticleTypes.HEART) return c.hideHypeHearts && p.getCount() == 3 && p.alwaysShow() && p.isOverrideLimiter();
        if (type == ParticleTypes.ENCHANT) return c.hideEnchantRunes && !signalParticle(p);
        if (type == ParticleTypes.DUST) return c.flamingFlayHider && c.section("Fishing") && flay(p);
        return false;
    }

    // pest locator and burrow particles stay visible
    private static boolean signalParticle(ClientboundLevelParticlesPacket p) {
        return p.getCount() == 10 && p.getMaxSpeed() == -2f || p.getCount() == 5 && p.getMaxSpeed() == 0.05f;
    }

    private static boolean flay(ClientboundLevelParticlesPacket p) {
        if (p.getCount() != 0 || p.getMaxSpeed() != 1f || !SkyblockUtils.isInSkyblock()) return false;
        float x = p.getXDist(), y = p.getYDist(), z = p.getZDist();
        boolean match = x == 1f && y == 1f && z == 0f
                || x == 1f && y == 0f && z == 0f
                || x == (float) 0.23137255012989044 && y == (float) 0.05098039284348488 && z == (float) 0.0313725508749485
                || x == WHIP && y == WHIP && z == WHIP;
        if (!match) return false;
        var pl = Minecraft.getInstance().player;
        if (pl == null) return false;
        double d = TeslaMapsConfig.get().flamingFlayHideDistance;
        return pl.distanceToSqr(p.getX(), p.getY(), p.getZ()) <= d * d;
    }
}
