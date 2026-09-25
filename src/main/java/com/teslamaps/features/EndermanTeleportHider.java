package com.teslamaps.features;

// Ported from SkyHanni's MiscFeatures.onEndermanTeleport / MixinEnderMan; modern clients only see the particles
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;

public final class EndermanTeleportHider {

    private EndermanTeleportHider() {}

    public static void register() {
        Feature.of("EndermanTeleportHider")
                .config("Hide", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Particles");
                    b.toggle("Hide Enderman Teleport", () -> c.endermanTeleportHider, v -> c.endermanTeleportHider = v)
                            .desc("Hides the portal particles of endermen, Zealots and Voidgloom, including the teleport burst.");
                });
    }

    public static boolean active() {
        return TeslaMapsConfig.get().endermanTeleportHider && SkyblockUtils.isInSkyblock();
    }

    /** Main thread: server-sent portal particles right next to an enderman. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        if (p.getParticle().getType() != ParticleTypes.PORTAL || !active()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        AABB box = new AABB(p.getX() - 3, p.getY() - 4, p.getZ() - 3, p.getX() + 3, p.getY() + 4, p.getZ() + 3);
        return !mc.level.getEntitiesOfClass(EnderMan.class, box).isEmpty();
    }
}
