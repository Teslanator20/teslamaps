package com.teslamaps.features.mining;

// Ported from SkyHanni's PrecisionMiningHighlight.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Boxes the Precision Mining particle on the block you mine: cyan = crit spot, green = already looking at it. */
public final class PrecisionMiningHighlight {

    private static final long LIFE_MS = 250;

    private static Feature feature;
    private static AABB box;
    private static boolean looking;
    private static long until;

    private PrecisionMiningHighlight() {}

    public static void register() {
        feature = Feature.of("PrecisionMiningHighlight").section("Mining")
                .when(() -> TeslaMapsConfig.get().precisionMiningHighlight)
                .world(PrecisionMiningHighlight::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Precision Mining Highlight", () -> c.precisionMiningHighlight, v -> c.precisionMiningHighlight = v)
                            .desc("Boxes the tiny Precision Mining particle on the block you are mining. Cyan = the spot, green = you are looking at it.")
                            .tags("precision mining", "particle", "hotm", "crit");
                });
    }

    /** From ClientPlayNetworkHandlerMixin. */
    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (feature == null || !TeslaMapsConfig.get().precisionMiningHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null) return;
        ParticleType<?> type = packet.particle().getType();
        if (type != ParticleTypes.CRIT && type != ParticleTypes.HAPPY_VILLAGER) return;
        if (!mc.options.keyAttack.isDown() || !feature.active() || !Mining.onMiningIsland()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        double x = packet.x(), y = packet.y(), z = packet.z();
        AABB particle = new AABB(x - 0.12, y - 0.12, z - 0.12, x + 0.12, y + 0.12, z + 0.12);
        BlockPos p = hit.getBlockPos();
        if (!new AABB(p).intersects(particle)) return;
        looking = type == ParticleTypes.HAPPY_VILLAGER;
        box = particle;
        until = System.currentTimeMillis() + LIFE_MS;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (box == null) return;
        if (System.currentTimeMillis() > until) {
            box = null;
            return;
        }
        ESPRenderer.drawFilledBox(pose, box, looking ? 0xAA55FF55 : 0xAA55FFFF, cam, false);
    }
}
