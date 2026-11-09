package com.teslamaps.features.fishing;

// After SkyOcean's FishingHookRendererMixin / FishingHookMixin and Skyblocker's RenderFishMixin
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

public final class FishingTweaks {

    private FishingTweaks() {}

    public static void register() {
        Feature.of("FishingTweaks").section("Fishing")
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Bobbers");
                    b.toggle("Hide Other Players' Bobbers", () -> c.hideOtherBobbers, v -> c.hideOtherBobbers = v)
                            .desc("Bobber and line of other players are not drawn, only yours")
                            .tags("rod", "hook", "line", "fishing");
                    b.toggle("  Also Their Wake Particles", () -> c.hideOtherBobberParticles, v -> c.hideOtherBobberParticles = v)
                            .desc("Drops the fishing wake particles that are not near your own bobber");
                    b.toggle("Bobber Fix", () -> c.fishingBobberFix, v -> c.fishingBobberFix = v)
                            .desc("Bobber floats on lava like on water and stops snapping to Hypixel's countdown label")
                            .tags("lava", "rubberband", "bobber");
                });
    }

    private static boolean on() {
        return SkyblockUtils.inSkyblockCached() && TeslaMapsConfig.get().section("Fishing");
    }

    /** EntityRenderDispatcherMixin. */
    public static boolean shouldHide(Entity e) {
        if (!(e instanceof FishingHook hook) || !TeslaMapsConfig.get().hideOtherBobbers || !on()) return false;
        return hook.getPlayerOwner() != Minecraft.getInstance().player;
    }

    /** Particle hide chain, main thread. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hideOtherBobbers || !c.hideOtherBobberParticles) return false;
        if (p.particle().getType() != ParticleTypes.FISHING || !on()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        FishingHook own = mc.player.fishing;
        if (own == null) return true;
        double dx = own.x() - p.x(), dz = own.z() - p.z();
        return dx * dx + dz * dz > 36;
    }

    /** FishingHookMixin. */
    public static boolean fixBobber() {
        return TeslaMapsConfig.get().fishingBobberFix && on();
    }
}
