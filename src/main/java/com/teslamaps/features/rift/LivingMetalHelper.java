package com.teslamaps.features.rift;

// Ported from SkyHanni's LivingCaveLivingMetalHelper.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class LivingMetalHelper {

    private static boolean inCave;
    private static BlockPos lastClicked, from, to;
    private static long animStart;
    private static int ticks;
    private static Feature feature;

    private LivingMetalHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("LivingMetalHelper").section("Rift").when(() -> cfg().livingMetalHelper)
                .tick(LivingMetalHelper::tick)
                .world(LivingMetalHelper::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Living Metal Helper", () -> c.livingMetalHelper, v -> c.livingMetalHelper = v)
                            .desc("Living Cave: after you hit the Living Metal (lapis ore) a box follows it to the block it jumps to.")
                            .tags("living", "metal", "cave", "rift", "lapis");
                    b.color("  Color", () -> c.livingMetalColor, v -> c.livingMetalColor = v);
                    b.toggle("  Hide Particles", () -> c.livingMetalHideParticles, v -> c.livingMetalHideParticles = v);
                });
    }

    static boolean livingCave() {
        if (!RiftSpot.inRift()) return false;
        String sub = RiftSpot.sub();
        return sub.contains("Living Cave") || sub.contains("Living Stillness");
    }

    private static void tick() {
        if (++ticks % 20 == 0) inCave = livingCave();
    }

    private static boolean on() {
        return inCave && feature != null && feature.active();
    }

    /** From the start-destroy hook. */
    public static void onLeftClick(BlockPos pos) {
        if (!on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockState(pos).is(Blocks.LAPIS_ORE)) lastClicked = pos.immutable();
    }

    public static boolean listening() {
        return inCave && cfg().livingMetalHelper;
    }

    /** From BlockUpdateMixin, main thread. */
    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState now) {
        if (!on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) >= 49) return;
        if (old.is(Blocks.LAPIS_ORE) && pos.equals(to)) to = null;
        if (!now.is(Blocks.LAPIS_ORE) || lastClicked == null) return;
        if (lastClicked.distSqr(pos) < 4) {
            from = lastClicked;
            to = pos.immutable();
            animStart = System.currentTimeMillis();
        }
    }

    /** Particle hide chain, main thread. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        BlockPos t = to;
        if (t == null || !cfg().livingMetalHideParticles || !on()) return false;
        double dx = p.getX() - t.getX(), dy = p.getY() - t.getY(), dz = p.getZ() - t.getZ();
        return dx * dx + dy * dy + dz * dz < 9;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        BlockPos a = from, b = to;
        if (a == null || b == null) return;
        long since = System.currentTimeMillis() - animStart;
        if (since > 4000) return;
        double f = Math.min(1.0, since / 500.0);
        double x = a.getX() + (b.getX() - a.getX()) * f;
        double y = a.getY() + (b.getY() - a.getY()) * f;
        double z = a.getZ() + (b.getZ() - a.getZ()) * f;
        AABB box = new AABB(x, y, z, x + 1, y + 1, z + 1);
        int color = TeslaMapsConfig.parseColor(cfg().livingMetalColor);
        boolean near = cam.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) < 100;
        ESPRenderer.drawFilledBox(pose, box, color, cam, near);
    }
}
